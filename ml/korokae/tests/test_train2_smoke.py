import numpy as np
import torch

from model2 import STANDARD, KorokaeNet2


def _make_data(root, n_train=3, n_test=2, seconds=5):
    rng = np.random.default_rng(0)
    for split, n in (("train", n_train), ("test", n_test)):
        d = root / "npy" / split
        d.mkdir(parents=True)
        for i in range(n):
            T = 44100 * seconds
            np.savez(d / f"t{i}.npz",
                     acc=(rng.standard_normal((2, T)) * 0.1).astype(np.float32),
                     voc=(rng.standard_normal((2, T)) * 0.1).astype(np.float32))


def test_train2_runs_20_steps(tmp_path):
    import train2

    _make_data(tmp_path)
    ckpt = tmp_path / "ckpt"
    rc = train2.main(["--config", "standard", "--steps", "20", "--bs", "2",
                      "--data-root", str(tmp_path), "--ckpt-dir", str(ckpt),
                      "--eval-every", "20", "--eval-tracks", "1",
                      "--seg-seconds", "2", "--device", "cpu"])
    assert rc == 0
    path = ckpt / "korokae2_standard.pt"
    assert path.exists()
    net = KorokaeNet2(STANDARD)
    net.load_state_dict(torch.load(path, map_location="cpu"))
    assert (ckpt / "train2_standard.log").exists()
