#!/usr/bin/env bash
# Korokae v2 Kaggle runbook helper (spec 5). Run from anywhere on the laptop.
#
#   kaggle_cycle.sh init [ft]                  create the private korokae-ckpt (or -ft) dataset, once
#   kaggle_cycle.sh prep <commit>              push both data-prep notebooks pinned to <commit>
#   kaggle_cycle.sh train <commit> [opts]      save the finished session's checkpoints as a new
#   kaggle_cycle.sh finetune <commit> [opts]   dataset version, then push the next session
#   kaggle_cycle.sh status                     show the notebooks' status
#
#   opts: --fresh        first session only (no previous session, empty checkpoint dataset)
#         --minutes N    session budget in minutes (default 690; e.g. 30 for the pilot)
#
# <commit> must already be on GitHub (the notebooks git-fetch it). Every step must succeed before
# the next runs; anything but a COMPLETE previous session stops before pushing.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../../.." && pwd)"
K="${KAGGLE:-$REPO/ml/korokae_v2/.venv/bin/kaggle}"
USER_ID="${KAGGLE_USER:-prabhusiddarthav}"
W="$(mktemp -d)"
trap 'rm -rf "$W"' EXIT

die() { echo "error: $*" >&2; exit 1; }

full_commit() {
  local c
  c="$(git -C "$REPO" rev-parse --verify "$1^{commit}")" || die "unknown commit $1"
  if [ -z "${KOROKAE_SKIP_PUSH_CHECK:-}" ]; then
    git -C "$REPO" fetch -q origin
    [ -n "$(git -C "$REPO" branch -r --contains "$c")" ] || die "$c is not pushed to GitHub yet"
  fi
  echo "$c"
}

stamp() {  # <file> <NAME> <python literal>
  sed -i -E "s|^$2 = .*$|$2 = $3|" "$1"
  grep -qxF "$2 = $3" "$1" || die "could not stamp $2 in $1"
}

push_kernel() {  # <kernel dir> <commit> [<minutes> <fresh True|False>]
  cp -r "$HERE/$1" "$W/push_$1"
  stamp "$W/push_$1/run.py" COMMIT "\"$2\""
  if [ $# -ge 4 ]; then
    stamp "$W/push_$1/run.py" MAX_MINUTES "$3"
    stamp "$W/push_$1/run.py" FRESH "$4"
  fi
  "$K" kernels push -p "$W/push_$1"
}

ckpt_dir() {  # <dir> <dataset slug> <marker>
  mkdir -p "$1"
  printf '{"title": "%s", "id": "%s/%s", "licenses": [{"name": "other"}]}\n' "$2" "$USER_ID" "$2" \
    > "$1/dataset-metadata.json"
  echo "Korokae v2 training checkpoints (private)." > "$1/$3"
}

wait_dataset_ready() {  # <dataset slug>
  local s
  for _ in $(seq 1 90); do
    s="$("$K" datasets status "$USER_ID/$1" 2>&1 || true)"
    case "${s,,}" in *ready*) return 0 ;; *error*|*fail*) die "dataset version failed: $s" ;; esac
    sleep 20
  done
  die "$1 version not ready after 30 min"
}

session() {  # <train|finetune> <commit> [--fresh] [--minutes N]
  local kind="$1" c fresh=False minutes=690.0 kernel dataset marker status step
  c="$(full_commit "$2")"; shift 2
  while [ $# -gt 0 ]; do
    case "$1" in
      --fresh) fresh=True ;;
      --minutes) minutes="$(printf '%.1f' "${2:?minutes}")"; shift ;;
      *) die "unknown option $1" ;;
    esac
    shift
  done
  if [ "$kind" = train ]; then
    kernel=korokae-train; dataset=korokae-ckpt; marker=korokae_ckpt.txt
  else
    kernel=korokae-finetune; dataset=korokae-ckpt-ft; marker=korokae_ckpt_ft.txt
  fi
  status="$("$K" kernels status "$USER_ID/$kernel" 2>&1 || true)"
  echo "previous $kernel session: $status"
  case "${status,,}" in
    *kernelworkerstatus.complete*)
      [ "$fresh" = False ] || die "--fresh, but a previous session exists"
      "$K" kernels output "$USER_ID/$kernel" -p "$W/out" > /dev/null
      ls "$W/out/ckpt/"step_*.pt > /dev/null 2>&1 || die "session output has no checkpoints"
      ckpt_dir "$W/ds" "$dataset" "$marker"
      cp "$W/out/ckpt/"step_*.pt "$W/out/ckpt/log.jsonl" "$W/ds/"
      if [ -f "$W/out/ckpt/best.pt" ]; then cp "$W/out/ckpt/best.pt" "$W/ds/"; fi
      step="$(ls "$W/ds/"step_*.pt | sort | tail -1 | sed -E 's/.*step_0*([0-9]+)\.pt/\1/')"
      "$K" datasets version -p "$W/ds" -m "step $step (commit ${c:0:7})"
      wait_dataset_ready "$dataset"
      grep '"val_inst_sdr"' "$W/ds/log.jsonl" | tail -1 || echo "(no validation yet)"
      ;;
    # CLI 2.2.5 reports a never-created notebook as "Permission 'kernels.get' was denied"
    *404*|*"not found"*|*"permission 'kernels.get' was denied"*)
      [ "$fresh" = True ] || die "no previous $kernel session: start the first one with --fresh"
      echo "starting the first $kernel session"
      ;;
    *running*|*queued*) die "the previous session is still running" ;;
    *error*|*cancel*) die "the previous session failed; read its log on Kaggle before continuing" ;;
    *) die "unrecognised status: $status" ;;
  esac
  push_kernel "${kernel#korokae-}" "$c" "$minutes" "$fresh"
}

cmd="${1:-}"
case "$cmd" in
  init)
    if [ "${2:-}" = ft ]; then ckpt_dir "$W/ds" korokae-ckpt-ft korokae_ckpt_ft.txt
    else ckpt_dir "$W/ds" korokae-ckpt korokae_ckpt.txt; fi
    "$K" datasets create -p "$W/ds"
    ;;
  prep)
    c="$(full_commit "${2:?commit}")"
    push_kernel prep_vocals "$c"
    push_kernel prep_accomp "$c"
    ;;
  train|finetune) session "$cmd" "${2:?commit}" "${@:3}" ;;
  status)
    for k in korokae-prep-vocals korokae-prep-accomp korokae-train korokae-finetune; do
      "$K" kernels status "$USER_ID/$k" 2>&1 || true
    done
    ;;
  *) sed -n '2,15p' "$0"; exit 2 ;;
esac
