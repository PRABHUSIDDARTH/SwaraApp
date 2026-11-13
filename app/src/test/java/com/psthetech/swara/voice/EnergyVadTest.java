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

    @Test public void floorAdaptsToSteadyNoise() {
        EnergyVad vad = new EnergyVad(HOP);
        for (int i = 0; i < 50; i++) assertFalse(vad.isSpeech(noise(0.02f, i)));
    }
}
