"""Licence-checked fetchers for the Plan 2 training sources (spec 3.1).

Each fetch_<source>(work, out, http, budget_s) downloads from the official host, re-checks the
licence there, converts audio to 44.1 kHz FLAC under out/audio/<source>/ and yields manifest
Rows with paths relative to `out`. `split` is filled in later by build.py (Slakh keeps its own).
"""
from __future__ import annotations

import csv
import http.client
import io
import json
import os
import shutil
import tarfile
import time
import urllib.request
import zipfile
from collections import Counter
from typing import Any, BinaryIO, Iterator, Protocol

from .manifest import Row, normalise_licence
from .prep import to_flac

ZENODO_API = "https://zenodo.org/api/records/"
CCMIXTER_API = "https://ccmixter.org/api/query?f=json&tags=acappella"
CCMIXTER_PAGE = 4  # larger pages hit records whose malformed responses break HTTP parsing
AI_TAGS = (",AI_generated,", ",AI_assisted,", ",ia_stems_generated,")
USER_AGENT = "SwaraKorokaeDataPrep/1.0"
SLAKH_SPLITS = {"train": "train", "validation": "val", "test": "test"}
SLAKH_HELDOUT_BUDGET_S = 5 * 3600.0


class LicenceError(RuntimeError):
    pass


class Http(Protocol):
    def get_json(self, url: str) -> Any: ...

    def download(self, url: str, dst: str) -> None: ...

    def stream(self, url: str) -> BinaryIO: ...


class UrllibHttp:
    """Real network access (used on Kaggle and for the laptop --dry-run probe)."""

    def _open(self, url: str, timeout: float = 120.0):
        return urllib.request.urlopen(
            urllib.request.Request(url, headers={"User-Agent": USER_AGENT}), timeout=timeout)

    def get_json(self, url: str) -> Any:
        with self._open(url) as r:
            body = r.read()
        if not body.strip():
            return []
        return json.loads(body)

    def download(self, url: str, dst: str) -> None:
        part = dst + ".part"
        with self._open(url, timeout=600.0) as r, open(part, "wb") as f:
            shutil.copyfileobj(r, f, 1 << 20)
        os.replace(part, dst)

    def stream(self, url: str) -> BinaryIO:
        return self._open(url, timeout=600.0)


# ---------------------------------------------------------------- Zenodo sources

def zenodo_record(http: Http, record_id: int) -> tuple[str, dict[str, str]]:
    """(normalised licence, {file key: download url}); raises LicenceError if not allowed."""
    rec = http.get_json(f"{ZENODO_API}{record_id}")
    raw = ((rec.get("metadata") or {}).get("license") or {}).get("id", "")
    licence = normalise_licence(raw)
    if licence is None:
        raise LicenceError(f"zenodo record {record_id}: licence {raw!r} is not allowed")
    return licence, {f["key"]: f["links"]["self"] for f in rec.get("files", [])}


def _wav_members(z: zipfile.ZipFile) -> list[str]:
    return sorted(n for n in z.namelist()
                  if n.lower().endswith(".wav") and not n.startswith("__MACOSX/")
                  and not os.path.basename(n).startswith("._"))


def _convert_member(z: zipfile.ZipFile, name: str, work: str, dst: str) -> float:
    tmp = os.path.join(work, "member" + os.path.splitext(name)[1])
    with z.open(name) as src, open(tmp, "wb") as f:
        shutil.copyfileobj(src, f, 1 << 20)
    try:
        return to_flac(tmp, dst)
    finally:
        os.remove(tmp)


def _download_zip(http: Http, url: str, work: str, name: str) -> str:
    os.makedirs(work, exist_ok=True)
    path = os.path.join(work, name)
    if not os.path.exists(path):
        http.download(url, path)
    return path


def fetch_vocalset(work: str, out: str, http: Http, budget_s: float) -> Iterator[Row]:
    record = 1203819
    licence, files = zenodo_record(http, record)
    zpath = _download_zip(http, files["VocalSet11.zip"], work, "VocalSet11.zip")
    total = 0.0
    with zipfile.ZipFile(zpath) as z:
        for name in _wav_members(z):
            parts = name.split("/")
            if len(parts) < 3 or parts[0] != "FULL" or total >= budget_s:
                continue
            rel = os.path.join("audio", "vocalset", *parts[1:])[:-4] + ".flac"
            total += _convert_member(z, name, work, os.path.join(out, rel))
            yield Row(path=rel, role="vocal", source="vocalset",
                      source_url=f"https://zenodo.org/records/{record}",
                      author="Julia Wilkins et al.", licence=licence,
                      attribution="VocalSet (Wilkins, Seetharaman, Wahl, Pardo, ISMIR 2018)",
                      group=parts[1], split="")


def fetch_vocadito(work: str, out: str, http: Http, budget_s: float) -> Iterator[Row]:
    record = 5578807
    licence, files = zenodo_record(http, record)
    zpath = _download_zip(http, files["vocadito.zip"], work, "vocadito.zip")
    total = 0.0
    with zipfile.ZipFile(zpath) as z:
        meta = z.read("vocadito_metadata.csv").decode("utf-8")
        singer = {r["track_id"]: r["singer_id"] for r in csv.DictReader(io.StringIO(meta))}
        for name in _wav_members(z):
            track = os.path.basename(name)[:-4].rsplit("_", 1)[-1]
            if track not in singer or total >= budget_s:
                continue
            rel = os.path.join("audio", "vocadito", os.path.basename(name)[:-4] + ".flac")
            total += _convert_member(z, name, work, os.path.join(out, rel))
            yield Row(path=rel, role="vocal", source="vocadito",
                      source_url=f"https://zenodo.org/records/{record}",
                      author="Rachel M. Bittner et al.", licence=licence,
                      attribution="vocadito (Bittner, Pasalo, Bosch, Meseguer-Brocal, Rubinstein, 2021)",
                      group=singer[track], split="")


