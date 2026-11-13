"""Licence-audited data manifest for the wake-word pipeline.

One CSV row per audio file. Only CC0-1.0, CC-BY-3.0, CC-BY-4.0 and our own audio are kept;
anything non-commercial, no-derivatives or share-alike is rejected. Own *recordings* of people
additionally need an explicit consent flag; synthetic (Kokoro) rows use consent "n/a".

normalise_licence, the allowlist and the audit/IO helpers are adapted from the Korokae v2
manifest (branch feature/korokae-v2-training: ml/korokae_v2/korokae_v2/data/manifest.py); the
two ml projects are independent packages, so the code is copied rather than imported.

CLI: python -m wakeword.data.manifest audit --root DIR   (exit 1 when problems are found)
"""
from __future__ import annotations

import argparse
import csv
import dataclasses
import hashlib
import os
import re
import sys
from dataclasses import dataclass

ALLOWED_LICENCES = ("CC0-1.0", "CC-BY-3.0", "CC-BY-4.0", "own")
LABELS = ("positive", "negative", "near_miss", "background")
OWN_SOURCES = ("kokoro-82m", "recording", "synthetic-noise")  # the only sources that may be "own"
SPLITS = ("train", "val", "test")
AUDIO_EXTS = (".wav", ".flac", ".mp3")
MANIFEST_NAME = "manifest.csv"


@dataclass(frozen=True)
class Row:
    path: str  # relative to the data root (absolute for files referenced in place)
    label: str  # one of LABELS
    source: str
    source_url: str  # official licence / record page checked at download time
    author: str
    licence: str  # one of ALLOWED_LICENCES
    attribution: str
    speaker: str  # speaker / voice id, used for splitting
    split: str  # "train" | "val" | "test"
    consent: str = "n/a"  # "yes" for own recordings of people, else "n/a"
    consent_ts: str = ""  # ISO-8601 UTC time the consent was given (recordings only)


FIELDS = [f.name for f in dataclasses.fields(Row)]


_CC_URL = re.compile(r"^https?://(www\.)?creativecommons\.org/"
                     r"(licenses/by/(?P<by>3\.0|4\.0)|publicdomain/zero/(?P<zero>1\.0))"
                     r"(/(legalcode(\.en)?)?)?/?$")
_NAME_FORMS = (
    re.compile(r"^cc-by-(?P<v>3\.0|4\.0)(-international)?$"),
    re.compile(r"^(creative-commons-)?attribution-\(?(?P<v>3\.0|4\.0)\)?(-international)?$"),
    re.compile(r"^(creative-commons-)?attribution-(?P<v>3\.0|4\.0)-(international|unported)$"),
)
_ZERO_FORMS = {"cc0", "cc0-1.0", "cc0-1.0-universal", "creative-commons-zero-1.0"}
_FORBIDDEN = ("non", "commercial", "share", "alike", "deriv", "gpl", "nc", "nd", "sa", "no-")


def normalise_licence(text: str) -> str | None:
    """Strict allowlist: map an allowed licence id, CC URL or plain name to its id, else None.

    Anything that is not exactly CC0 1.0, CC BY 3.0 / 4.0 or "own" is rejected, including any
    string that mentions non-commercial, share-alike or no-derivatives terms.
    """
    raw = (text or "").strip().lower()
    if raw == "own":
        return "own"
    m = _CC_URL.match(raw)
    if m:
        return "CC0-1.0" if m.group("zero") else f"CC-BY-{m.group('by')}"
    t = re.sub(r"[\s_,-]+", "-", raw).strip("-")
    if any(f in t.split("-") or (len(f) > 2 and f in t) for f in _FORBIDDEN):
        return None
    if t in _ZERO_FORMS:
        return "CC0-1.0"
    for form in _NAME_FORMS:
        m = form.match(t)
        if m:
            return f"CC-BY-{m.group('v')}"
    return None


class ManifestError(ValueError):
    pass


def split_for_id(key: str) -> str:
    """Deterministic 80/10/10 split by hashed id (used where ids are too many to rank)."""
    h = int(hashlib.sha1(key.encode()).hexdigest(), 16) % 10
    return "test" if h == 0 else "val" if h == 1 else "train"


