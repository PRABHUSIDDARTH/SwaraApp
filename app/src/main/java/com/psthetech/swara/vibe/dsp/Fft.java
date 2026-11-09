package com.psthetech.swara.vibe.dsp;

/** In-place radix-2 FFT. Pure Java, thread safe. */
public final class Fft {
    private static final class Tables {
        final int n;
        final double[] cos;
        final double[] sin;
        final int[] rev;

        Tables(int n) {
            this.n = n;
            cos = new double[n / 2];
            sin = new double[n / 2];
            for (int i = 0; i < n / 2; i++) {
                cos[i] = Math.cos(2 * Math.PI * i / n);
                sin[i] = -Math.sin(2 * Math.PI * i / n);
            }
            rev = new int[n];
            int bits = Integer.numberOfTrailingZeros(n);
            for (int i = 0; i < n; i++) {
                rev[i] = bits == 0 ? 0 : Integer.reverse(i) >>> (32 - bits);
            }
        }
    }

    private static volatile Tables cached;

    private Fft() {}

    private static Tables tables(int n) {
        Tables t = cached;
        if (t == null || t.n != n) {
            t = new Tables(n);
            cached = t;
        }
        return t;
    }

    /** Forward FFT. Length must be a power of 2 and re/im the same length. */
    public static void transform(double[] re, double[] im) {
        int n = re.length;
        if (n != im.length || n < 1 || (n & (n - 1)) != 0) {
            throw new IllegalArgumentException("length must be a power of 2");
        }
        if (n == 1) return;
        Tables t = tables(n);
        for (int i = 0; i < n; i++) {
            int j = t.rev[i];
            if (j > i) {
                double a = re[i]; re[i] = re[j]; re[j] = a;
                a = im[i]; im[i] = im[j]; im[j] = a;
            }
        }
        for (int size = 2; size <= n; size <<= 1) {
            int half = size >> 1;
            int step = n / size;
            for (int start = 0; start < n; start += size) {
                for (int k = 0, idx = 0; k < half; k++, idx += step) {
                    double wr = t.cos[idx];
                    double wi = t.sin[idx];
                    int a = start + k;
                    int b = a + half;
                    double xr = re[b] * wr - im[b] * wi;
                    double xi = re[b] * wi + im[b] * wr;
                    re[b] = re[a] - xr;
                    im[b] = im[a] - xi;
                    re[a] += xr;
                    im[a] += xi;
                }
            }
        }
    }
}
