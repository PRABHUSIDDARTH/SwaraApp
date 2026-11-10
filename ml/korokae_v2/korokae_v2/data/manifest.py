"""Licence-audited data manifest (spec section 3.1).

One CSV row per audio file. Only CC0-1.0, CC-BY-3.0, CC-BY-4.0 and our own recordings are kept;
anything non-commercial, no-derivatives or share-alike is rejected.

CLI: python -m korokae_v2.data.manifest audit --root DIR   (exit 1 when problems are found)
"""
from __future__ import annotations

import argparse
import csv
import dataclasses
import os
import re
import sys
from dataclasses import dataclass

ALLOWED_LICENCES = ("CC0-1.0", "CC-BY-3.0", "CC-BY-4.0", "own")
MANIFEST_NAME = "manifest.csv"


@dataclass(frozen=True)
class Row:
    path: str  # relative to the data root
    role: str  # "vocal" | "accompaniment"
    source: str
    source_url: str  # official licence / record page checked at download time
    author: str
    licence: str  # one of ALLOWED_LICENCES
    attribution: str
    group: str  # singer or recording id, used for splitting
    split: str  # "train" | "val" | "test"


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


def write_manifest(rows: list[Row], path: str) -> None:
    tmp = path + ".part"
    with open(tmp, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS)
        w.writeheader()
        for r in rows:
            w.writerow(dataclasses.asdict(r))
    os.replace(tmp, path)


def read_manifest(path: str) -> list[Row]:
    with open(path, newline="", encoding="utf-8") as f:
        return [Row(**{k: rec[k] for k in FIELDS}) for rec in csv.DictReader(f)]


def merge_manifests(roots: list[str]) -> list[Row]:
    """Rows of root/manifest.csv for each root, with absolute paths (os.path.join(any_root, abs)
    is abs, so the merged list works with any data root)."""
    rows = []
    for root in roots:
        for r in read_manifest(os.path.join(root, MANIFEST_NAME)):
            rows.append(dataclasses.replace(r, path=os.path.abspath(os.path.join(root, r.path))))
    return rows


def audit(rows: list[Row], data_root: str) -> list[str]:
    """Problems with the manifest against the files under data_root; empty means it passes."""
    problems = []
    listed = set()
    for r in rows:
        listed.add(os.path.normpath(r.path))
        if r.licence not in ALLOWED_LICENCES:
            problems.append(f"{r.path}: licence {r.licence!r} is not allowed")
        if not os.path.isfile(os.path.join(data_root, r.path)):
            problems.append(f"{r.path}: listed but missing")
    for dirpath, _, files in os.walk(data_root):
        for name in files:
            if name.endswith(".flac"):
                rel = os.path.normpath(os.path.relpath(os.path.join(dirpath, name), data_root))
                if rel not in listed:
                    problems.append(f"{rel}: audio file not in the manifest")
    return problems


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("command", choices=["audit"])
    ap.add_argument("--root", required=True)
    args = ap.parse_args()
    problems = audit(read_manifest(os.path.join(args.root, MANIFEST_NAME)), args.root)
    for p in problems:
        print(p)
    print(f"audit: {len(problems)} problem(s)")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
