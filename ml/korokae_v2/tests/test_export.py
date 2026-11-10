import struct

import numpy as np
import onnxruntime
import torch

from korokae_v2.export import export_onnx, write_parity_fixture
from korokae_v2.model import KorokaeNetV2


def test_onnx_matches_torch(tmp_path):
    torch.manual_seed(0)
    m = KorokaeNetV2(n_blocks=2, d=32, heads=2).eval()
    p = tmp_path / "m.onnx"
    export_onnx(m, str(p))
    x = torch.randn(1, 4, 1025, 37)
    sess = onnxruntime.InferenceSession(str(p))
    got = sess.run(["mask"], {"spec": x.numpy()})[0]
    with torch.no_grad():
        want = m(x).numpy()
    assert np.max(np.abs(got - want)) < 1e-4


def test_parity_fixture_layout(tmp_path):
    m = KorokaeNetV2(n_blocks=1, d=16, heads=2).eval()
    p = tmp_path / "f.bin"
    write_parity_fixture(m, str(p), seconds=2.0)
    b = p.read_bytes()
    assert b[:4] == b"KRP2"
    version, sr, frames = struct.unpack("<iii", b[4:16])
    assert (version, sr, frames) == (1, 44100, 88200)
    assert len(b) == 16 + 2 * frames * 2 * 4
