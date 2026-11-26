package com.psthetech.swara.audio.spectrum;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpectrumMathTest {

    private static final int N = SpectrumAnalyzer.SIZE;
    private static final int BANDS = SpectrumAnalyzer.BANDS;

    private static float[] tone(double hz, int rate, double amplitude) {
        float[] s = new float[N];
        for (int i = 0; i < N; i++) s[i] = (float) (amplitude * Math.sin(2 * Math.PI * hz * i / rate));
        return s;
    }

    private static int loudest(float[] levels) {
        int best = 0;
        for (int i = 1; i < levels.length; i++) if (levels[i] > levels[best]) best = i;
        return best;
    }

    @Test
    public void silenceIsZeroInEveryBand() {
        float[] levels = new float[BANDS];
        new SpectrumAnalyzer().analyze(new float[N], 44100, levels);
        assertArrayEquals(new float[BANDS], levels, 0f);
    }

    @Test
    public void aToneLandsInTheBandThatContainsItAtEveryCommonRate() {
        SpectrumAnalyzer analyzer = new SpectrumAnalyzer(); // reused, as the rate changes
        float[] levels = new float[BANDS];
        for (int rate : new int[]{22050, 44100, 48000, 96000, 44100}) {
            for (double hz : new double[]{100, 440, 1000, 5000}) {
                analyzer.analyze(tone(hz, rate, 0.5), rate, levels);
                int expected = SpectrumAnalyzer.bandOf((float) hz);
                assertTrue(hz + " Hz at " + rate + " landed in " + loudest(levels)
                        + ", expected " + expected, Math.abs(loudest(levels) - expected) <= 1);
            }
        }
    }

    @Test
    public void levelsStayWithinZeroAndOneAndFollowLoudness() {
        SpectrumAnalyzer analyzer = new SpectrumAnalyzer();
        float[] loud = new float[BANDS];
        float[] quiet = new float[BANDS];
        analyzer.analyze(tone(1000, 44100, 1.0), 44100, loud);
        analyzer.analyze(tone(1000, 44100, 0.02), 44100, quiet);
        for (float v : loud) assertTrue(v >= 0f && v <= 1f);
        int band = SpectrumAnalyzer.bandOf(1000f);
        assertEquals(1f, loud[loudest(loud)], 1e-3f);
        assertTrue(quiet[band] < loud[band]);
        assertTrue(quiet[band] > 0f);
    }

    @Test
    public void bandsAboveNyquistStayEmpty() {
        float[] levels = new float[BANDS];
        new SpectrumAnalyzer().analyze(tone(1000, 8000, 1.0), 8000, levels);
        assertEquals(0f, levels[BANDS - 1], 0f);
    }

    @Test
    public void bandsAreLogSpacedAndClamped() {
        assertEquals(0, SpectrumAnalyzer.bandOf(1f));
        assertEquals(BANDS - 1, SpectrumAnalyzer.bandOf(40_000f));
        assertTrue(SpectrumAnalyzer.bandOf(100f) < SpectrumAnalyzer.bandOf(1000f));
        assertTrue(SpectrumAnalyzer.bandOf(1000f) < SpectrumAnalyzer.bandOf(10_000f));
    }

    @Test
    public void barsRiseFasterThanTheyFall() {
        float[] bars = new float[]{0f, 1f};
        SpectrumSmoother.step(bars, new float[]{1f, 0f}, 16f);
        assertTrue("attack", bars[0] > 0.5f);
        assertTrue("release", bars[1] > 0.85f);
    }

    @Test
    public void smoothingDoesNotDependOnTheFrameRate() {
        float[] oneStep = new float[]{1f};
        float[] twoSteps = new float[]{1f};
        float[] silent = new float[]{0f};
        SpectrumSmoother.step(oneStep, silent, 32f);
        SpectrumSmoother.step(twoSteps, silent, 16f);
        SpectrumSmoother.step(twoSteps, silent, 16f);
        assertEquals(oneStep[0], twoSteps[0], 1e-5f);
    }

    @Test
    public void barsSettleToExactlyZero() {
        float[] bars = new float[]{1f};
        for (int i = 0; i < 200; i++) SpectrumSmoother.step(bars, new float[]{0f}, 16f);
        assertEquals(0f, bars[0], 0f);
    }
}
