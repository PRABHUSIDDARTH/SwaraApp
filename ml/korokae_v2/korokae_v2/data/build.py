"""Build a data root: fetch sources, assign splits, write manifest.csv, audit.

CLI: python -m korokae_v2.data.build --sources vocalset,vocadito,ccmixter --out DIR
         --budget-hours H [--work DIR] [--dry-run]
"""
from __future__ import annotations

import argparse
import dataclasses
import os
import sys
import tempfile

from . import sources
from .manifest import MANIFEST_NAME, audit, write_manifest
from .splits import assign_splits

ZENODO_RECORDS = {"vocalset": 1203819, "vocadito": 5578807, "jacrc": 6536490, "adovoc": 3383118,
                  "albumdb": 19683001, "slakh": 4599666}


def build(names: list[str], out: str, work: str, budget_hours: float,
          http: sources.Http | None = None) -> list:
    http = http or sources.UrllibHttp()
    rows = []
    for name in names:
        got = list(sources.FETCHERS[name](os.path.join(work, name), out, http, budget_hours * 3600))
        if name != "slakh":
            split_of = assign_splits([r.group for r in got])
            got = [dataclasses.replace(r, split=split_of[r.group]) for r in got]
        rows += got
        print(f"{name}: {len(got)} files, {len({r.group for r in got})} groups", flush=True)
    write_manifest(rows, os.path.join(out, MANIFEST_NAME))
    problems = audit(rows, out)
    for p in problems:
        print(p)
    if problems:
        raise SystemExit(f"audit failed: {len(problems)} problem(s)")
    return rows


def dry_run(names: list[str], http: sources.Http | None = None) -> None:
    http = http or sources.UrllibHttp()
    for name in names:
        if name == "ccmixter":
            recs = list(sources.ccmixter_records(http))
            reasons = sources.ccmixter_reasons(recs)
            singers = {r["user_name"] for r in recs if sources.ccmixter_reason(r) == "accepted"}
            print(f"ccmixter: {len(recs)} records, {reasons}, accepted singers {len(singers)}")
        else:
            licence, files = sources.zenodo_record(http, ZENODO_RECORDS[name])
            print(f"{name}: licence {licence}, files {sorted(files)}")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--sources", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--work", default=None)
    ap.add_argument("--budget-hours", type=float, default=1e6)
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()
    names = [s.strip() for s in args.sources.split(",") if s.strip()]
    unknown = set(names) - set(sources.FETCHERS)
    if unknown:
        sys.exit(f"unknown sources: {sorted(unknown)}")
    if args.dry_run:
        dry_run(names)
    else:
        # work files (zips, temp audio) stay outside `out`, which becomes the published dataset
        build(names, args.out, args.work or os.path.join(tempfile.gettempdir(), "korokae_work"),
              args.budget_hours)


if __name__ == "__main__":
    main()
