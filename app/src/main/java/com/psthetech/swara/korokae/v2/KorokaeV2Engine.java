package com.psthetech.swara.korokae.v2;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Separates a whole song into its instrumental with a {@link MaskModel}.
 *
 * <p>Mirrors {@code dsp.separate} in {@code ml/korokae_v2}: 8 s windows with 1 s overlap and
 * linear crossfades; per window STFT -> complex vocal mask M -> instrumental X - M*X -> iSTFT.
 * The two crossfade weights in each overlap sum to exactly 1, so windows are added straight into
 * the output without a separate weight-sum buffer.
 */
public final class KorokaeV2Engine {

    public static final int SAMPLE_RATE = 44100;
    public static final int WINDOW = 8 * SAMPLE_RATE;
    public static final int OVERLAP = SAMPLE_RATE;

    /** Receives 0..100 as windows complete. */
    public interface ProgressListener {
        void onProgress(int percent);
    }

    private final MaskModel model;

    public KorokaeV2Engine(MaskModel model) {
        this.model = model;
    }

    /**
     * @param interleavedStereo {@code frames * 2} samples, L/R interleaved, 44.1 kHz
     * @return the instrumental, interleaved, exactly {@code frames * 2} samples
     * @throws CancellationException when {@code cancelled} is true at a window boundary
     */
    public float[] separate(float[] interleavedStereo, int frames, ProgressListener progress,
                            BooleanSupplier cancelled) throws Exception {
        float[] out = new float[frames * 2];
        int step = WINDOW - OVERLAP;
        int windows = 1;
        while ((long) (windows - 1) * step + step < frames) windows++;

        int t = Stft.frameCount(WINDOW);
        int plane = Stft.BINS * t;
        float[] left = new float[WINDOW];
        float[] right = new float[WINDOW];
        float[] spec = new float[4 * plane];
        float[] instRe = new float[plane];
        float[] instIm = new float[plane];
        float[] mono = new float[WINDOW];
        float[] lRe = new float[plane];
        float[] lIm = new float[plane];
        float[] rRe = new float[plane];
        float[] rIm = new float[plane];

        for (int k = 0; k < windows; k++) {
            if (cancelled.getAsBoolean()) throw new CancellationException("Korokae cancelled");
            int start = k * step;
            int n = Math.min(WINDOW, frames - start);
            for (int i = 0; i < WINDOW; i++) {
                boolean inside = i < n;
                left[i] = inside ? interleavedStereo[2 * (start + i)] : 0f;
                right[i] = inside ? interleavedStereo[2 * (start + i) + 1] : 0f;
            }
            Stft.forward(left, WINDOW, lRe, lIm);
            Stft.forward(right, WINDOW, rRe, rIm);
            System.arraycopy(lRe, 0, spec, 0, plane);
            System.arraycopy(lIm, 0, spec, plane, plane);
            System.arraycopy(rRe, 0, spec, 2 * plane, plane);
            System.arraycopy(rIm, 0, spec, 3 * plane, plane);

            float[] mask = model.run(spec, t);

            boolean first = k == 0;
            boolean last = k == windows - 1;
            for (int c = 0; c < 2; c++) {
                float[] xr = c == 0 ? lRe : rRe;
                float[] xi = c == 0 ? lIm : rIm;
                int mo = 2 * c * plane;
                for (int i = 0; i < plane; i++) {
                    float mr = mask[mo + i];
                    float mi = mask[mo + plane + i];
                    float vr = mr * xr[i] - mi * xi[i];
                    float vi = mr * xi[i] + mi * xr[i];
                    instRe[i] = xr[i] - vr;
                    instIm[i] = xi[i] - vi;
                }
                Stft.inverse(instRe, instIm, t, WINDOW, mono);
                for (int i = 0; i < n; i++) {
                    out[2 * (start + i) + c] += mono[i] * weight(i, first, last);
                }
            }
            progress.onProgress((k + 1) * 100 / windows);
        }
        return out;
    }

    /** Crossfade weight of window sample {@code i}: fade-in x fade-out (see dsp._crossfade). */
    static float weight(int i, boolean first, boolean last) {
        double in = first ? 1.0 : Math.min(1.0, (i + 1) / (double) (OVERLAP + 1));
        double out = last ? 1.0 : Math.min(1.0, (WINDOW - i) / (double) (OVERLAP + 1));
        return (float) (in * out);
    }
}
