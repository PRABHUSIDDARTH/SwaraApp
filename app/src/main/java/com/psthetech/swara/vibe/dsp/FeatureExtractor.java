package com.psthetech.swara.vibe.dsp;

import androidx.annotation.Nullable;

/** Extracts tempo, onset rate, brightness and major/minor tendency from mono PCM. */
public final class FeatureExtractor {
    private static final int FRAME = 2048;
    private static final int HOP = 512;
    private static final double MIN_SECONDS = 5.0;
    private static final double MIN_RMS = 1e-4;
    private static final double[] MAJOR = {6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88};
    private static final double[] MINOR = {6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17};

    private FeatureExtractor() {}

    @Nullable
    public static AudioFeatures extract(float[] mono, int sampleRate) {
        if (mono == null || sampleRate <= 0 || mono.length < MIN_SECONDS * sampleRate) return null;
        int n = mono.length;

        double sumSq = 0;
        for (int i = 0; i < n; i++) {
            float v = mono[i];
            if (Float.isNaN(v) || Float.isInfinite(v)) continue;
            sumSq += (double) v * v;
        }
        double rms = Math.sqrt(sumSq / n);
        if (!(rms >= MIN_RMS)) return null;

        int frames = (n - FRAME) / HOP + 1;
        if (frames < 2) return null;

        double[] window = new double[FRAME];
        for (int i = 0; i < FRAME; i++) {
            window[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / FRAME);
        }
        int bins = FRAME / 2 + 1;
        double binHz = (double) sampleRate / FRAME;

        // Chroma bin mapping (precomputed): pitch class per bin, -1 outside ~130 Hz - 2 kHz.
        int[] pitchClass = new int[bins];
        double[] binWeight = new double[bins];
        double semitone = Math.pow(2.0, 1.0 / 12.0) - 1.0;
        for (int k = 0; k < bins; k++) {
            double f = k * binHz;
            if (f < 130.0 || f > 2000.0) {
                pitchClass[k] = -1;
            } else {
                long midi = Math.round(12.0 * Math.log(f / 440.0) / Math.log(2) + 69.0);
                pitchClass[k] = (int) (((midi % 12) + 12) % 12);
                // Equalise per-semitone density: bin width over semitone width at f.
                binWeight[k] = binHz / (f * semitone);
            }
        }

        double[] re = new double[FRAME];
        double[] im = new double[FRAME];
        double[] mag = new double[bins];
        double[] prev = new double[bins];
        double[] onset = new double[frames];
        double[] chroma = new double[12];
        double[] frameChroma = new double[12];
        double centroidSum = 0;
        int centroidFrames = 0;

        for (int fr = 0; fr < frames; fr++) {
            int off = fr * HOP;
            for (int i = 0; i < FRAME; i++) {
                float v = mono[off + i];
                re[i] = (Float.isNaN(v) || Float.isInfinite(v)) ? 0.0 : v * window[i];
                im[i] = 0.0;
            }
            Fft.transform(re, im);
            java.util.Arrays.fill(frameChroma, 0.0);
            double flux = 0;
            double magSum = 0;
            double weighted = 0;
            for (int k = 0; k < bins; k++) {
                double m = Math.sqrt(re[k] * re[k] + im[k] * im[k]);
                mag[k] = m;
                double d = m - prev[k];
                if (d > 0 && fr > 0) flux += d;
                magSum += m;
                weighted += m * k * binHz;
                if (pitchClass[k] >= 0) frameChroma[pitchClass[k]] += m * binWeight[k];
            }
            double chromaSum = 0;
            for (int c = 0; c < 12; c++) chromaSum += frameChroma[c];
            if (chromaSum > 1e-9) {
                for (int c = 0; c < 12; c++) chroma[c] += frameChroma[c] / chromaSum;
            }
            onset[fr] = flux;
            if (magSum > 1e-9) {
                centroidSum += weighted / magSum;
                centroidFrames++;
            }
            double[] t = prev; prev = mag; mag = t;
        }

        double frameRate = (double) sampleRate / HOP;
        double centroid = centroidFrames > 0 ? centroidSum / centroidFrames : 0;
        return new AudioFeatures(rms, onsetRate(onset, frameRate), tempo(onset, frameRate),
                centroid, majorness(chroma));
    }

