"""Train KorokaeNet2 (Standard or High) on MUSDB18 stems with the v2 loss and augmentation.

Usage: python3 train2.py --config {standard,high} --steps N [--bs 16]
Writes checkpoints/korokae2_<config>.pt (best by acc SDR + 0.25 * vocal attenuation) and
checkpoints/train2_<config>.log.
"""
import argparse
import pathlib
import queue
import random
import sys
import threading
import time

import numpy as np
import torch

from augment import random_eq, random_reverb
from evaluate import evaluate, load_tracks
from losses import loss_v2
from model import HOP, N_FFT, SR, istft, stft
from model2 import HIGH, STANDARD, KorokaeNet2, make_mask_fn
from train import crop

ROOT = pathlib.Path(__file__).resolve().parent
CONFIGS = {"standard": STANDARD, "high": HIGH}

P_INSTRUMENTAL = 0.2  # vocal zeroed so instrumentals pass untouched (v1 used 0.1)
P_CODEC = 0.5         # sample the track from the codec-degraded set
P_REVERB = 0.5
P_EQ = 0.5
ATTENUATION_WEIGHT = 0.25


def seg_len(seconds: float) -> int:
    n = int(seconds * SR)
    return n - ((n - N_FFT) % HOP)  # whole number of frames


def spec(x: torch.Tensor) -> torch.Tensor:
    """[B, 2, T] -> complex [B, 2, BINS, F]."""
    B, C, T = x.shape
    X = stft(x.reshape(B * C, T))
    return X.reshape(B, C, *X.shape[1:])


def mid_side_mags(mix: torch.Tensor):
    """mix [B, 2, T] -> (|STFT(mid)|, |STFT(side)|), each [B, BINS, F]."""
    return stft(mix.mean(1)).abs(), stft(0.5 * (mix[:, 0] - mix[:, 1])).abs()


def make_batch(sets, bs, seg, device, rng):
    accs, vocs = [], []
    for _ in range(bs):
        tracks = sets["codec"] if sets["codec"] and random.random() < P_CODEC else sets["clean"]
        if random.random() < 0.7:  # accompaniment and vocals from (usually) different tracks
            a = crop(random.choice(tracks)[0], seg)
            v = crop(random.choice(tracks)[1], seg)
        else:
            ta = random.choice(tracks)
            s = random.randint(0, max(0, ta[0].shape[1] - seg))
            a, v = crop(ta[0], seg, s), crop(ta[1], seg, s)
        a = a * 10 ** (random.uniform(-6, 3) / 20)
        v = v.to(device) * 10 ** (random.uniform(-6, 6) / 20)  # augment on the training device
        if random.random() < 0.5:
            a = a.flip(0)
        if random.random() < 0.5:
            v = v.flip(0)
        if random.random() < P_INSTRUMENTAL:
            v = v * 0
        else:
            if random.random() < P_REVERB:
                v = random_reverb(v, rng)
            if random.random() < P_EQ:
                v = random_eq(v, rng)
        accs.append(a)
        vocs.append(v)
    acc = torch.stack(accs).to(device)
    voc = torch.stack(vocs).to(device)
    mix = acc + voc
    peak = mix.abs().amax(dim=(1, 2), keepdim=True).clamp(min=1.0)
    return mix / peak, acc / peak, voc / peak


def prefetch(sets, bs, seg, device, rng, steps, depth=6):
    """Builds batches on a background thread so CPU data work overlaps the GPU step."""
    q = queue.Queue(maxsize=depth)

    def work():
        for _ in range(steps):
            q.put(make_batch(sets, bs, seg, device, rng))

    threading.Thread(target=work, daemon=True).start()
    return q


def step_loss(net, mix, acc, voc):
    X, S, V = spec(mix), spec(acc), spec(voc)
    mid, side = mid_side_mags(mix)
    masks = net(mid, side)  # [B, 2, BINS, F]
    est_wave = istft(masks * X, mix.shape[-1])
    return loss_v2(masks, X, S, V, est_wave, acc)


