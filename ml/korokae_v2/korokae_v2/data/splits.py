"""Deterministic train / val / test assignment by singer or recording (spec 3.4)."""
from __future__ import annotations

import hashlib


def assign_splits(groups: list[str], test_frac: float = 0.1,
                  val_frac: float = 0.1) -> dict[str, str]:
    """Sort groups by sha1 and cut test, then val, then train.

    With n >= 3 groups every split gets at least one; n == 2 gives train + test; n == 1 train.
    """
    ordered = sorted(set(groups), key=lambda g: hashlib.sha1(g.encode("utf-8")).hexdigest())
    n = len(ordered)
    n_test = max(1, round(test_frac * n)) if n >= 2 else 0
    n_val = max(1, round(val_frac * n)) if n >= 3 else 0
    out = {}
    for i, g in enumerate(ordered):
        out[g] = "test" if i < n_test else "val" if i < n_test + n_val else "train"
    return out
