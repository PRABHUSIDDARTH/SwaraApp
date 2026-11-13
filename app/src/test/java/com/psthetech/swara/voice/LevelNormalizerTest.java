package com.psthetech.swara.voice;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.voice.audio.LevelNormalizer;

import org.junit.Test;

public class LevelNormalizerTest {
    private static final int FRAME = 1280;

    /** Sine bursts: one 1280-sample frame on, one off, scaled to the given RMS over the active frames. */
    private static float[] bursts(int frames, float rms, float clipAt) {
        float[] a = new float[frames * FRAME];
        float amp = rms * (float) Math.sqrt(2);
        for (int f = 0; f < frames; f += 2) {
            for (int i = 0; i < FRAME; i++) {
                float v = amp * (float) Math.sin(2 * Math.PI * 440 * (f * FRAME + i) / 16000.0);
                a[f * FRAME + i] = Math.max(-clipAt, Math.min(clipAt, v));
            }
        }
        return a;
    }

    private static float activeRms(float[] a, int from, int to) {
        double sum = 0;
        int cnt = 0;
        for (int s = from; s + FRAME <= to; s += FRAME) {
            double e = 0;
            for (int i = s; i < s + FRAME; i++) e += a[i] * a[i];
            double r = Math.sqrt(e / FRAME);
            if (r >= 0.01) {
                sum += e;
                cnt += FRAME;
            }
        }
        return (float) Math.sqrt(sum / cnt);
    }

    private static float peak(float[] a) {
        float p = 0;
        for (float v : a) p = Math.max(p, Math.abs(v));
        return p;
    }

    @Test public void hotSpeechIsTurnedDown() {
        float[] in = bursts(20, 0.2f, 1.0f);
        float[] out = LevelNormalizer.normalize(in);
        assertEquals(0.05f, activeRms(out, 0, out.length), 0.005f);
        assertTrue(peak(out) <= 0.9f + 1e-6f);
    }

    @Test public void quietSpeechIsTurnedUp() {
        float[] in = bursts(20, 0.005f, 1.0f);
        // active frames are below 0.01, so the whole buffer is used: RMS = 0.005/sqrt2
        float[] out = LevelNormalizer.normalize(in);
        double e = 0;
        for (float v : out) e += v * v;
        float rms = (float) Math.sqrt(e / out.length);
        double e0 = 0;
        for (float v : in) e0 += v * v;
        float rms0 = (float) Math.sqrt(e0 / in.length);
        assertEquals(Math.min(8f, 0.05f / rms0) * rms0, rms, 0.1f * rms);
    }

    @Test public void quietActiveSpeechCappedAtGain8() {
        float[] in = bursts(20, 0.012f, 1.0f); // active frames, RMS 0.012 -> gain 4.17
        float[] out = LevelNormalizer.normalize(in);
        assertEquals(0.05f, activeRms(out, 0, out.length), 0.005f);
        float[] quiet = bursts(20, 0.0055f, 1.0f);
        for (int i = 0; i < quiet.length; i++) quiet[i] *= 1f; // below threshold -> whole-buffer path, gain capped at 8
        float[] o2 = LevelNormalizer.normalize(quiet);
        assertEquals(8f * peak(quiet), peak(o2), 0.01f * 8f * peak(quiet));
    }

    @Test public void silencePaddingDoesNotInflateGain() {
        float[] speech = bursts(6, 0.05f, 1.0f); // 0.48 s
        float[] in = new float[speech.length + 40000];
        System.arraycopy(speech, 0, in, 0, speech.length);
        float[] out = LevelNormalizer.normalize(in);
        assertEquals(0.05f, activeRms(out, 0, speech.length), 0.005f);
    }

    @Test public void peakNeverExceeds0_9() {
        float[] in = bursts(20, 0.02f, 1.0f);
        in[5] = 1.0f;
        in[FRAME * 2 + 3] = -1.0f;
        float[] out = LevelNormalizer.normalize(in);
        assertTrue(peak(out) <= 0.9f + 1e-6f);
    }

    @Test public void silentInputUnchanged() {
        float[] in = new float[5000];
        float[] out = LevelNormalizer.normalize(in);
        assertArrayEquals(in, out, 0f);
        assertTrue(in != out);
        assertEquals(0, LevelNormalizer.normalize(new float[0]).length);
    }

    @Test public void inputNotMutated() {
        float[] in = bursts(10, 0.2f, 1.0f);
        float[] copy = in.clone();
        LevelNormalizer.normalize(in);
        assertArrayEquals(copy, in, 0f);
    }
}