@torch.no_grad()
def compute_norm(net, tracks, device):
    mids, sides = [], []
    for acc, voc in tracks:
        mix = (acc + voc).to(device)
        mids.append(torch.log1p(stft(mix.mean(0, keepdim=True)).abs())[0].T)
        sides.append(torch.log1p(stft(0.5 * (mix[0:1] - mix[1:2])).abs())[0].T)
    f = torch.stack([torch.cat(mids), torch.cat(sides)])  # [2, N, BINS]
    net.mean.copy_(f.mean(1))
    net.std.copy_(f.std(1).clamp(min=1e-3))


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--config", choices=sorted(CONFIGS), required=True)
    ap.add_argument("--steps", type=int, default=12000)
    ap.add_argument("--bs", type=int, default=16)
    ap.add_argument("--data-root", default=str(ROOT / "data"))
    ap.add_argument("--ckpt-dir", default=str(ROOT / "checkpoints"))
    ap.add_argument("--eval-every", type=int, default=500)
    ap.add_argument("--eval-tracks", type=int, default=12, help="evenly spaced test tracks scored during training")
    ap.add_argument("--seg-seconds", type=float, default=6.0)
    ap.add_argument("--device", default="cuda" if torch.cuda.is_available() else "cpu")
    args = ap.parse_args(argv)

    torch.manual_seed(0)
    random.seed(0)
    rng = torch.Generator().manual_seed(0)
    device = args.device
    cfg = CONFIGS[args.config]
    ckpt_dir = pathlib.Path(args.ckpt_dir)
    ckpt_dir.mkdir(parents=True, exist_ok=True)
    ckpt_path = ckpt_dir / f"korokae2_{cfg.name}.pt"
    log = open(ckpt_dir / f"train2_{cfg.name}.log", "w")

    def say(msg):
        print(msg, flush=True)
        log.write(msg + "\n")
        log.flush()

    sets = {"clean": load_tracks("train", args.data_root),
            "codec": load_tracks("train", args.data_root, subdir="npy_codec")}
    test = load_tracks("test", args.data_root)
    idx = np.linspace(0, len(test) - 1, min(args.eval_tracks, len(test))).astype(int)
    test = [test[i] for i in idx]
    say(f"config={cfg} device={device} train={len(sets['clean'])} codec={len(sets['codec'])} eval={len(test)}")

    seg = seg_len(args.seg_seconds)
    net = KorokaeNet2(cfg).to(device)
    compute_norm(net, sets["clean"], device)
    opt = torch.optim.AdamW(net.parameters(), lr=1e-3, weight_decay=1e-4)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=1e-3, total_steps=args.steps,
                                                pct_start=max(0.05, 3 / args.steps))  # >=3 warm-up steps

    best = -float("inf")
    t0 = time.time()
    batches = prefetch(sets, args.bs, seg, device, rng, args.steps)
    for step in range(1, args.steps + 1):
        mix, acc, voc = batches.get()
        loss = step_loss(net, mix, acc, voc)
        opt.zero_grad(set_to_none=True)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(net.parameters(), 5.0)
        opt.step()
        sched.step()
        if step % args.eval_every == 0 or step == args.steps:
            net.eval()
            r = evaluate(make_mask_fn(net), test, device)
            net.train()
            score = r["acc_sdr_median"] + ATTENUATION_WEIGHT * r["vocal_attenuation_median_db"]
            say(f"step {step} loss {loss.item():.4f} sdr {r['acc_sdr_median']:.2f} dB "
                f"vocal-att {r['vocal_attenuation_median_db']:.2f} dB "
                f"passthrough {r['passthrough_sdr_median']:.2f} dB score {score:.2f} {time.time() - t0:.0f}s")
            if np.isfinite(score) and score > best:
                best = score
                torch.save(net.state_dict(), ckpt_path)
    if not ckpt_path.exists():
        torch.save(net.state_dict(), ckpt_path)
    say(f"best score {best:.2f}")
    log.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