def assign_splits(ids: list[str]) -> dict[str, str]:
    """Deterministic split by hashed rank so val and test each get at least one id (when >= 3 ids)."""
    ranked = sorted(set(ids), key=lambda i: hashlib.sha1(i.encode()).hexdigest())
    n_hold = max(1, round(0.1 * len(ranked))) if len(ranked) >= 3 else 0
    out = {}
    for k, i in enumerate(ranked):
        out[i] = "test" if k < n_hold else "val" if k < 2 * n_hold else "train"
    return out


def validate_rows(rows: list[Row]) -> list[str]:
    """Row-level rules that need no files; the write-time licence gate. Empty means valid."""
    problems = []
    seen = set()
    speaker_splits: dict[str, set[str]] = {}
    for r in rows:
        if r.path in seen:
            problems.append(f"{r.path}: duplicate path")
        seen.add(r.path)
        if r.licence not in ALLOWED_LICENCES:
            problems.append(f"{r.path}: licence {r.licence!r} is not allowed")
        if r.licence == "own" and r.source not in OWN_SOURCES:
            problems.append(f"{r.path}: licence 'own' is not allowed for source {r.source!r}")
        if r.label not in LABELS:
            problems.append(f"{r.path}: label {r.label!r} is not one of {LABELS}")
        if r.split not in SPLITS:
            problems.append(f"{r.path}: split {r.split!r} is not one of {SPLITS}")
        for name in ("source", "licence", "attribution", "author", "speaker"):
            if not getattr(r, name).strip():
                problems.append(f"{r.path}: empty {name}")
        if r.licence != "own" and not r.source_url.strip():
            problems.append(f"{r.path}: source_url required for licence {r.licence!r}")
        if r.licence == "own" and r.source == "recording" and (r.consent != "yes" or not r.consent_ts):
            problems.append(f"{r.path}: own recording without consent "
                            f"(consent={r.consent!r}, consent_ts={r.consent_ts!r})")
        speaker_splits.setdefault(r.speaker, set()).add(r.split)
    for spk, sp in speaker_splits.items():
        if len(sp) > 1:
            problems.append(f"speaker {spk!r} appears in more than one split: {sorted(sp)}")
    return problems


def write_manifest(rows: list[Row], path: str, validate: bool = True) -> None:
    if validate:
        problems = validate_rows(rows)
        if problems:
            raise ManifestError("refusing to write manifest:\n" + "\n".join(problems[:20]))
    tmp = path + ".part"
    with open(tmp, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS)
        w.writeheader()
        for r in rows:
            w.writerow(dataclasses.asdict(r))
    os.replace(tmp, path)


def read_manifest(path: str) -> list[Row]:
    with open(path, newline="", encoding="utf-8") as f:
        return [Row(**{k: rec.get(k, "") or ("n/a" if k == "consent" else "") for k in FIELDS}) for rec in csv.DictReader(f)]


def append_rows(rows: list[Row], root: str) -> None:
    """Add rows to root/manifest.csv (created when absent)."""
    path = os.path.join(root, MANIFEST_NAME)
    existing = read_manifest(path) if os.path.isfile(path) else []
    write_manifest(existing + list(rows), path)


def audit(rows: list[Row], data_root: str) -> list[str]:
    """Problems with the manifest against the files under data_root; empty means it passes."""
    problems = validate_rows(rows)
    listed = set()
    for r in rows:
        listed.add(os.path.normpath(r.path))
        if not os.path.isfile(os.path.join(data_root, r.path)):
            problems.append(f"{r.path}: listed but missing")
    for dirpath, _, files in os.walk(data_root):
        for name in files:
            if name.endswith(AUDIO_EXTS):
                rel = os.path.normpath(os.path.relpath(os.path.join(dirpath, name), data_root))
                if rel not in listed:
                    problems.append(f"{rel}: audio file not in the manifest")
    return problems


def main(argv: list[str] | None = None) -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("command", choices=["audit"])
    ap.add_argument("--root", required=True)
    args = ap.parse_args(argv)
    problems = audit(read_manifest(os.path.join(args.root, MANIFEST_NAME)), args.root)
    for p in problems:
        print(p)
    print(f"audit: {len(problems)} problem(s)")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
