"""Resumable mixed-precision training (spec 5).

CLI: python -m korokae_v2.train --config configs/train_main.yaml --data D --eval E --ckpt C
         --max-minutes 690

Every batch is a pure function of (seed, micro-batch number), so a run stopped at any step and
resumed produces exactly the weights of an uninterrupted run. Checkpoints are written atomically
(*.tmp then rename); resume uses the newest step_*.pt that loads.
"""
from __future__ import annotations

import argparse
import glob
import inspect
import json
import math
import os
import random
import re
import subprocess
import time

import numpy as np
import torch
import yaml

from .augment import AugmentConfig, mix_batch
from .data.manifest import MANIFEST_NAME, read_manifest
from .data.segments import StemPool
from .forward import separate_batch
from .losses import separation_loss
from .metrics import sdr
from .model import KorokaeNetV2
from .render_eval import load_clips

_STEP_RE = re.compile(r"^step_(\d{8})\.pt$")


# ---------------------------------------------------------------- config

def load_config(path: str) -> dict:
    with open(path, encoding="utf-8") as f:
        cfg = yaml.safe_load(f)
    model = {}
    if cfg.get("base"):
        with open(os.path.join(os.path.dirname(path), cfg["base"]), encoding="utf-8") as f:
            model.update((yaml.safe_load(f) or {}).get("model", {}))
    model.update(cfg.get("model") or {})
    cfg["model"] = model
    return cfg


def build_model(model_cfg: dict) -> KorokaeNetV2:
    """Only constructor keys are passed (base.yaml also documents arch, n_bands, time_stride)."""
    params = inspect.signature(KorokaeNetV2.__init__).parameters
    return KorokaeNetV2(**{k: v for k, v in model_cfg.items() if k in params})


def _augment_config(over: dict) -> AugmentConfig:
    return AugmentConfig(**{k: tuple(v) if isinstance(v, list) else v for k, v in (over or {}).items()})


