# Swara Voice — on-device speech-to-text assets

Offline speech-to-text for voice commands: **Moonshine tiny (English)**, run on the app's
ONNX Runtime (`com.microsoft.onnxruntime:onnxruntime-android:1.31.1`) by
`app/src/main/java/com/psthetech/swara/voice/stt/MoonshineStt.java`.

## Model source and licence

- ONNX export: Hugging Face [`onnx-community/moonshine-tiny-ONNX`](https://huggingface.co/onnx-community/moonshine-tiny-ONNX),
  revision `a6da1241cd305dcd64eab1edbd615f2bb9aabb95` (int8 dynamic-quantised graphs).
- Base model: [`UsefulSensors/moonshine-tiny`](https://huggingface.co/UsefulSensors/moonshine-tiny), **MIT**.
- Licence text shipped in `assets/voice/moonshine/LICENSE`, from
  <https://raw.githubusercontent.com/moonshine-ai/moonshine/d8d8fa29d05ca67441a7719e70099f9f48917a93/LICENSE>
  (the repo moved from `usefulsensors/moonshine` to `moonshine-ai/moonshine`). Its preamble states that the
  English Tiny and Base speech-to-text models are MIT (Section 1); the non-commercial Community License covers
  only the listed non-English legacy models, which we do not use. Commercial use in a free Play Store app is fine.

Only the ONNX graphs, tokenizer and configs are used; no Moonshine / transformers.js / sherpa code.

## Setup and fetch

```bash
uv venv <scratch>/voice-venv
uv pip install --python <scratch>/voice-venv/bin/python -r ml/voice_assets/requirements.txt
<scratch>/voice-venv/bin/python -I ml/voice_assets/fetch_moonshine.py   # model -> app/src/main/assets/voice/moonshine/
<scratch>/voice-venv/bin/python -I ml/voice_assets/fetch_clips.py       # test clips -> app/src/androidTest/assets/voice/clips/
<scratch>/voice-venv/bin/python -I ml/voice_assets/check_moonshine.py   # desktop transcript check
```

Every download is pinned by revision and SHA-256; a mismatch aborts. The venv lives outside the repo.

## Sizes (fetch output)

```
      7.94 MB  encoder_model_quantized.onnx
     20.24 MB  decoder_model_merged_quantized.onnx
      3.76 MB  tokenizer.json
      0.00 MB  generation_config.json
      0.00 MB  config.json
      0.01 MB  LICENSE
     31.96 MB  TOTAL (budget 43 MB)
```

`.onnx` is stored uncompressed in the APK (`noCompress 'onnx'`); `tokenizer.json` compresses well.

## Graph I/O

From `config.json`: hidden 288, 6 decoder layers, 8 heads (head dim 36), vocab 32768,
`decoder_start_token_id` = `bos` = 1, `eos` = 2.

**encoder_model_quantized.onnx** — takes raw 16 kHz mono audio, no separate feature graph.

| | name | dtype | shape |
|---|---|---|---|
| in | `input_values` | float32 | `[batch, num_samples]` |
| out | `last_hidden_state` | float32 | `[batch, floor(floor(floor(N/64 - 127/64)/3)/2) - 1, 288]` |

**decoder_model_merged_quantized.onnx** — optimum "merged" decoder (no-cache and with-cache branches in one graph).

| | name | dtype | shape |
|---|---|---|---|
| in | `input_ids` | int64 | `[batch, dec_len]` |
| in | `encoder_hidden_states` | float32 | `[batch, enc_len, 288]` |
| in | `past_key_values.{0..5}.decoder.{key,value}` | float32 | `[batch, 8, past_dec_len, 36]` |
| in | `past_key_values.{0..5}.encoder.{key,value}` | float32 | `[batch, 8, enc_len_out, 36]` |
| in | `use_cache_branch` | bool | `[1]` |
| out | `logits` | float32 | `[batch, dec_len, 32768]` |
| out | `present.{0..5}.decoder.{key,value}` | float32 | `[batch, 8, past_dec_len + 1, 36]` |
| out | `present.{0..5}.encoder.{key,value}` | float32 | `[batch, 8, enc_len_out, 36]` |

## Decode loop (check_moonshine.py, ported 1:1 to MoonshineStt.java)

1. `hidden = encoder(input_values=[1, N])`.
2. Step 0: `input_ids=[[1]]`, every `past_key_values.*` a zero-length `[1, 8, 0, 36]` tensor, `use_cache_branch=false`.
3. Greedy: `tok = argmax(logits[0, -1])`; stop at `eos` (2) or after `maxTokens = ceil(6 * N / 16000)` steps.
4. Next step: `input_ids=[[tok]]`, `use_cache_branch=true`, `past.*.decoder.*` = the previous step's `present.*.decoder.*`;
   `past.*.encoder.*` = step 0's `present.*.encoder.*` (cross-attention KV is fixed; this is what transformers.js does).
5. Decode the token ids with the tokenizer (below).

Tokenizer: `tokenizer.json` `model.vocab` (token -> id, 32000 entries) plus `added_tokens` (771, incl. `<unk>` 0,
`<s>` 1, `</s>` 2, `<<ST_n>>` 32000+). Decode: skip special added tokens, `<0xNN>` byte-fallback tokens become raw
bytes (UTF-8), `▁` becomes a space, then trim.

## Test clips (androidTest)

Five utterances from Hugging Face [`hf-internal-testing/librispeech_asr_dummy`](https://huggingface.co/datasets/hf-internal-testing/librispeech_asr_dummy)
(revision `5be91486e11a2d616f4ec5db8d3fd248585ac07a`, `clean/validation-00000-of-00001.parquet`), written as 16 kHz mono
16-bit WAV to `app/src/androidTest/assets/voice/clips/<id>.wav`.

**Credit:** LibriSpeech ASR corpus, Vassil Panayotov, Guoguo Chen, Daniel Povey and Sanjeev Khudanpur,
<https://www.openslr.org/12>, licensed [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Audio derived from
LibriVox public-domain recordings. Clips are unmodified apart from WAV re-encoding. Test-only; not shipped in the app.

| Clip id | Length | Reference transcript | Keywords |
|---|---|---|---|
| 1272-135031-0011 | 3.18 s | NOT EXACTLY RETURNED KALIKO | exactly, returned |
| 1272-135031-0008 | 3.67 s | I HOPE HE DOESN'T WORK TOO HARD SAID SHAGGY | hope, hard |
| 1272-135031-0004 | 4.22 s | THE KING HAS FLED IN DISGRACE AND YOUR FRIENDS ARE ASKING FOR YOU | king, friends |
| 1272-141231-0000 | 4.65 s | A MAN SAID TO THE UNIVERSE SIR I EXIST | universe, exist |
| 1272-128104-0008 | 5.12 s | AS FOR ETCHINGS THEY ARE OF TWO KINDS BRITISH AND FOREIGN | british, foreign |

## Desktop check (check_moonshine.py, onnxruntime 1.31 CPU, 2 threads)

```
1272-135031-0011   3.18s     75 ms  OK   'Not exactly, returned Calico.'
1272-135031-0008   3.67s    132 ms  OK   "I hope he doesn't work too hard, said Shaggy."
1272-135031-0004   4.22s    154 ms  OK   'The king is flooded disgrace in your friends, or asking for you.'
1272-141231-0000   4.65s    122 ms  OK   'A man said to the universe, Sir, I exist.'
1272-128104-0008   5.12s    168 ms  OK   'As for etchings, there are of two kinds British and foreign.'
```

## Device check (MoonshineSttInstrumentedTest, Pixel 3 XL / Snapdragon 845 / Android 12, ORT 1.31.1, 2 threads)

Run via `adb install -r` of the debug + androidTest APKs and `am instrument` (offline Gradle lacks the UTP plugin
that `connectedDebugAndroidTest` needs). `OK (3 tests)`.

```
load ms=3367
budget clip=1272-135031-0011 audio=3.18s ms=539 (target < 800, fail > 2000)
clip=1272-135031-0011 audio=3.18s ms=513 OK "Not exactly, returned Calico."
clip=1272-135031-0008 audio=3.67s ms=862 OK "I hope he doesn't work too hard, said Shaggy."
clip=1272-135031-0004 audio=4.22s ms=909 OK "The king is flooded disgrace in your friends, or asking for you."
clip=1272-141231-0000 audio=4.65s ms=816 OK "A man said to the universe, Sir, I exist."
clip=1272-128104-0008 audio=5.12s ms=1138 OK "As for etchings, there are of two kinds British and foreign."
```
