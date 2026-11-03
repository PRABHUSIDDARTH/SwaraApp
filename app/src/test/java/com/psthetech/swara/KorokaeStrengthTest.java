package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.data.preference.KorokaePreferences;
import com.psthetech.swara.korokae.KorokaeAudioProcessor;
import com.psthetech.swara.korokae.KorokaeEngine;
import com.psthetech.swara.korokae.KorokaeModel;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Vocal-reduction strength: clamping, target mix, and the engine's dry/wet contract. */
public class KorokaeStrengthTest {

    private static final int FRAMES = 16384;
    private KorokaeModel model;
    private float[] input;

    private static File asset(String rel) {
        File f = new File("src/main/assets/" + rel);
        return f.exists() ? f : new File("app/src/main/assets/" + rel);
    }

    @Before
    public void setUp() throws IOException {
        try (InputStream in = new FileInputStream(asset(KorokaeModel.ASSET_PATH))) {
            model = KorokaeModel.load(in);
        }
        java.util.Random rnd = new java.util.Random(11);
        input = new float[2 * FRAMES];
        for (int i = 0; i < FRAMES; i++) {
            float tone = 0.3f * (float) Math.sin(2 * Math.PI * 220 * i / 44100.0);
            input[2 * i] = tone + 0.05f * (float) rnd.nextGaussian();
            input[2 * i + 1] = 0.8f * tone + 0.05f * (float) rnd.nextGaussian();
        }
    }

    private float[] run(float mix) {
        KorokaeEngine e = new KorokaeEngine(model);
        e.setMix(mix);
        e.snapMix();
        float[] out = new float[input.length];
        e.process(input, 0, out, 0, FRAMES);
        return out;
    }

    @Test
    public void clampStrengthHandlesBadValues() {
        assertEquals(1f, KorokaePreferences.clampStrength(Float.NaN), 0f);
        assertEquals(0f, KorokaePreferences.clampStrength(-0.5f), 0f);
        assertEquals(1f, KorokaePreferences.clampStrength(7f), 0f);
        assertEquals(0.4f, KorokaePreferences.clampStrength(0.4f), 0f);
    }

    @Test
    public void targetMixIsStrengthWhenEnabledAndZeroWhenDisabled() {
        assertEquals(0.3f, KorokaeAudioProcessor.targetMix(true, 0.3f), 0f);
        assertEquals(0f, KorokaeAudioProcessor.targetMix(false, 0.3f), 0f);
        assertEquals(1f, KorokaeAudioProcessor.targetMix(true, Float.NaN), 0f);
    }

    @Test
    public void strengthZeroIsDelayedPassthrough() {
        float[] out = run(0f);
        int lat = new KorokaeEngine(model).getLatencyFrames();
        for (int f = lat; f < FRAMES; f++) {
            assertEquals(input[2 * (f - lat)], out[2 * f], 1e-6f);
            assertEquals(input[2 * (f - lat) + 1], out[2 * f + 1], 1e-6f);
        }
    }

    @Test
    public void strengthOneDiffersFromPassthrough() {
        float[] dry = run(0f);
        float[] wet = run(1f);
        double diff = 0;
        for (int i = 4096; i < dry.length; i++) diff += Math.abs(dry[i] - wet[i]);
        assertTrue("wet output must differ from dry, sum=" + diff, diff > 1.0);
    }

    @Test
    public void halfStrengthIsAverageOfDryAndWet() {
        float[] dry = run(0f);
        float[] wet = run(1f);
        float[] half = run(0.5f);
        for (int i = 0; i < half.length; i++) {
            assertEquals(0.5f * (dry[i] + wet[i]), half[i], 1e-5f);
        }
    }

    @Test
    public void strengthChangeHasNoClick() {
        KorokaeEngine e = new KorokaeEngine(model);
        e.setMix(0f);
        e.snapMix();
        float[] sine = new float[2 * FRAMES];
        for (int i = 0; i < FRAMES; i++) {
            float v = 0.3f * (float) Math.sin(2 * Math.PI * 220 * i / 44100.0);
            sine[2 * i] = v;
            sine[2 * i + 1] = v;
        }
        float[] out = new float[sine.length];
        int half = FRAMES / 2;
        e.process(sine, 0, out, 0, half);
        e.setMix(1f); // no snap: must ramp
        e.process(sine, 2 * half, out, 2 * half, FRAMES - half);
        float maxJump = 0;
        for (int f = 1; f < FRAMES; f++) {
            maxJump = Math.max(maxJump, Math.abs(out[2 * f] - out[2 * (f - 1)]));
        }
        assertTrue("largest sample jump " + maxJump, maxJump < 0.2f);
    }
}
