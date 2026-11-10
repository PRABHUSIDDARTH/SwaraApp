package com.psthetech.swara.korokae.v2;

/**
 * Runs the Korokae v2 network on one window.
 *
 * <p>{@code spec} and the returned mask are {@code [4][BINS][frames]} flattened channel-major,
 * index {@code (c * BINS + bin) * frames + t}, channels {@code (L.re, L.im, R.re, R.im)}.
 */
public interface MaskModel extends AutoCloseable {
    float[] run(float[] spec, int frames) throws Exception;
}
