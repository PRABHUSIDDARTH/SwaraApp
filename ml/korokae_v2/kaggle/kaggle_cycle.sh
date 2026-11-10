#!/usr/bin/env bash
# Korokae v2 Kaggle runbook helper (spec 5). Run from anywhere on the laptop.
#
#   kaggle_cycle.sh init              create the private korokae-ckpt dataset (once)
#   kaggle_cycle.sh prep <commit>     push both data-prep notebooks pinned to <commit>
#   kaggle_cycle.sh train <commit>    after a session: save its checkpoints as a new korokae-ckpt
#                                     version, then push the next session pinned to <commit>
#   kaggle_cycle.sh status            show the notebooks' status
#
# <commit> must already be on GitHub (the notebooks git-fetch it). Any failing step aborts
# before the next one, so a failed download never pushes a session that would start from scratch.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../../.." && pwd)"
K="${KAGGLE:-$REPO/ml/korokae_v2/.venv/bin/kaggle}"
USER_ID="${KAGGLE_USER:-prabhusiddarthav}"
CKPT_DS="$USER_ID/korokae-ckpt"
TRAIN="$USER_ID/korokae-train"
W="$(mktemp -d)"
trap 'rm -rf "$W"' EXIT

die() { echo "error: $*" >&2; exit 1; }

full_commit() {
  local c
  c="$(git -C "$REPO" rev-parse --verify "$1^{commit}")" || die "unknown commit $1"
  git -C "$REPO" fetch -q origin
  [ -n "$(git -C "$REPO" branch -r --contains "$c")" ] || die "$c is not pushed to GitHub yet"
  echo "$c"
}

stamp_and_push() {  # <kernel dir name> <commit>
  cp -r "$HERE/$1" "$W/$1"
  sed -i -E "s/^COMMIT = \"[0-9a-f]{40}\"$/COMMIT = \"$2\"/" "$W/$1/run.py"
  grep -q "^COMMIT = \"$2\"$" "$W/$1/run.py" || die "could not stamp $1/run.py"
  "$K" kernels push -p "$W/$1"
}

ckpt_metadata() {  # <dir>
  printf '{"title": "korokae-ckpt", "id": "%s", "licenses": [{"name": "other"}]}\n' "$CKPT_DS" \
    > "$1/dataset-metadata.json"
  echo "Korokae v2 training checkpoints (private)." > "$1/korokae_ckpt.txt"
}

wait_dataset_ready() {
  for _ in $(seq 1 90); do
    s="$("$K" datasets status "$CKPT_DS" 2>&1 || true)"
    case "$s" in *ready*) return 0 ;; *error*|*failed*) die "dataset version failed: $s" ;; esac
    sleep 20
  done
  die "korokae-ckpt version not ready after 30 min"
}

cmd="${1:-}"
case "$cmd" in
  init)
    mkdir "$W/ds"; ckpt_metadata "$W/ds"
    "$K" datasets create -p "$W/ds"
    ;;
  prep)
    c="$(full_commit "${2:?commit}")"
    stamp_and_push prep_vocals "$c"
    stamp_and_push prep_accomp "$c"
    ;;
  train)
    c="$(full_commit "${2:?commit}")"
    status="$("$K" kernels status "$TRAIN" 2>&1 || true)"
    echo "previous session: $status"
    case "$status" in
      *running*|*queued*) die "the previous session is still running" ;;
      *error*|*cancel*) die "the previous session failed; read its log on Kaggle before continuing" ;;
      *complete*)
        "$K" kernels output "$TRAIN" -p "$W/out" > /dev/null
        ls "$W/out/ckpt/"step_*.pt > /dev/null 2>&1 || die "session output has no checkpoints"
        mkdir "$W/ds"; ckpt_metadata "$W/ds"
        cp "$W/out/ckpt/"step_*.pt "$W/out/ckpt/log.jsonl" "$W/ds/"
        [ -f "$W/out/ckpt/best.pt" ] && cp "$W/out/ckpt/best.pt" "$W/ds/"
        step="$(ls "$W/ds/"step_*.pt | sort | tail -1 | sed -E 's/.*step_0*([0-9]+)\.pt/\1/')"
        "$K" datasets version -p "$W/ds" -m "step $step (commit ${c:0:7})"
        wait_dataset_ready
        grep '"val_inst_sdr"' "$W/ds/log.jsonl" | tail -1 || echo "(no validation yet)"
        ;;
      *) echo "no previous session found: starting the first one" ;;
    esac
    stamp_and_push train "$c"
    ;;
  status)
    for k in korokae-prep-vocals korokae-prep-accomp korokae-train; do
      "$K" kernels status "$USER_ID/$k" 2>&1 || true
    done
    ;;
  *) sed -n '2,13p' "$0"; exit 2 ;;
esac
