package com.psthetech.swara.vibe;

import java.util.Arrays;

/** Converts raw values to library-relative percentile ranks in [0, 1]. */
public final class PercentileNormalizer {
    private PercentileNormalizer() {}

    /** NaN is treated as the lowest value (and equal to other NaNs). Returns a new array. O(n log n). */
    public static double[] normalize(double[] raw) {
        int n = raw.length;
        double[] out = new double[n];
        if (n <= 1) {
            Arrays.fill(out, 0.5);
            return out;
        }
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> {
            boolean na = Double.isNaN(raw[a]);
            boolean nb = Double.isNaN(raw[b]);
            if (na || nb) return na == nb ? 0 : (na ? -1 : 1);
            return Double.compare(raw[a], raw[b]);
        });
        int s = 0;
        while (s < n) {
            int e = s + 1;
            while (e < n && sameValue(raw[idx[s]], raw[idx[e]])) e++;
            double v = (s + 0.5 * (e - s - 1)) / (n - 1);
            for (int k = s; k < e; k++) out[idx[k]] = v;
            s = e;
        }
        return out;
    }

    private static boolean sameValue(double a, double b) {
        if (Double.isNaN(a) || Double.isNaN(b)) return Double.isNaN(a) && Double.isNaN(b);
        return a == b;
    }
}
