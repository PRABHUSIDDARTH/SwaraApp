"""Desktop check of the Moonshine tiny greedy-decode loop that MoonshineStt.java ports.

Runs encoder_model_quantized.onnx + decoder_model_merged_quantized.onnx (from the app
assets) on the LibriSpeech test clips and prints each transcript, timing and whether
the README's 2 expected keywords are present.

Run from the repo root:  python -I ml/voice_assets/check_moonshine.py
"""
import json
import math
import os
import sys
import time

import numpy as np
import onnxruntime as ort
import soundfile as sf

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MODEL = os.path.join(REPO, "app", "src", "main", "assets", "voice", "moonshine")
CLIPS = os.path.join(REPO, "app", "src", "androidTest", "assets", "voice", "clips")

# clip id -> 2 expected keywords (same table as README.md and MoonshineSttInstrumentedTest)
KEYWORDS = {
    "1272-135031-0011": ("exactly", "returned"),
    "1272-135031-0008": ("hope", "hard"),
    "1272-135031-0004": ("king", "friends"),
    "1272-141231-0000": ("universe", "exist"),
    "1272-128104-0008": ("british", "foreign"),
}

LAYERS, HEADS, HEAD_DIM = 6, 8, 36
BOS, EOS = 1, 2


def session(name):
    o = ort.SessionOptions()
    o.intra_op_num_threads = 2
    return ort.InferenceSession(os.path.join(MODEL, name), o, providers=["CPUExecutionProvider"])


class Tokenizer:
    """Same rules as MoonshineTokenizer.java: vocab from tokenizer.json, '▁' -> ' ',
    <0xNN> byte fallback decoded as UTF-8, special added_tokens skipped, trimmed."""

    def __init__(self, path):
        t = json.load(open(path, encoding="utf-8"))
        self.id_to_tok = {i: s for s, i in t["model"]["vocab"].items()}
        self.special = set()
        for a in t["added_tokens"]:
            self.id_to_tok[a["id"]] = a["content"]
            if a["special"]:
                self.special.add(a["id"])

    def decode(self, ids):
        out = bytearray()
        for i in ids:
            if i in self.special or i not in self.id_to_tok:
                continue
            tok = self.id_to_tok[i]
            if len(tok) == 6 and tok.startswith("<0x") and tok.endswith(">"):
                out.append(int(tok[3:5], 16))
            else:
                out += tok.replace("▁", " ").encode("utf-8")
        return out.decode("utf-8", errors="replace").strip()


def transcribe(enc, dec, audio):
    n = audio.shape[0]
    max_tokens = math.ceil(6 * n / 16000)
    hidden = enc.run(["last_hidden_state"], {"input_values": audio[None, :].astype(np.float32)})[0]
    out_names = [o.name for o in dec.get_outputs()]
    empty = np.zeros((1, HEADS, 0, HEAD_DIM), dtype=np.float32)
    past = {}
    for l in range(LAYERS):
        for part in ("decoder", "encoder"):
            for kv in ("key", "value"):
                past["past_key_values.%d.%s.%s" % (l, part, kv)] = empty
    tokens = []
    tok = BOS
    for step in range(max_tokens):
        feeds = dict(past)
        feeds["input_ids"] = np.array([[tok]], dtype=np.int64)
        feeds["encoder_hidden_states"] = hidden
        feeds["use_cache_branch"] = np.array([step > 0], dtype=bool)
        res = dict(zip(out_names, dec.run(out_names, feeds)))
        tok = int(np.argmax(res["logits"][0, -1]))
        if tok == EOS:
            break
        tokens.append(tok)
        for l in range(LAYERS):
            for kv in ("key", "value"):
                past["past_key_values.%d.decoder.%s" % (l, kv)] = res["present.%d.decoder.%s" % (l, kv)]
                if step == 0:  # cross-attention KV is computed once, then reused
                    past["past_key_values.%d.encoder.%s" % (l, kv)] = res["present.%d.encoder.%s" % (l, kv)]
    return tokens


def main():
    enc = session("encoder_model_quantized.onnx")
    dec = session("decoder_model_merged_quantized.onnx")
    tk = Tokenizer(os.path.join(MODEL, "tokenizer.json"))
    ok = True
    for cid, (k1, k2) in KEYWORDS.items():
        audio, sr = sf.read(os.path.join(CLIPS, cid + ".wav"), dtype="float32")
        assert sr == 16000
        t0 = time.perf_counter()
        text = tk.decode(transcribe(enc, dec, audio))
        ms = (time.perf_counter() - t0) * 1000
        hit = k1 in text.lower() and k2 in text.lower()
        ok &= hit
        print("%-18s %4.2fs %6.0f ms  %-4s %r" % (cid, len(audio) / sr, ms, "OK" if hit else "MISS", text))
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
