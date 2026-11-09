package com.psthetech.swara.korokae.v2;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.Collections;

/** {@link MaskModel} backed by ONNX Runtime (CPU execution provider). */
public final class OnnxMaskModel implements MaskModel {

    private static final String INPUT = "spec";
    private static final String OUTPUT = "mask";

    private final OrtEnvironment env;
    private final OrtSession session;

    private OnnxMaskModel(OrtEnvironment env, OrtSession session) {
        this.env = env;
        this.session = session;
    }

    /** Opens {@code modelFile} with {@code threads} intra-op threads and full graph optimisation. */
    public static OnnxMaskModel open(File modelFile, int threads) throws OrtException {
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
            opts.setIntraOpNumThreads(threads);
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            return new OnnxMaskModel(env, env.createSession(modelFile.getAbsolutePath(), opts));
        }
    }

    @Override
    public float[] run(float[] spec, int frames) throws OrtException {
        long[] shape = {1, 4, Stft.BINS, frames};
        try (OnnxTensor in = OnnxTensor.createTensor(env, FloatBuffer.wrap(spec), shape);
             OrtSession.Result result = session.run(Collections.singletonMap(INPUT, in))) {
            OnnxTensor out = (OnnxTensor) result.get(OUTPUT)
                    .orElseThrow(() -> new IllegalStateException("model has no 'mask' output"));
            FloatBuffer fb = out.getFloatBuffer();
            float[] mask = new float[fb.remaining()];
            fb.get(mask);
            return mask;
        }
    }

    @Override
    public void close() throws OrtException {
        session.close();
    }
}
