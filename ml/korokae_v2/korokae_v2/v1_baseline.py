"""Korokae v1 as the evaluation baseline (spec 7.2): loads the shipped korokae_v1.bin into v1's
own PyTorch model and runs its offline equivalent. Evaluation only; v1 is never trained on."""
from __future__ import annotations

import importlib.util
import os
import struct

import numpy as np
import torch

_REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
DEFAULT_V1 = os.path.join(_REPO, "app", "src", "main", "assets", "models", "korokae_v1.bin")
_V1_MODEL = os.path.join(_REPO, "ml", "korokae", "model.py")


def _v1_module():
    """ml/korokae/model.py under a unique name, so it cannot clash with any top-level `model`."""
    spec = importlib.util.spec_from_file_location("korokae_v1_model", _V1_MODEL)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


V1 = _v1_module()


def load_v1(path: str = DEFAULT_V1) -> torch.nn.Module:
    """Reads the KRK1 layout written by ml/korokae/export.py:export_weights."""
    with open(path, "rb") as f:
        data = f.read()
    if data[:4] != b"KRK1":
        raise ValueError(f"{path}: not a KRK1 file")
    version, n_fft, hop, hidden, layers = struct.unpack("<5i", data[4:24])
    if (version, n_fft, hop, hidden, layers) != (1, V1.N_FFT, V1.HOP, V1.HIDDEN, V1.LAYERS):
        raise ValueError(f"{path}: unexpected header {(version, n_fft, hop, hidden, layers)}")
    net = V1.KorokaeNet()
    order = ["mean", "std", "inp.weight", "inp.bias"]
    for l in range(layers):
        order += [f"gru.weight_ih_l{l}", f"gru.weight_hh_l{l}", f"gru.bias_ih_l{l}", f"gru.bias_hh_l{l}"]
    order += ["out.weight", "out.bias"]
    state = net.state_dict()
    offset = 24
    for name in order:
        n = state[name].numel()
        arr = np.frombuffer(data, dtype="<f4", count=n, offset=offset)
        state[name] = torch.from_numpy(arr.copy()).reshape(state[name].shape)
        offset += 4 * n
    if offset != len(data):
        raise ValueError(f"{path}: {len(data) - offset} trailing bytes")
    net.load_state_dict(state)
    return net.eval()


@torch.no_grad()
def v1_instrumental(net: torch.nn.Module, x: torch.Tensor) -> torch.Tensor:
    """x [2, L] -> v1's instrumental [2, L] (export.py's streaming_reference, re-aligned)."""
    length = x.shape[1]
    xp = torch.cat([torch.zeros(2, V1.HOP), x.float()], dim=1)
    n = xp.shape[1] - ((xp.shape[1] - V1.N_FFT) % V1.HOP)
    xp = xp[:, :n]
    spec = V1.stft(xp)
    mask = net(V1.stft(xp.mean(0, keepdim=True)).abs())
    y = V1.istft(mask * spec, n)[:, V1.HOP:V1.HOP + length]
    return torch.nn.functional.pad(y, (0, length - y.shape[1]))
