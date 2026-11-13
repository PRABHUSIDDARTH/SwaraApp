"""Fetch Moonshine tiny (English, MIT) ONNX graphs into the app's assets.

Source: Hugging Face onnx-community/moonshine-tiny-ONNX, pinned to one revision,
every file pinned by SHA-256. Base model: UsefulSensors/moonshine-tiny (MIT).

Run from the repo root:  python -I ml/voice_assets/fetch_moonshine.py

Downloads are staged in a fresh temporary directory, verified, then copied into
app/src/main/assets/voice/moonshine/. Afterwards the script prints the total size
and every graph's input/output names, shapes and dtypes (needs onnxruntime).
Exits non-zero if the total exceeds the 43 MB budget.
"""
import hashlib
import os
import shutil
import sys
import tempfile
import urllib.request

REVISION = "a6da1241cd305dcd64eab1edbd615f2bb9aabb95"
HF = "https://huggingface.co/onnx-community/moonshine-tiny-ONNX/resolve/" + REVISION + "/"
# Moonshine's own LICENSE (repo moved from usefulsensors/moonshine to moonshine-ai/moonshine),
# pinned to a commit. It states the English Tiny/Base speech-to-text models are MIT.
LICENSE_URL = ("https://raw.githubusercontent.com/moonshine-ai/moonshine/"
               "d8d8fa29d05ca67441a7719e70099f9f48917a93/LICENSE")

# (url, file name in assets, sha256)
FILES = [
    (HF + "onnx/encoder_model_quantized.onnx", "encoder_model_quantized.onnx",
     "c6fc4b7bc5af75c0591fd157a1f3829b533d18e9769a888fd95a62e470dd4f4a"),
    (HF + "onnx/decoder_model_merged_quantized.onnx", "decoder_model_merged_quantized.onnx",
     "eed87831c3a6103534aae7d47a5d485025c659a1323901513961c39fe8a1a367"),
    (HF + "tokenizer.json", "tokenizer.json",
     "7b913404bdd039af4756783218af4440bc07fb7d6d8258d677e34f95b3ec416f"),
    (HF + "generation_config.json", "generation_config.json",
     "f9b3f711b57be7def2e50a8942f64f36ee0a55fad5b84ff93a687b6c5bcc1d44"),
    (HF + "config.json", "config.json",
     "558e1e02069137c796ace1e50c48d8fe451f04a295929138e6bea885517f0edb"),
    (LICENSE_URL, "LICENSE",
     "fa7d1174dd8af6a7cd280be20b80d10095ed4c19b5b20b61a7715c3ad790dc5f"),
]

BUDGET_MB = 43.0
REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEST = os.path.join(REPO, "app", "src", "main", "assets", "voice", "moonshine")


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def fetch(stage):
    for url, name, want in FILES:
        dst = os.path.join(DEST, name)
        if os.path.exists(dst) and sha256(dst) == want:
            print("ok (cached)  " + name)
            continue
        tmp = os.path.join(stage, name)
        print("downloading  " + url)
        with urllib.request.urlopen(url) as r, open(tmp, "wb") as f:
            shutil.copyfileobj(r, f)
        got = sha256(tmp)
        if got != want:
            sys.exit("SHA-256 mismatch for %s: got %s want %s" % (name, got, want))
        shutil.copyfile(tmp, dst)
        print("ok           " + name)


def inspect():
    import onnxruntime as ort
    for name in sorted(os.listdir(DEST)):
        if not name.endswith(".onnx"):
            continue
        s = ort.InferenceSession(os.path.join(DEST, name), providers=["CPUExecutionProvider"])
        print("\n== %s" % name)
        for kind, items in (("in ", s.get_inputs()), ("out", s.get_outputs())):
            for i in items:
                print("  %s %-40s %-14s %s" % (kind, i.name, i.type, i.shape))


def main():
    os.makedirs(DEST, exist_ok=True)
    with tempfile.TemporaryDirectory() as stage:
        fetch(stage)
    total = 0
    for _, name, _ in FILES:
        size = os.path.getsize(os.path.join(DEST, name))
        total += size
        print("%10.2f MB  %s" % (size / 1e6, name))
    print("%10.2f MB  TOTAL (budget %.0f MB)" % (total / 1e6, BUDGET_MB))
    inspect()
    if total / 1e6 > BUDGET_MB:
        sys.exit("BLOCKED: assets exceed the %.0f MB budget" % BUDGET_MB)


if __name__ == "__main__":
    main()
