package com.psthetech.swara.korokae.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class KorokaeV2EngineTest {

    /** Fake model returning a constant complex vocal mask (re, im) on both channels. */
    private static MaskModel constantMask(float re, float im) {
        return new MaskModel() {
            @Override public float[] run(float[] spec, int frames) {
                int plane = Stft.BINS * frames;
                float[] out = new float[4 * plane];
                for (int i = 0; i < plane; i++) {
                    out[i] = re; out[plane + i] = im;
                    out[2 * plane + i] = re; out[3 * plane + i] = im;
                }
                return out;
            }
            @Override public void close() {}
        };
    }

    private static float[] noise(int frames, long seed) {
        Random r = new Random(seed);
        float[] x = new float[frames * 2];
        for (int i = 0; i < x.length; i++) x[i] = (float) (r.nextGaussian() * 0.3);
        return x;
    }

    private static float maxAbsDiff(float[] a, float[] b, float scaleB) {
        float m = 0f;
        for (int i = 0; i < a.length; i++) m = Math.max(m, Math.abs(a[i] - b[i] * scaleB));
        return m;
    }

    private static float[] run(MaskModel model, float[] x) throws Exception {
        return new KorokaeV2Engine(model).separate(x, x.length / 2, p -> {}, () -> false);
    }

    @Test
    public void zeroMaskReturnsInput() throws Exception {
        int frames = (int) (44100 * 17.3);
        float[] x = new float[frames * 2];
        for (int i = 0; i < frames; i++) {
            float v = ((i / 220) % 2 == 0) ? 1.0f : -1.0f; // 100 Hz full-scale square
            x[2 * i] = v; x[2 * i + 1] = -v;
        }
        float[] y = run(constantMask(0f, 0f), x);
        assertEquals(x.length, y.length);
        assertTrue(maxAbsDiff(y, x, 1f) < 1e-4f);
    }

    @Test
    public void identityVocalMaskGivesSilence() throws Exception {
        float[] x = noise(44100 * 10, 1);
        float[] y = run(constantMask(1f, 0f), x);
        assertTrue(maxAbsDiff(y, new float[y.length], 0f) < 1e-4f);
    }

    @Test
    public void shortInputKeepsLength() throws Exception {
        float[] x = noise((int) (44100 * 0.3), 2);
        float[] y = run(constantMask(0f, 0f), x);
        assertEquals(x.length, y.length);
        assertTrue(maxAbsDiff(y, x, 1f) < 1e-4f);
    }

    @Test
    public void oddLengthKeepsLength() throws Exception {
        float[] x = noise(352800 * 2 + 7, 3);
        float[] y = run(constantMask(0f, 0f), x);
        assertEquals(x.length, y.length);
        assertTrue(maxAbsDiff(y, x, 1f) < 1e-4f);
    }

    @Test
    public void progressIsMonotonicAndEndsAt100() throws Exception {
        float[] x = noise(44100 * 30, 4);
        List<Integer> seen = new ArrayList<>();
        new KorokaeV2Engine(constantMask(0f, 0f)).separate(x, x.length / 2, seen::add, () -> false);
        assertTrue(!seen.isEmpty());
        for (int i = 1; i < seen.size(); i++) assertTrue(seen.get(i) >= seen.get(i - 1));
        assertEquals(100, (int) seen.get(seen.size() - 1));
    }

    @Test
    public void cancellationStopsBetweenWindows() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean cancel = new AtomicBoolean(false);
        MaskModel inner = constantMask(0f, 0f);
        MaskModel counting = new MaskModel() {
            @Override public float[] run(float[] spec, int frames) throws Exception {
                calls.incrementAndGet();
                cancel.set(true);
                return inner.run(spec, frames);
            }
            @Override public void close() {}
        };
        float[] x = noise(44100 * 30, 5);
        try {
            new KorokaeV2Engine(counting).separate(x, x.length / 2, p -> {}, cancel::get);
            fail("expected CancellationException");
        } catch (CancellationException expected) {
            assertEquals(1, calls.get());
        }
    }

    @Test
    public void matchesPythonChunking() throws Exception {
        float[] x = noise(44100 * 23, 6); // 4 windows: seams at 7 s, 14 s, 21 s
        float[] y = run(constantMask(0.5f, 0f), x);
        assertTrue(maxAbsDiff(y, x, 0.5f) < 1e-4f);
    }
}