    private static double onsetRate(double[] onset, double frameRate) {
        int f = onset.length;
        double mean = 0;
        for (double v : onset) mean += v;
        mean /= f;
        double var = 0;
        for (double v : onset) var += (v - mean) * (v - mean);
        double thr = mean + Math.sqrt(var / f);
        int count = 0;
        int last = -100;
        for (int i = 1; i < f - 1; i++) {
            if (onset[i] > thr && onset[i] > 1e-9 && onset[i] >= onset[i - 1] && onset[i] > onset[i + 1]
                    && i - last >= 3) {
                count++;
                last = i;
            }
        }
        return count / (f / frameRate);
    }

    private static double tempo(double[] onset, double frameRate) {
        int f = onset.length;
        double mean = 0;
        for (double v : onset) mean += v;
        mean /= f;
        double[] x = new double[f];
        for (int i = 0; i < f; i++) x[i] = onset[i] - mean;

        int minLag = Math.max(1, (int) Math.floor(60.0 * frameRate / 180.0) - 1);
        int maxLag = Math.min(f - 2, (int) Math.ceil(60.0 * frameRate / 60.0) + 1);
        if (maxLag <= minLag) return 120.0;
        double[] ac = new double[maxLag + 2];
        for (int lag = minLag - 1; lag <= maxLag + 1 && lag < f; lag++) {
            if (lag < 1) continue;
            double s = 0;
            for (int i = 0; i + lag < f; i++) s += x[i] * x[i + lag];
            ac[lag] = s / (f - lag);
        }
        double best = Double.NEGATIVE_INFINITY;
        int bestLag = -1;
        for (int lag = minLag; lag <= maxLag; lag++) {
            double bpm = 60.0 * frameRate / lag;
            if (bpm < 60.0 || bpm > 180.0) continue;
            double o = Math.log(bpm / 120.0) / Math.log(2);
            double score = ac[lag] * Math.exp(-0.5 * o * o);
            if (score > best) {
                best = score;
                bestLag = lag;
            }
        }
        if (bestLag < 0 || best <= 0) return 120.0;
        // Parabolic refinement on the raw autocorrelation.
        double lagF = bestLag;
        if (bestLag > 1 && bestLag + 1 < ac.length) {
            double a = ac[bestLag - 1], b = ac[bestLag], c = ac[bestLag + 1];
            double den = a - 2 * b + c;
            if (den < 0) {
                double d = 0.5 * (a - c) / den;
                if (Math.abs(d) <= 1) lagF += d;
            }
        }
        double bpm = 60.0 * frameRate / lagF;
        return Math.max(60.0, Math.min(180.0, bpm));
    }

    private static double majorness(double[] chroma) {
        double bestMajor = Double.NEGATIVE_INFINITY;
        double bestMinor = Double.NEGATIVE_INFINITY;
        for (int tonic = 0; tonic < 12; tonic++) {
            bestMajor = Math.max(bestMajor, pearson(chroma, MAJOR, tonic));
            bestMinor = Math.max(bestMinor, pearson(chroma, MINOR, tonic));
        }
        double m = bestMajor - bestMinor;
        if (Double.isNaN(m)) return 0;
        return Math.max(-1, Math.min(1, m));
    }

    private static double pearson(double[] chroma, double[] profile, int tonic) {
        double mc = 0, mp = 0;
        for (int i = 0; i < 12; i++) { mc += chroma[i]; mp += profile[i]; }
        mc /= 12; mp /= 12;
        double num = 0, dc = 0, dp = 0;
        for (int i = 0; i < 12; i++) {
            double c = chroma[i] - mc;
            double p = profile[((i - tonic) % 12 + 12) % 12] - mp;
            num += c * p; dc += c * c; dp += p * p;
        }
        if (dc <= 1e-12 || dp <= 1e-12) return 0;
        return num / Math.sqrt(dc * dp);
    }
}
