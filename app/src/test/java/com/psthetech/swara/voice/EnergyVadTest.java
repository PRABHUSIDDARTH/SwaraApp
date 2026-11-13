package com.psthetech.swara.voice;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.voice.audio.EnergyVad;
import java.util.Random;
import org.junit.Test;

public class EnergyVadTest {
    static final int HOP = 1280;

    static float[] zeros() { return new float[HOP]; }

    static float[] noise(float amp, long seed) {
        Random r = new Random(seed);
        float[] f = new float[HOP];
        for (int i = 0; i < HOP; i++) f[i] = (r.nextFloat() * 2f - 1f) * amp;
        return f;
    }

    static float[] sine(float amp) {
        float[] f = new float[HOP];
        for (int i = 0; i < HOP; i++) f[i] = (float) (amp * Math.sin(2 * Math.PI * 440 * i / 16000.0));
        return f;
    }

    @Test public void silenceIsNotSpeech() {
        EnergyVad vad = new EnergyVad(HOP);
        for (int i = 0; i < 5; i++) assertFalse(vad.isSpeech(zeros()));
        assertFalse(vad.isSpeech(noise(0.001f, 1)));
    }

    @Test public void loudSineIsSpeech() {
        EnergyVad vad = new EnergyVad(HOP);
        for (int i = 0; i < 3; i++) vad.isSpeech(noise(0.001f, i));
        assertTrue(vad.isSpeech(sine(0.2f)));
    }

    /** Constant-RMS square wave. */
    static float[] steady(float rms) {
        float[] f = new float[HOP];
        for (int i = 0; i < HOP; i++) f[i] = (i % 2 == 0) ? rms : -rms;
        return f;
    }

    @Test public void floorAdaptsToSteadyNoise() {
        EnergyVad vad = new EnergyVad(HOP);
        boolean[] r = new boolean[50];
        for (int i = 0; i < 50; i++) r[i] = vad.isSpeech(steady(0.02f));
        assertTrue("0.02 is initially above the floor", r[0]);
        for (int i = 40; i < 50; i++) assertFalse("floor should have adapted by hop " + i, r[i]);
    }

    @Test public void firstFrameSpeechIsDetected() {
        EnergyVad vad = new EnergyVad(HOP);
        assertTrue(vad.isSpeech(steady(0.04f)));
        com.psthetech.swara.voice.audio.CommandRecorder rec =
                new com.psthetech.swara.voice.audio.CommandRecorder(new EnergyVad(HOP), 16000);
        assertTrue(rec.push(steady(0.04f)) == com.psthetech.swara.voice.audio.CommandRecorder.State.RECORDING);
        com.psthetech.swara.voice.audio.CommandRecorder.State s = null;
        for (int i = 0; i < 10; i++) s = rec.push(zeros());
        assertTrue(s == com.psthetech.swara.voice.audio.CommandRecorder.State.DONE);
    }

    @Test public void zeroFirstFrameThenNoiseIsNotSpeechForever() {
        EnergyVad vad = new EnergyVad(HOP);
        vad.isSpeech(zeros());
        boolean last = true;
        for (int i = 0; i < 5; i++) last = vad.isSpeech(steady(0.003f));
        assertFalse(last);
    }

    @Test public void noiseStepIsEventuallyLearned() {
        EnergyVad vad = new EnergyVad(HOP);
        for (int i = 0; i < 20; i++) vad.isSpeech(steady(0.003f));
        int learnedAt = -1;
        for (int i = 1; i <= 75; i++) {
            if (!vad.isSpeech(steady(0.02f))) { learnedAt = i; break; }
        }
        assertTrue("step not learned within 75 hops", learnedAt > 0);
    }
}
