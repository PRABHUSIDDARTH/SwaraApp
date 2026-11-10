"""kaggle_cycle.sh against a fake `kaggle` that prints the real CLI's status format."""
import os
import re
import subprocess

import pytest

SCRIPT = os.path.join(os.path.dirname(__file__), "..", "kaggle", "kaggle_cycle.sh")
COMMIT = subprocess.run(["git", "rev-parse", "HEAD"], capture_output=True, text=True,
                        cwd=os.path.dirname(__file__)).stdout.strip()

FAKE = r'''#!/usr/bin/env bash
echo "$*" >> "$FAKE_LOG"
case "$1 $2" in
  "kernels status")
    if [ "$FAKE_STATUS" = "missing" ]; then echo "404 Client Error: Not Found for url"; exit 1; fi
    if [ "$FAKE_STATUS" = "denied" ]; then
      echo "Cannot access kernel '$3' (Permission 'kernels.get' was denied). The most likely cause is a wrong kernel slug."; exit 1; fi
    echo "$3 has status \"KernelWorkerStatus.$FAKE_STATUS\"" ;;
  "kernels output")
    d="${@: -1}"; mkdir -p "$d/ckpt"
    echo x > "$d/ckpt/step_00000500.pt"; echo x > "$d/ckpt/best.pt"
    echo '{"step": 500, "val_inst_sdr": 7.5}' > "$d/ckpt/log.jsonl" ;;
  "kernels push") cp "$4/run.py" "$FAKE_PUSHED" ;;
  "datasets status") echo "ready" ;;
esac
'''


@pytest.fixture()
def fake(tmp_path):
    k = tmp_path / "kaggle"
    k.write_text(FAKE)
    k.chmod(0o755)
    env = dict(os.environ, KAGGLE=str(k), FAKE_LOG=str(tmp_path / "calls"),
               FAKE_PUSHED=str(tmp_path / "pushed.py"), KOROKAE_SKIP_PUSH_CHECK="1")

    def run(*args, status="COMPLETE"):
        env["FAKE_STATUS"] = status
        p = subprocess.run(["bash", SCRIPT, *args], capture_output=True, text=True, env=env)
        calls = (tmp_path / "calls").read_text() if (tmp_path / "calls").exists() else ""
        pushed = (tmp_path / "pushed.py").read_text() if (tmp_path / "pushed.py").exists() else None
        return p, calls, pushed
    return run


def _const(src, name):
    return re.search(rf"^{name} = (.+)$", src, re.M).group(1)


def test_complete_session_is_versioned_then_next_pushed(fake):
    p, calls, pushed = fake("train", COMMIT)
    assert p.returncode == 0, p.stderr
    assert "kernels output" in calls and "datasets version" in calls and "kernels push" in calls
    assert calls.index("datasets version") < calls.index("kernels push")
    assert _const(pushed, "COMMIT") == f'"{COMMIT}"' and _const(pushed, "FRESH") == "False"


@pytest.mark.parametrize("status", ["RUNNING", "QUEUED", "ERROR", "CANCEL_ACKNOWLEDGED", "SOMETHING_NEW"])
def test_not_complete_never_pushes(fake, status):
    p, calls, pushed = fake("train", COMMIT, status=status)
    assert p.returncode != 0 and pushed is None and "datasets version" not in calls


def test_first_session_needs_explicit_fresh(fake):
    p, _, pushed = fake("train", COMMIT, status="missing")
    assert p.returncode != 0 and pushed is None
    p, calls, pushed = fake("train", COMMIT, "--fresh", status="missing")
    assert p.returncode == 0, p.stderr
    assert _const(pushed, "FRESH") == "True" and "datasets version" not in calls


def test_never_created_kernel_reported_as_denied_counts_as_missing(fake):
    # Kaggle CLI 2.2.5 answers a status query for a notebook that does not exist yet like this
    p, _, pushed = fake("train", COMMIT, status="denied")
    assert p.returncode != 0 and pushed is None
    p, calls, pushed = fake("train", COMMIT, "--fresh", status="denied")
    assert p.returncode == 0, p.stderr
    assert _const(pushed, "FRESH") == "True" and "datasets version" not in calls


def test_minutes_override_is_stamped(fake):
    p, _, pushed = fake("train", COMMIT, "--minutes", "30")
    assert p.returncode == 0, p.stderr and _const(pushed, "MAX_MINUTES") == "30.0"
    assert _const(pushed, "MAX_MINUTES") == "30.0"


def test_finetune_uses_its_own_kernel_and_dataset(fake):
    p, calls, pushed = fake("finetune", COMMIT)
    assert p.returncode == 0, p.stderr
    assert "korokae-finetune" in calls and "korokae-ckpt-ft" in calls
    assert _const(pushed, "CKPT_MARKER") == '"korokae_ckpt_ft.txt"'
