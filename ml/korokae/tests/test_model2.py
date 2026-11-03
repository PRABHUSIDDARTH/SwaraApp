import torch

from model import BINS, KorokaeNet
from model2 import HIGH, STANDARD, KorokaeNet2


def _inputs(F=40, seed=0):
    g = torch.Generator().manual_seed(seed)
    return (torch.rand(2, BINS, F, generator=g) * 3, torch.rand(2, BINS, F, generator=g) * 3)


def _net(cfg):
    torch.manual_seed(1)
    return KorokaeNet2(cfg).eval()


def _params(m):
    return sum(p.numel() for p in m.parameters())


def test_output_shape():
    for cfg in (STANDARD, HIGH):
        mid, side = _inputs()
        with torch.no_grad():
            out = _net(cfg)(mid, side)
        assert out.shape == (2, 2, BINS, 40)
        assert float(out.min()) > 0 and float(out.max()) < 1


def test_standard_is_strictly_causal():
    net = _net(STANDARD)
    mid, side = _inputs()
    base = net(mid, side)
    mid2, side2 = mid.clone(), side.clone()
    mid2[:, :, 20] += 1.0
    side2[:, :, 20] += 1.0
    out = net(mid2, side2)
    assert float((out[..., :20] - base[..., :20]).abs().max()) < 1e-7
    assert float((out[..., 20:] - base[..., 20:]).abs().max()) > 1e-6


def test_high_lookahead_exactly_two():
    net = _net(HIGH)
    mid, side = _inputs()
    base = net(mid, side)
    t = 10

    def changed_at_t(frame):
        m, s = mid.clone(), side.clone()
        m[:, :, frame] += 1.0
        s[:, :, frame] += 1.0
        return float((net(m, s)[..., t] - base[..., t]).abs().max())

    assert changed_at_t(t + 2) > 1e-6
    assert changed_at_t(t + 3) < 1e-7


def test_standard_param_count_within_budget():
    assert _params(KorokaeNet2(STANDARD)) <= 1.3 * _params(KorokaeNet())
