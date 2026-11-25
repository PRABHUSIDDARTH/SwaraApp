"""Licence-gated fetch of speech negatives and music backgrounds.

Every source passes `manifest.normalise_licence` before anything is downloaded; a source whose
licence is not on the allowlist raises LicenceError and nothing is fetched.

  * LibriSpeech train-clean-100 / dev-clean / test-clean (CC BY 4.0) -> speech negatives
  * backgrounds: the Korokae v2 audited manifest CSV (accompaniment rows, re-checked against the
    allowlist) when it exists, otherwise ccMixter (CC BY; every upload's licence URL is gated)

CLI: python -m wakeword.data.fetch --dry-run --config configs/base.yaml   (no network)
     python -m wakeword.data.fetch --config configs/base.yaml --root data/external
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import hashlib
import tarfile
import urllib.parse
import urllib.request
from dataclasses import dataclass

import yaml

from .manifest import ManifestError, Row, assign_splits, normalise_licence, split_for_id, write_manifest

LIBRISPEECH_PAGE = "https://www.openslr.org/12/"
LIBRISPEECH_ATTRIBUTION = ("LibriSpeech ASR corpus (Panayotov, Chen, Povey, Khudanpur; "
                           "LibriVox readers), CC BY 4.0")


class LicenceError(ValueError):
    pass


@dataclass(frozen=True)
class FetchItem:
    source: str
    name: str
    url: str
    licence: str  # normalised id, or a note when gated per file
    allowed: bool


HTTP_TIMEOUT = 60
MAX_JSON_BYTES = 5 * 1024 * 1024
MAX_AUDIO_BYTES = 100 * 1024 * 1024  # per ccMixter file
MAX_ARCHIVE_BYTES = 8 * 1024 ** 3  # train-clean-100 is ~6.3 GB
AUDIO_EXTS_OK = (".mp3", ".flac", ".wav", ".ogg")


def _check_url(url: str) -> None:
    if urllib.parse.urlparse(url).scheme not in ("http", "https"):
        raise ValueError(f"only http(s) URLs are allowed, got {url!r}")


def _read_capped(url: str, sink, max_bytes: int) -> None:
    _check_url(url)
    total = 0
    with urllib.request.urlopen(url, timeout=HTTP_TIMEOUT) as r:  # noqa: S310 (scheme checked)
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                return
            total += len(chunk)
            if total > max_bytes:
                raise ValueError(f"{url}: larger than the {max_bytes} byte cap")
            sink(chunk)


def _http_get(url: str) -> bytes:
    parts: list[bytes] = []
    _read_capped(url, parts.append, MAX_JSON_BYTES)
    return b"".join(parts)


def _download(url: str, dest: str, max_bytes: int = MAX_AUDIO_BYTES) -> None:
    os.makedirs(os.path.dirname(dest) or ".", exist_ok=True)
    tmp = dest + ".part"
    try:
        with open(tmp, "wb") as f:
            _read_capped(url, f.write, max_bytes)
        os.replace(tmp, dest)
    finally:
        if os.path.exists(tmp):
            os.remove(tmp)


def _gate(source: str, text: str) -> str:
    lic = normalise_licence(text)
    if lic is None or lic == "own":
        raise LicenceError(f"{source}: licence {text!r} is not on the allowlist; refusing to fetch")
    return lic


def plan(cfg: dict, base_dir: str = ".") -> list[FetchItem]:
    """What would be fetched, with the licence gate applied (raises LicenceError on a bad source)."""
    items = []
    ls = cfg["librispeech"]
    lic = _gate("librispeech", ls["licence"])
    for name, sub in ls["subsets"].items():
        items.append(FetchItem("librispeech", name, sub["url"], lic, True))
    csv_path = os.path.join(base_dir, cfg.get("korokae_manifest_csv", "") or "")
    if cfg.get("korokae_manifest_csv") and os.path.isfile(csv_path):
        items.append(FetchItem("korokae-v2", csv_path, csv_path, "per-row (allowlist re-checked)", True))
    else:
        cm = cfg["ccmixter"]
        items.append(FetchItem("ccmixter", "background music", cm["query_url"],
                               "per-file (CC BY only, licence URL gated)", True))
    return items


def backgrounds_from_korokae(csv_path: str) -> list[Row]:
    """Background rows (referenced in place, absolute paths) from the Korokae v2 audited CSV."""
    base = os.path.dirname(os.path.abspath(csv_path))
    rows = []
    with open(csv_path, newline="", encoding="utf-8") as f:
        for rec in csv.DictReader(f):
            if rec["role"] != "accompaniment":
                continue
            lic = normalise_licence(rec["licence"])
            if lic is None or lic == "own":  # same gate as _gate: own is never a background licence
                continue
            rows.append(Row(path=os.path.join(base, rec["path"]), label="background",
                            source=rec["source"], source_url=rec["source_url"], author=rec["author"],
                            licence=lic, attribution=rec["attribution"], speaker=f"korokae-v2:{rec['group']}",
                            split=split_for_id(f"korokae-v2:{rec['group']}"), consent="n/a"))
    return rows


def fetch_ccmixter(cfg: dict, root: str, http_get=_http_get, download=_download) -> list[Row]:
    uploads = json.loads(http_get(cfg["query_url"]))
    out_dir = os.path.realpath(os.path.join(root, "ccmixter"))
    seen_urls: set[str] = set()
    pending = []
    for up in uploads[: cfg.get("max_files", 200)]:
        lic = normalise_licence(up.get("license_url", ""))
        if lic is None or lic == "own" or not up.get("files"):
            continue
        url = up["files"][0].get("download_url", "")
        ext = os.path.splitext(urllib.parse.urlparse(url).path)[1].lower()
        if urllib.parse.urlparse(url).scheme not in ("http", "https") or ext not in AUDIO_EXTS_OK:
            continue
        if url in seen_urls:
            continue
        seen_urls.add(url)
        pending.append((up, url, ext, lic))
    splits = {up["user_name"]: split_for_id(f"ccmixter:{up['user_name']}") for up, *_ in pending}
    os.makedirs(out_dir, exist_ok=True)
    rows = []
    for up, url, ext, lic in pending:
        uid = str(up.get("upload_id", ""))
        name = (f"{uid}_" if uid.isalnum() else "") + hashlib.sha1(url.encode()).hexdigest()[:12] + ext
        dest = os.path.realpath(os.path.join(out_dir, name))
        if os.path.dirname(dest) != out_dir:
            raise ValueError(f"destination {dest!r} escapes {out_dir!r}")
        download(url, dest)
        rows.append(Row(path=f"ccmixter/{name}", label="background", source="ccmixter",
                        source_url=up.get("file_page_url") or url, author=up["user_name"], licence=lic,
                        attribution=f'"{up["upload_name"]}" by {up["user_name"]} (ccMixter), {lic}',
                        speaker=f"ccmixter:{up['user_name']}", split=splits[up["user_name"]],
                        consent="n/a"))
    return rows


def fetch_librispeech(cfg: dict, root: str, download=_download, only: list[str] | None = None) -> list[Row]:
    lic = _gate("librispeech", cfg["licence"])
    limit = cfg.get("max_files_per_subset", 0)
    rows = []
    for name, sub in cfg["subsets"].items():
        if only and name not in only:
            continue
        archive = os.path.join(root, f"{name}.tar.gz")
        download(sub["url"], archive, max_bytes=MAX_ARCHIVE_BYTES) if download is _download else download(sub["url"], archive)
        n = 0
        with tarfile.open(archive) as tf:
            for m in tf:
                parts = m.name.split("/")
                if not (m.isfile() and m.name.endswith(".flac")) or ".." in parts or len(parts) < 5:
                    continue
                spk = parts[-3]
                rel = f"librispeech/{name}/{spk}/{parts[-2]}/{parts[-1]}"
                dest = os.path.join(root, rel)
                os.makedirs(os.path.dirname(dest), exist_ok=True)
                with open(dest, "wb") as out:
                    out.write(tf.extractfile(m).read())
                rows.append(Row(path=rel, label="negative", source="librispeech",
                                source_url=LIBRISPEECH_PAGE, author=f"LibriVox reader {spk}",
                                licence=lic, attribution=LIBRISPEECH_ATTRIBUTION, speaker=f"librispeech:{spk}",
                                split=sub["split"], consent="n/a"))
                n += 1
                if limit and n >= limit:
                    break
        os.remove(archive)
    return rows


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--config", default="configs/base.yaml")
    ap.add_argument("--root", default="data/external")
    ap.add_argument("--dry-run", action="store_true", help="print the plan and licence checks only")
    args = ap.parse_args(argv)
    with open(args.config) as f:
        cfg = yaml.safe_load(f)["fetch"]
    base = os.path.dirname(os.path.abspath(args.config))
    try:
        items = plan(cfg, os.path.dirname(base))
    except LicenceError as e:
        print(f"LICENCE GATE FAILED: {e}")
        return 1
    for it in items:
        print(f"{it.source:12s} {it.name:20s} licence={it.licence:12s} {'OK' if it.allowed else 'BLOCKED'}  {it.url}")
    if args.dry_run:
        print("dry run: nothing downloaded, nothing written")
        return 0
    os.makedirs(args.root, exist_ok=True)
    rows = fetch_librispeech(cfg["librispeech"], args.root)
    korokae = [i for i in items if i.source == "korokae-v2"]
    rows += backgrounds_from_korokae(korokae[0].url) if korokae else fetch_ccmixter(cfg["ccmixter"], args.root)
    write_manifest(rows, os.path.join(args.root, "manifest.csv"))  # validates: fails loudly
    print(f"wrote {len(rows)} rows to {args.root}/manifest.csv")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
