package com.psthetech.swara.korokae.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Test;

public class StftTest {

    @Test
    public void frameCountMatchesPython() {
        assertEquals(176, Stft.frameCount(88200));
        assertEquals(693, Stft.frameCount(352800));
        assertEquals(4, Stft.frameCount(1));
    }

    @Test
    public void roundTripReconstructs() {
        int n = (int) (44100 * 3.2);
        float[] x = new float[n];
        Random r = new Random(7);
        for (int i = 0; i < n; i++) x[i] = (float) r.nextGaussian();
        int t = Stft.frameCount(n);
        float[] re = new float[Stft.BINS * t];
        float[] im = new float[Stft.BINS * t];
        Stft.forward(x, n, re, im);
        float[] y = new float[n];
        Stft.inverse(re, im, t, n, y);
        float max = 0f;
        for (int i = 0; i < n; i++) max = Math.max(max, Math.abs(x[i] - y[i]));
        assertTrue("max error " + max, max < 1e-4f);
    }

    @Test
    public void sineLandsInExpectedBin() {
        int n = 44100;
        double f = 50.0 * 44100 / 2048; // exactly bin 50
        float[] x = new float[n];
        for (int i = 0; i < n; i++) x[i] = (float) Math.sin(2 * Math.PI * f * i / 44100.0);
        int t = Stft.frameCount(n);
        float[] re = new float[Stft.BINS * t];
        float[] im = new float[Stft.BINS * t];
        Stft.forward(x, n, re, im);
        int mid = t / 2;
        int best = -1;
        double bestMag = -1;
        for (int b = 0; b < Stft.BINS; b++) {
            double m = Math.hypot(re[b * t + mid], im[b * t + mid]);
            if (m > bestMag) { bestMag = m; best = b; }
        }
        assertEquals(50, best);
    }
}
