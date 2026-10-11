"""Train KorokaeNet on MUSDB18 accompaniment/vocal stems (GPU when available).

Usage: python3 train.py [--steps N]
"""
import argparse
import pathlib
import random
import time

import numpy as np
import torch

from model import HOP, N_FFT, SR, KorokaeNet, stft

ROOT = pathlib.Path(__file__).resolve().parent
SEG = 6 * SR - ((6 * SR - N_FFT) % HOP)  # whole number of frames


def load(split):
    tracks = []
    for p in sorted((ROOT / "data" / "npy" / split).glob("*.npz")):
        d = np.load(p)
        tracks.append((torch.from_numpy(d["acc"]), torch.from_numpy(d["voc"])))
    return tracks


def crop(x, n, start=None):
    if x.shape[1] < n:
        x = torch.nn.functional.pad(x, (0, n - x.shape[1]))
    if start is None:
        start = random.randint(0, x.shape[1] - n)
    return x[:, start:start + n]


def batch(tracks, bs, device):
    """Random remix: accompaniment and vocals from (usually) different tracks."""
    accs, vocs = [], []
    for _ in range(bs):
        a = crop(random.choice(tracks)[0], SEG)
        v = crop(random.choice(tracks)[1], SEG) if random.random() < 0.7 else None
        if v is None:
            ta = random.choice(tracks)
            s = random.randint(0, max(0, ta[0].shape[1] - SEG))
            a, v = crop(ta[0], SEG, s), crop(ta[1], SEG, s)
        a = a * 10 ** (random.uniform(-6, 3) / 20)
        v = v * 10 ** (random.uniform(-6, 6) / 20)
        if random.random() < 0.5:
            a = a.flip(0)
        if random.random() < 0.5:
            v = v.flip(0)
        if random.random() < 0.1:
            v = v * 0  # teach the net to pass instrumental music untouched
        accs.append(a)
        vocs.append(v)
    acc = torch.stack(accs).to(device)
    voc = torch.stack(vocs).to(device)
    mix = acc + voc
    peak = mix.abs().amax(dim=(1, 2), keepdim=True).clamp(min=1.0)
    return mix / peak, acc / peak


def spectra(mix, acc):
    B = mix.shape[0]
    X = stft(mix.reshape(B * 2, -1)).reshape(B, 2, *stft(mix[:1, 0]).shape[1:])
    S = stft(acc.reshape(B * 2, -1)).reshape(X.shape)
    mid = stft(mix.mean(1))
    return X, S, mid.abs()


def loss_fn(net, mix, acc):
    X, S, mid_mag = spectra(mix, acc)
    mask = net(mid_mag).unsqueeze(1)
    est = mask * X
    c = 0.3
    mag = (est.abs() + 1e-8) ** c - (S.abs() + 1e-8) ** c
    cplx = est / (est.abs() + 1e-8) * (est.abs() + 1e-8) ** c - S / (S.abs() + 1e-8) * (S.abs() + 1e-8) ** c
    return mag.abs().mean() + 0.3 * cplx.abs().mean()


@torch.no_grad()
def compute_norm(net, tracks, device):
    feats = []
    for acc, voc in tracks:
        mix = (acc + voc).mean(0, keepdim=True).to(device)
        feats.append(torch.log1p(stft(mix).abs())[0].T)
    f = torch.cat(feats)
    net.mean.copy_(f.mean(0))
    net.std.copy_(f.std(0).clamp(min=1e-3))


def sdr(est, ref):
    num = (ref ** 2).sum()
    den = ((ref - est) ** 2).sum().clamp(min=1e-10)
    return 10 * torch.log10(num.clamp(min=1e-10) / den)


@torch.no_grad()
def evaluate(net, tracks, device):
    from model import istft
    net.eval()
    gains, base = [], []
    for acc, voc in tracks:
        mix = (acc + voc).to(device)
        acc = acc.to(device)
        n = mix.shape[1] - ((mix.shape[1] - N_FFT) % HOP)
        mix, acc = mix[:, :n], acc[:, :n]
        if (acc ** 2).sum() < 1e-6 or (voc ** 2).sum() < 1e-6:
            continue
        X = stft(mix)
        mask = net(stft(mix.mean(0, keepdim=True)).abs())
        est = istft(mask * X, n)
        e = slice(N_FFT, n - N_FFT)
        gains.append(sdr(est[:, e], acc[:, e]).item())
        base.append(sdr(mix[:, e], acc[:, e]).item())
    net.train()
    return float(np.median(gains)), float(np.median(base))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--steps", type=int, default=20000)
    ap.add_argument("--bs", type=int, default=16)
    args = ap.parse_args()

    torch.manual_seed(0)
    random.seed(0)
    device = "cuda" if torch.cuda.is_available() else "cpu"
    print("device:", device, torch.cuda.get_device_name(0) if device == "cuda" else "")

    train, test = load("train"), load("test")
    print(f"train tracks={len(train)} test tracks={len(test)}")
    net = KorokaeNet().to(device)
    compute_norm(net, train, device)
    opt = torch.optim.AdamW(net.parameters(), lr=1e-3, weight_decay=1e-4)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=1e-3, total_steps=args.steps, pct_start=0.05)

    ckpt_dir = ROOT / "checkpoints"
    ckpt_dir.mkdir(exist_ok=True)
    best = -1e9
    t0 = time.time()
    for step in range(1, args.steps + 1):
        mix, acc = batch(train, args.bs, device)
        loss = loss_fn(net, mix, acc)
        opt.zero_grad(set_to_none=True)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(net.parameters(), 5.0)
        opt.step()
        sched.step()
        if step % 500 == 0 or step == args.steps:
            sdr_est, sdr_mix = evaluate(net, test, device)
            print(f"step {step} loss {loss.item():.4f} test median SDR acc {sdr_est:.2f} dB "
                  f"(mixture baseline {sdr_mix:.2f} dB) {time.time() - t0:.0f}s", flush=True)
            if sdr_est > best:
                best = sdr_est
                torch.save(net.state_dict(), ckpt_dir / "korokae_best.pt")
    print(f"best test median accompaniment SDR {best:.2f} dB")


if __name__ == "__main__":
    main()
