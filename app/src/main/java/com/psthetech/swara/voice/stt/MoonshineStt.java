package com.psthetech.swara.voice.stt;

import android.content.res.AssetManager;

import com.psthetech.swara.voice.audio.LevelNormalizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Moonshine tiny (English, MIT) speech-to-text on ONNX Runtime: an encoder over raw 16 kHz audio
 * and an optimum "merged" decoder with a KV cache, decoded greedily. A direct port of
 * {@code ml/voice_assets/check_moonshine.py}; graph I/O is documented in
 * {@code ml/voice_assets/README.md}.
 */
public final class MoonshineStt implements SpeechToText {

    static final String ENCODER = "encoder_model_quantized.onnx";
    static final String DECODER = "decoder_model_merged_quantized.onnx";
    static final String TOKENIZER = "tokenizer.json";

    private static final int LAYERS = 6, HEADS = 8, HEAD_DIM = 36;
    private static final long BOS = 1, EOS = 2;
    private static final int SAMPLE_RATE = 16000;
    /** Below ~0.1 s the encoder output is empty; nothing to decode. */
    private static final int MIN_SAMPLES = SAMPLE_RATE / 10;
    private static final int THREADS = 2;

    private static final String[] PAST_DECODER = new String[LAYERS * 2];
    private static final String[] PAST_ENCODER = new String[LAYERS * 2];
    private static final String[] PRESENT_DECODER = new String[LAYERS * 2];
    private static final String[] PRESENT_ENCODER = new String[LAYERS * 2];

    static {
        String[] kv = {"key", "value"};
        for (int l = 0; l < LAYERS; l++) {
            for (int k = 0; k < 2; k++) {
                int i = l * 2 + k;
                PAST_DECODER[i] = "past_key_values." + l + ".decoder." + kv[k];
                PAST_ENCODER[i] = "past_key_values." + l + ".encoder." + kv[k];
                PRESENT_DECODER[i] = "present." + l + ".decoder." + kv[k];
                PRESENT_ENCODER[i] = "present." + l + ".encoder." + kv[k];
            }
        }
    }

    private final OrtEnvironment env;
    private final OrtSession encoder;
    private final OrtSession decoder;
    private final MoonshineTokenizer tokenizer;
    private boolean closed; // guarded by this

    /** Loads both graphs and the tokenizer from {@code dir} in {@code assets}. */
    public MoonshineStt(OrtEnvironment env, AssetManager assets, String dir)
            throws IOException, OrtException {
        this.env = env;
        try (InputStream in = assets.open(dir + "/" + TOKENIZER)) {
            this.tokenizer = MoonshineTokenizer.load(in);
        }
        try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
            opts.setIntraOpNumThreads(THREADS);
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            OrtSession enc = env.createSession(readAsset(assets, dir + "/" + ENCODER), opts);
            try {
                this.decoder = env.createSession(readAsset(assets, dir + "/" + DECODER), opts);
            } catch (OrtException | IOException | RuntimeException e) {
                enc.close();
                throw e;
            }
            this.encoder = enc;
        }
    }

    @Override
    public synchronized String transcribe(float[] audio16k) throws OrtException {
        if (closed) throw new IllegalStateException("MoonshineStt is closed");
        int n = audio16k.length;
        if (n < MIN_SAMPLES) return "";
        // Model-specific input conditioning: hot/clipped mics make the decoder emit EOS at once.
        audio16k = LevelNormalizer.normalize(audio16k);
        int maxTokens = (int) Math.ceil(6.0 * n / SAMPLE_RATE);

        List<Long> tokens = new ArrayList<>();
        try (OnnxTensor audio = OnnxTensor.createTensor(env, FloatBuffer.wrap(audio16k), new long[]{1, n});
             OrtSession.Result enc = encoder.run(Collections.singletonMap("input_values", audio));
             OnnxTensor empty = OnnxTensor.createTensor(env, FloatBuffer.allocate(0),
                     new long[]{1, HEADS, 0, HEAD_DIM});
             OnnxTensor noCache = OnnxTensor.createTensor(env, new boolean[]{false});
             OnnxTensor useCache = OnnxTensor.createTensor(env, new boolean[]{true})) {
            OnnxTensor hidden = output(enc, "last_hidden_state");

            OrtSession.Result first = null;  // step 0: holds the cross-attention KV for every step
            OrtSession.Result prev = null;   // last step: holds the self-attention KV
            long tok = BOS;
            try {
                for (int step = 0; step < maxTokens; step++) {
                    Map<String, OnnxTensor> feeds = new HashMap<>();
                    for (int i = 0; i < PAST_DECODER.length; i++) {
                        feeds.put(PAST_DECODER[i], prev == null ? empty : output(prev, PRESENT_DECODER[i]));
                        feeds.put(PAST_ENCODER[i], first == null ? empty : output(first, PRESENT_ENCODER[i]));
                    }
                    feeds.put("encoder_hidden_states", hidden);
                    feeds.put("use_cache_branch", step == 0 ? noCache : useCache);
                    OrtSession.Result res;
                    try (OnnxTensor ids = OnnxTensor.createTensor(env, LongBuffer.wrap(new long[]{tok}),
                            new long[]{1, 1})) {
                        feeds.put("input_ids", ids);
                        res = decoder.run(feeds);
                    }
                    if (prev != null && prev != first) prev.close();
                    prev = res;
                    if (first == null) first = res;

                    tok = argmaxLastStep(output(res, "logits"));
                    if (tok == EOS) break;
                    tokens.add(tok);
                }
            } finally {
                if (prev != null && prev != first) prev.close();
                if (first != null) first.close();
            }
        }
        int[] ids = new int[tokens.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = (int) (long) tokens.get(i);
        return tokenizer.decode(ids);
    }

    @Override
    public synchronized void close() throws OrtException {
        if (closed) return;
        closed = true;
        try {
            encoder.close();
        } finally {
            decoder.close();
        }
    }

    /** Greedy pick over the last position of logits [1, len, vocab]. */
    private static long argmaxLastStep(OnnxTensor logits) {
        long[] shape = logits.getInfo().getShape();
        int vocab = (int) shape[2];
        FloatBuffer fb = logits.getFloatBuffer();
        int base = fb.limit() - vocab;
        int best = 0;
        float bestVal = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < vocab; i++) {
            float v = fb.get(base + i);
            if (v > bestVal) {
                bestVal = v;
                best = i;
            }
        }
        return best;
    }

    private static OnnxTensor output(OrtSession.Result result, String name) {
        OnnxValue v = result.get(name)
                .orElseThrow(() -> new IllegalStateException("model has no '" + name + "' output"));
        return (OnnxTensor) v;
    }

    private static byte[] readAsset(AssetManager assets, String path) throws IOException {
        try (InputStream in = assets.open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(in.available(), 1 << 16));
            byte[] buf = new byte[1 << 16];
            for (int r; (r = in.read(buf)) > 0; ) out.write(buf, 0, r);
            return out.toByteArray();
        }
    }
}
