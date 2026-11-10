"""Attribution text for the in-app "Korokae model credits" screen (spec 3.1, 6.5).

CLI: python -m korokae_v2.data.credits --manifest M --out F
"""
from __future__ import annotations

import argparse

from .manifest import Row, read_manifest

HEADER = ("Korokae's vocal-removal model was trained by Swara on the following recordings,\n"
          "used under Creative Commons Attribution licences:\n")


def credits_text(rows: list[Row]) -> str:
    seen = {}
    for r in rows:
        if r.licence.startswith("CC-BY"):
            seen.setdefault((r.source, r.author), r)
    lines = [f"{r.attribution} — {r.licence} — {r.source_url}"
             for _, r in sorted(seen.items(), key=lambda kv: kv[0])]
    return HEADER + "\n" + "\n".join(lines) + "\n"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--manifest", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    with open(args.out, "w", encoding="utf-8") as f:
        f.write(credits_text(read_manifest(args.manifest)))


if __name__ == "__main__":
    main()