def git_hash() -> str:
    env = os.environ.get("KOROKAE_COMMIT")
    if env:
        return env
    try:
        return subprocess.run(["git", "-C", os.path.dirname(os.path.abspath(__file__)), "rev-parse",
                               "HEAD"], capture_output=True, text=True, check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


# ---------------------------------------------------------------- data

class BatchStream(torch.utils.data.IterableDataset):
    """Raw (vocal, accomp) batches [B, 2, L] numbered start, start+1, ...; batch n depends only on
    (seed, n). Worker w of W produces numbers n with (n - start) % W == w, and the DataLoader's
    round-robin order restores the sequence."""

    def __init__(self, manifest: str, root: str, split: str, length: int, batch: int, seed: int,
                 start: int):
        super().__init__()
        rows = read_manifest(manifest)
        self.vocals = StemPool(rows, root, "vocal", split)
        self.accomp = StemPool(rows, root, "accompaniment", split)
        self.length, self.batch, self.seed, self.start = length, batch, seed, start

    def make(self, n: int) -> tuple[torch.Tensor, torch.Tensor]:
        rng = np.random.default_rng([self.seed, n])
        v = np.stack([self.vocals.sample(rng, self.length) for _ in range(self.batch)])
        a = np.stack([self.accomp.sample(rng, self.length) for _ in range(self.batch)])
        return torch.from_numpy(v), torch.from_numpy(a)

    def __iter__(self):
        info = torch.utils.data.get_worker_info()
        w, workers = (info.id, info.num_workers) if info else (0, 1)
        n = self.start + w
        while True:
            yield self.make(n)
            n += workers


# ---------------------------------------------------------------- checkpoints

def _checkpoints(ckpt_dir: str) -> list[tuple[int, str]]:
    out = []
    for p in glob.glob(os.path.join(ckpt_dir, "step_*.pt")):
        m = _STEP_RE.match(os.path.basename(p))
        if m:
            out.append((int(m.group(1)), p))
    return sorted(out)


def _load_latest(ckpt_dir: str):
    for step, p in reversed(_checkpoints(ckpt_dir)):
        try:
            return torch.load(p, map_location="cpu", weights_only=False)
        except Exception:  # truncated or corrupt: fall back to the previous one
            continue
    return None


def _save_atomic(obj: dict, path: str) -> None:
    tmp = path + ".tmp"
    torch.save(obj, tmp)
    os.replace(tmp, path)


# ---------------------------------------------------------------- training

def _lr_lambda(warmup: int, total: int, min_ratio: float):
    def f(s: int) -> float:
        if s < warmup:
            return (s + 1) / warmup
        p = min(1.0, (s - warmup) / max(1, total - warmup))
        return min_ratio + (1 - min_ratio) * 0.5 * (1 + math.cos(math.pi * p))
    return f


@torch.no_grad()
def validate(model, eval_root: str, device: torch.device, amp: bool) -> tuple[float, float]:
    model.eval()
    v_scores, i_scores = [], []
    for mix, voc, acc in load_clips(eval_root, "val"):
        with torch.autocast(device.type, dtype=torch.float16, enabled=amp):
            v_hat, i_hat = separate_batch(model, mix[None].to(device))
        v_scores.append(float(sdr(v_hat[0].cpu(), voc)))
        i_scores.append(float(sdr(i_hat[0].cpu(), acc)))
    model.train()
    return float(np.median(v_scores)), float(np.median(i_scores))


def train(cfg: dict, data_root: str, eval_root: str | None, ckpt_dir: str, max_minutes: float,
          max_steps: int | None = None) -> dict:
    t0 = time.monotonic()
    os.makedirs(ckpt_dir, exist_ok=True)
    dev_name = cfg.get("device", "auto")
    device = torch.device("cuda" if dev_name == "auto" and torch.cuda.is_available()
                          else "cpu" if dev_name == "auto" else dev_name)
    amp = bool(cfg.get("amp", False)) and device.type == "cuda"
    if device.type == "cuda":
        torch.backends.cudnn.benchmark = True
    seed = int(cfg.get("seed", 0))
    torch.manual_seed(seed)

    model = build_model(cfg["model"]).to(device)
    if cfg.get("init_from"):
        model.load_state_dict(torch.load(cfg["init_from"], map_location="cpu",
                                         weights_only=False)["model"])
    opt = torch.optim.AdamW(model.parameters(), lr=cfg["lr"], betas=tuple(cfg.get("betas", (0.9, 0.999))),
                            weight_decay=cfg.get("weight_decay", 0.01))
    total = int(cfg["total_steps"])
    sched = torch.optim.lr_scheduler.LambdaLR(
        opt, _lr_lambda(int(cfg.get("warmup", 0)) or 1, total, 1e-6 / cfg["lr"]))
    scaler = torch.amp.GradScaler(device.type, enabled=amp)

    step, best = 0, -math.inf
    state = _load_latest(ckpt_dir)
    if state is not None:
        model.load_state_dict(state["model"])
        opt.load_state_dict(state["optimizer"])
        sched.load_state_dict(state["scheduler"])
        scaler.load_state_dict(state["scaler"])
        step, best = state["step"], state["best"]
        torch.set_rng_state(state["rng"]["torch"])
        np.random.set_state(state["rng"]["numpy"])
        random.setstate(state["rng"]["python"])
        if device.type == "cuda" and state["rng"].get("cuda") is not None:
            torch.cuda.set_rng_state_all(state["rng"]["cuda"])
    commit = git_hash()
    log_path = os.path.join(ckpt_dir, "log.jsonl")

    def log(entry: dict) -> None:
        with open(log_path, "a", encoding="utf-8") as f:
            f.write(json.dumps(entry) + "\n")

    log({"event": "start", "git_hash": commit, "resumed_from": state["step"] if state else None,
         "device": str(device), "cfg": cfg})

    def save(path: str) -> None:
        _save_atomic({"model": model.state_dict(), "optimizer": opt.state_dict(),
                      "scheduler": sched.state_dict(), "scaler": scaler.state_dict(), "step": step,
                      "rng": {"torch": torch.get_rng_state(), "numpy": np.random.get_state(),
                              "python": random.getstate(),
                              "cuda": torch.cuda.get_rng_state_all() if device.type == "cuda" else None},
                      "cfg": cfg, "git_hash": commit, "best": best}, path)

    def checkpoint() -> None:
        save(os.path.join(ckpt_dir, f"step_{step:08d}.pt"))
        for _, old in _checkpoints(ckpt_dir)[:-int(cfg.get("keep_last", 3))]:
            os.remove(old)

    accum = int(cfg.get("accum", 1))
    stream = BatchStream(os.path.join(data_root, MANIFEST_NAME), data_root, "train",
                         int(cfg["segment"]), int(cfg["batch"]), seed, start=step * accum)
    workers = int(cfg.get("workers", 0))
    batches = iter(torch.utils.data.DataLoader(stream, batch_size=None, num_workers=workers,
                                               pin_memory=device.type == "cuda",
                                               prefetch_factor=4 if workers else None))
    aug = _augment_config(cfg.get("augment"))
    every_min = float(cfg.get("ckpt_every_minutes", 30))
    val_every, log_every = int(cfg.get("val_every", 0)), int(cfg.get("log_every", 50))
    last_ckpt, last_log, stopped = time.monotonic(), time.monotonic(), "done"
    model.train()
    while step < total:
        opt.zero_grad(set_to_none=True)
        loss_sum = 0.0
        for k in range(accum):
            v, a = next(batches)
            gen = torch.Generator().manual_seed(seed * 1_000_003 + step * accum + k)
            mix, vt, at = mix_batch(v.to(device, non_blocking=True), a.to(device, non_blocking=True),
                                    aug, gen)
            with torch.autocast(device.type, dtype=torch.float16, enabled=amp):
                v_hat, i_hat = separate_batch(model, mix)
            loss = separation_loss(v_hat, i_hat, vt, at) / accum
            scaler.scale(loss).backward()
            loss_sum += float(loss.detach())
        scaler.unscale_(opt)
        torch.nn.utils.clip_grad_norm_(model.parameters(), cfg.get("grad_clip", 5.0))
        scaler.step(opt)
        scaler.update()
        sched.step()
        step += 1
        now = time.monotonic()
        if log_every and step % log_every == 0:
            log({"step": step, "loss": loss_sum, "lr": sched.get_last_lr()[0],
                 "sec_per_step": (now - last_log) / log_every})
            last_log = now
        if val_every and eval_root and step % val_every == 0:
            v_sdr, i_sdr = validate(model, eval_root, device, amp)
            log({"step": step, "val_vocal_sdr": v_sdr, "val_inst_sdr": i_sdr})
            if i_sdr > best:
                best = i_sdr
                save(os.path.join(ckpt_dir, "best.pt"))
        saved = False
        if every_min <= 0 or now - last_ckpt >= every_min * 60:
            checkpoint()
            last_ckpt, saved = now, True
        if max_steps is not None and step >= max_steps:
            stopped = "steps"
        elif now - t0 >= max_minutes * 60 and step < total:
            stopped = "time"
        else:
            continue
        if not saved:
            checkpoint()
        break
    else:
        if not _checkpoints(ckpt_dir) or _checkpoints(ckpt_dir)[-1][0] != step:
            checkpoint()
    log({"event": "stop", "step": step, "stopped": stopped})
    return {"step": step, "best_val_inst_sdr": None if best == -math.inf else best, "stopped": stopped}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--config", required=True)
    ap.add_argument("--data", required=True)
    ap.add_argument("--eval", default=None)
    ap.add_argument("--ckpt", required=True)
    ap.add_argument("--max-minutes", type=float, default=690.0)
    ap.add_argument("--max-steps", type=int, default=None)
    a = ap.parse_args()
    out = train(load_config(a.config), a.data, a.eval, a.ckpt, a.max_minutes, a.max_steps)
    print(" ".join(f"{k}={v}" for k, v in out.items()))


if __name__ == "__main__":
    main()
