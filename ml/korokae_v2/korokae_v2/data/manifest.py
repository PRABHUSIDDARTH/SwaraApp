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


def normalise_licence(text: str) -> str | None:
    """Map a licence name or Creative Commons URL to an allowed id, or None to reject."""
    t = (text or "").strip().lower()
    if not t:
        return None
    if t == "own":
        return "own"
    if t in {l.lower() for l in ALLOWED_LICENCES}:
        return next(l for l in ALLOWED_LICENCES if l.lower() == t)
    words = set(re.split(r"[^a-z0-9.]+", t))
    if words & {"nc", "nd", "sa", "noncommercial", "noderivatives", "noderivs", "sharealike",
                "by-nc", "by-sa", "by-nd"} or re.search(r"by-(nc|nd|sa)", t):
        return None
    if "publicdomain/zero/1.0" in t or re.fullmatch(r"cc0[\s-]*1\.0", t) or t == "cc0":
        return "CC0-1.0"
    version = re.search(r"(\d\.\d)", t)
    is_by = ("/licenses/by/" in t or "attribution" in words or "by" in words)
    if is_by and version and version.group(1) in ("3.0", "4.0"):
        return f"CC-BY-{version.group(1)}"
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
