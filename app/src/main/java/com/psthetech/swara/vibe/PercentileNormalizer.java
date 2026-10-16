package com.psthetech.swara.vibe;

/** Converts raw values to library-relative percentile ranks in [0, 1]. */
public final class PercentileNormalizer {
    private PercentileNormalizer() {}

    /** NaN is treated as the lowest value. Returns a new array. */
    public static double[] normalize(double[] raw) {
        int n = raw.length;
        double[] out = new double[n];
        if (n <= 1) {
            java.util.Arrays.fill(out, 0.5);
            return out;
        }
        for (int i = 0; i < n; i++) {
            double vi = raw[i];
            boolean ni = Double.isNaN(vi);
            double score = 0;
            for (int j = 0; j < n; j++) {
                if (j == i) continue;
                double vj = raw[j];
                boolean nj = Double.isNaN(vj);
                if (ni && nj) score += 0.5;
                else if (nj) score += 1;
                else if (ni) continue;
                else if (vj < vi) score += 1;
                else if (vj == vi) score += 0.5;
            }
            out[i] = score / (n - 1);
        }
        return out;
    }
}
