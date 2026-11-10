package com.psthetech.swara.korokae.v2;

import com.psthetech.swara.vibe.dsp.Fft;

/**
 * STFT / iSTFT for Korokae v2. Mirrors {@code ml/korokae_v2/korokae_v2/dsp.py} exactly:
 * sqrt-periodic-Hann analysis and synthesis windows, N_FFT 2048, hop 512, PAD = 1536 zeros on
 * the left, right padding so frames tile exactly, overlap-add divided by 2.0.
 *
 * <p>Spectra are laid out {@code [bin * frames + t]} with {@link #BINS} bins.
 */
public final class Stft {

    public static final int N_FFT = 2048;
    public static final int HOP = 512;
    public static final int BINS = N_FFT / 2 + 1;
    public static final int PAD = N_FFT - HOP;
    private static final double OLA_GAIN = 2.0;

    private static final double[] WINDOW = new double[N_FFT];

    static {
        for (int n = 0; n < N_FFT; n++) {
            WINDOW[n] = Math.sqrt(0.5 - 0.5 * Math.cos(2 * Math.PI * n / N_FFT));
        }
    }

    private Stft() {
    }

    /** Number of STFT frames for a signal of {@code length} samples. */
    public static int frameCount(int length) {
        int need = length + 2 * PAD;
        return (need - N_FFT + HOP - 1) / HOP + 1;
    }

    /** Samples {@code [0, length)} of {@code mono} -> {@code outRe/outIm[BINS * frameCount]}. */
    public static void forward(float[] mono, int length, float[] outRe, float[] outIm) {
        int frames = frameCount(length);
        double[] re = new double[N_FFT];
        double[] im = new double[N_FFT];
        for (int t = 0; t < frames; t++) {
            int origin = t * HOP - PAD; // index into mono of frame sample 0
            for (int n = 0; n < N_FFT; n++) {
                int i = origin + n;
                re[n] = (i >= 0 && i < length) ? mono[i] * WINDOW[n] : 0.0;
                im[n] = 0.0;
            }
            Fft.transform(re, im);
            for (int b = 0; b < BINS; b++) {
                outRe[b * frames + t] = (float) re[b];
                outIm[b * frames + t] = (float) im[b];
            }
        }
    }

    /** Inverse of {@link #forward}: writes {@code length} samples to {@code outMono}. */
    public static void inverse(float[] specRe, float[] specIm, int frames, int length, float[] outMono) {
        int padded = (frames - 1) * HOP + N_FFT;
        double[] acc = new double[padded];
        double[] re = new double[N_FFT];
        double[] im = new double[N_FFT];
        for (int t = 0; t < frames; t++) {
            // Conjugate of the Hermitian-extended spectrum; forward FFT; conjugate; / N.
            for (int b = 0; b < BINS; b++) {
                re[b] = specRe[b * frames + t];
                im[b] = -specIm[b * frames + t];
            }
            for (int b = BINS; b < N_FFT; b++) {
                int m = N_FFT - b;
                re[b] = specRe[m * frames + t];
                im[b] = specIm[m * frames + t];
            }
            Fft.transform(re, im);
            int origin = t * HOP;
            for (int n = 0; n < N_FFT; n++) {
                acc[origin + n] += (re[n] / N_FFT) * WINDOW[n];
            }
        }
        for (int i = 0; i < length; i++) {
            outMono[i] = (float) (acc[PAD + i] / OLA_GAIN);
        }
    }
}