def fetch_slakh(work: str, out: str, http: Http, budget_s: float) -> Iterator[Row]:
    """Streams the 104 GB archive; keeps only mix.flac, within budget_s for train and
    min(budget_s, 5 h) each for val and test. Never stores the archive."""
    record = 4599666
    licence, files = zenodo_record(http, record)
    budgets = {"train": budget_s, "val": min(budget_s, SLAKH_HELDOUT_BUDGET_S),
               "test": min(budget_s, SLAKH_HELDOUT_BUDGET_S)}
    used = Counter()
    os.makedirs(work, exist_ok=True)
    with tarfile.open(fileobj=http.stream(files["slakh2100_flac_redux.tar.gz"]), mode="r|gz") as t:
        for m in t:
            parts = m.name.split("/")
            if len(parts) != 4 or parts[3] != "mix.flac" or parts[1] not in SLAKH_SPLITS:
                continue
            split, track = SLAKH_SPLITS[parts[1]], parts[2]
            if used[split] >= budgets[split]:
                if all(used[s] >= budgets[s] for s in budgets):
                    break
                continue
            tmp = os.path.join(work, "slakh_mix.flac")
            with t.extractfile(m) as src, open(tmp, "wb") as f:
                shutil.copyfileobj(src, f, 1 << 20)
            rel = os.path.join("audio", "slakh", split, track, "mix.flac")
            try:
                used[split] += to_flac(tmp, os.path.join(out, rel))
            finally:
                os.remove(tmp)
            yield Row(path=rel, role="accompaniment", source="slakh2100",
                      source_url=f"https://zenodo.org/records/{record}",
                      author="Ethan Manilow et al.", licence=licence,
                      attribution="Slakh2100 (Manilow, Wichern, Seetharaman, Le Roux, WASPAA 2019)",
                      group=track, split=split)


# ---------------------------------------------------------------- ccMixter

def ccmixter_page_url(offset: int, limit: int = CCMIXTER_PAGE) -> str:
    return f"{CCMIXTER_API}&limit={limit}&offset={offset}"


def ccmixter_reason(rec: dict) -> str:
    """'accepted', 'licence', 'ai_tag' (AI tags win: never ours to learn from) or 'fetch_error'."""
    if "_error" in rec:
        return "fetch_error"
    tags = "," + (rec.get("upload_tags") or "").strip(",") + ","
    if any(t.lower() in tags.lower() for t in AI_TAGS):
        return "ai_tag"
    if normalise_licence(rec.get("license_url", "")) is None:
        return "licence"
    return "accepted"


def ccmixter_reasons(records: list[dict]) -> dict[str, int]:
    return dict(Counter(ccmixter_reason(r) for r in records))


def _get_with_retries(http: Http, url: str, pause: float, retries: int) -> Any:
    for attempt in range(retries + 1):
        try:
            return http.get_json(url)
        except (OSError, http_client_errors, ValueError):
            if attempt == retries:
                raise
            time.sleep(pause * 2 ** attempt)


http_client_errors = http.client.HTTPException


def ccmixter_records(http: Http, pause: float = 1.0, retries: int = 3) -> Iterator[dict]:
    """All a cappella records, paged politely; ends after 3 consecutive empty pages.

    A page that keeps failing is re-fetched one record at a time; a record that still fails is
    yielded as {"_error": offset} so the dry run can count it.
    """
    offset, empties = 0, 0
    while empties < 3:
        try:
            page = _get_with_retries(http, ccmixter_page_url(offset), pause, retries)
        except (OSError, http_client_errors, ValueError):
            page = []
            for i in range(offset, offset + CCMIXTER_PAGE):
                try:
                    page += _get_with_retries(http, ccmixter_page_url(i, limit=1), pause, 1) or []
                except (OSError, http_client_errors, ValueError):
                    page.append({"_error": i})
        empties = empties + 1 if not page else 0
        yield from page or []
        offset += CCMIXTER_PAGE
        time.sleep(pause)


def _ccmixter_file(rec: dict) -> dict | None:
    files = rec.get("files") or []
    for ext in ("flac", "wav", "mp3"):
        for f in files:
            if (f.get("file_format_info") or {}).get("default-ext") == ext:
                return f
    return None


def fetch_ccmixter(work: str, out: str, http: Http, budget_s: float,
                   pause: float = 1.0) -> Iterator[Row]:
    os.makedirs(work, exist_ok=True)
    total = 0.0
    for rec in ccmixter_records(http, pause):
        if total >= budget_s:
            return
        f = _ccmixter_file(rec)
        if ccmixter_reason(rec) != "accepted" or f is None:
            continue
        ext = f["file_format_info"]["default-ext"]
        tmp = os.path.join(work, f"ccmixter.{ext}")
        http.download(f["download_url"], tmp)
        rel = os.path.join("audio", "ccmixter", rec["user_name"], f"{rec['upload_id']}.flac")
        try:
            total += to_flac(tmp, os.path.join(out, rel))
        finally:
            os.remove(tmp)
        yield Row(path=rel, role="vocal", source="ccmixter", source_url=rec["license_url"],
                  author=rec["user_name"], licence=normalise_licence(rec["license_url"]),
                  attribution=f"\"{rec['upload_name']}\" by {rec['user_name']} "
                              f"({rec.get('file_page_url', 'ccMixter')})",
                  group=rec["user_name"], split="")


FETCHERS = {"vocalset": fetch_vocalset, "vocadito": fetch_vocadito,
            "ccmixter": fetch_ccmixter, "slakh": fetch_slakh}
