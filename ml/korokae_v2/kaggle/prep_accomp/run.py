"""Kaggle notebook: prepare the accompaniment dataset (Slakh2100 mixes) — CPU, internet. Pushed by ml/korokae_v2/kaggle/kaggle_cycle.sh, which stamps COMMIT."""
import os
import subprocess
import sys
import time

START = time.time()  # the session deadline counts from here

COMMIT = "11797f17c60c1b29e587da450dc958a992b921cc"
REPO = "https://github.com/PRABHUSIDDARTH/SwaraApp"
SRC = "/kaggle/temp/swara"


def sh(*cmd, cwd=None):
    print("+", " ".join(cmd), flush=True)
    subprocess.run(cmd, check=True, cwd=cwd)


os.makedirs(SRC, exist_ok=True)
sh("git", "init", "-q", cwd=SRC)
sh("git", "fetch", "-q", "--depth", "1", REPO, COMMIT, cwd=SRC)
sh("git", "checkout", "-q", "FETCH_HEAD", cwd=SRC)
sh(sys.executable, "-m", "pip", "install", "-q", "soundfile>=0.13", "pyyaml>=6")
sys.path.insert(0, os.path.join(SRC, "ml", "korokae_v2"))
os.environ["KOROKAE_COMMIT"] = COMMIT
import torch  # noqa: E402

print("torch", torch.__version__, "cuda", torch.cuda.is_available(), flush=True)
from korokae_v2 import kaggle_entry  # noqa: E402

# 40 h train (+5 h val, +5 h test) stays under the 20 GB notebook output even if mixes are stereo
kaggle_entry.prep(["slakh"], budget_hours=40.0)
