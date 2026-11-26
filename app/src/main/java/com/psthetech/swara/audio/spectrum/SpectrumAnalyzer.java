package com.psthetech.swara.audio.spectrum;

import androidx.annotation.NonNull;

/**
 * Turns a window of mono audio into log-spaced band levels in 0..1. Pure maths; all working
 * arrays are preallocated, so {@link #analyze} does not allocate.
 */
public final class SpectrumAnalyzer {

    public static final int SIZE = PcmRing.MAX_WINDOW_FRAMES;
    public static final int BANDS = 16;

    private static final float LOW_HZ = 40f;
    private static final float HIGH_HZ = 16_000f;
    /** Levels at or below the floor read 0; at or above the ceiling they read 1. */
    private static final float FLOOR_DB = -62f;
    private static final float CEILING_DB = -8f;
    /** Music has less energy higher up; this tilt, about 1 kHz, keeps the high bars visible. */
    private static final float TILT_DB_PER_OCTAVE = 3f;

    private final float[] hann = new float[SIZE];
    private final float[] cos = new float[SIZE / 2];
    private final float[] sin = new float[SIZE / 2];
    private final int[] reversed = new int[SIZE];
    private final float[] re = new float[SIZE];
    private final float[] im = new float[SIZE];

    // Recomputed only when the sample rate changes.
    private final int[] firstBin = new int[BANDS];
    private final int[] lastBin = new int[BANDS];
    private final float[] tiltDb = new float[BANDS];
    private int binsForRate;

    public SpectrumAnalyzer() {
        for (int i = 0; i < SIZE; i++) {
            hann[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / SIZE));
        }
        for (int i = 0; i < SIZE / 2; i++) {
            cos[i] = (float) Math.cos(2 * Math.PI * i / SIZE);
            sin[i] = (float) -Math.sin(2 * Math.PI * i / SIZE);
        }
        int bits = Integer.numberOfTrailingZeros(SIZE);
        for (int i = 0; i < SIZE; i++) reversed[i] = Integer.reverse(i) >>> (32 - bits);
    }

    private static float edgeHz(int edge) {
        return (float) (LOW_HZ * Math.pow(HIGH_HZ / LOW_HZ, edge / (double) BANDS));
    }

    /** The band a frequency belongs to, clamped to the first and last band. */
    public static int bandOf(float hz) {
        int band = (int) Math.floor(BANDS * Math.log(hz / LOW_HZ) / Math.log(HIGH_HZ / LOW_HZ));
        return Math.max(0, Math.min(BANDS - 1, band));
    }

    private void prepareBins(int rate) {
        if (rate == binsForRate) return;
        binsForRate = rate;
        float binHz = rate / (float) SIZE;
        for (int band = 0; band < BANDS; band++) {
            float low = edgeHz(band);
            float high = edgeHz(band + 1);
            int first = (int) Math.ceil(low / binHz);
            int last = (int) Math.ceil(high / binHz) - 1;
            if (last < first) {
                // A low band narrower than one bin: use the bin nearest its centre.
                first = last = Math.round((float) Math.sqrt(low * high) / binHz);
            }
            boolean aboveNyquist = low >= rate / 2f;
            firstBin[band] = aboveNyquist ? 1 : Math.max(1, first);
            lastBin[band] = aboveNyquist ? 0 : Math.min(SIZE / 2 - 1, last);
            tiltDb[band] = (float) (TILT_DB_PER_OCTAVE
                    * Math.log(Math.sqrt(low * high) / 1000f) / Math.log(2));
        }
    }

    /**
     * @param mono   {@link #SIZE} samples in -1..1
     * @param levels receives {@link #BANDS} values in 0..1
     */
    public void analyze(@NonNull float[] mono, int sampleRate, @NonNull float[] levels) {
        prepareBins(sampleRate);
        for (int i = 0; i < SIZE; i++) {
            re[reversed[i]] = mono[i] * hann[i];
            im[i] = 0f;
        }
        transform();
        // A full-scale sine under a Hann window peaks at SIZE / 4.
        float scale = 4f / SIZE;
        for (int band = 0; band < BANDS; band++) {
            float peak = 0f;
            for (int bin = firstBin[band]; bin <= lastBin[band]; bin++) {
                peak = Math.max(peak, re[bin] * re[bin] + im[bin] * im[bin]);
            }
            if (peak <= 0f) {
                levels[band] = 0f;
                continue;
            }
            float db = (float) (10 * Math.log10(peak * scale * scale)) + tiltDb[band];
            levels[band] = Math.max(0f, Math.min(1f, (db - FLOOR_DB) / (CEILING_DB - FLOOR_DB)));
        }
    }

    /** In-place radix-2 FFT of data already in bit-reversed order. */
    private void transform() {
        for (int length = 2; length <= SIZE; length <<= 1) {
            int half = length / 2;
            int step = SIZE / length;
            for (int start = 0; start < SIZE; start += length) {
                for (int k = 0; k < half; k++) {
                    int a = start + k;
                    int b = a + half;
                    float wr = cos[k * step];
                    float wi = sin[k * step];
                    float tr = re[b] * wr - im[b] * wi;
                    float ti = re[b] * wi + im[b] * wr;
                    re[b] = re[a] - tr;
                    im[b] = im[a] - ti;
                    re[a] += tr;
                    im[a] += ti;
                }
            }
        }
    }
}
