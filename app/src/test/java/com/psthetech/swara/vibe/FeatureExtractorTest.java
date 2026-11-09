package com.psthetech.swara.vibe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.vibe.dsp.AudioFeatures;
import com.psthetech.swara.vibe.dsp.FeatureExtractor;
import com.psthetech.swara.vibe.dsp.Fft;

import org.junit.Test;

public class FeatureExtractorTest {
    private static final int SR = 22050;

    private static float[] sines(double seconds, double... freqs) {
        float[] x = new float[(int) (seconds * SR)];
        for (int i = 0; i < x.length; i++) {
            double s = 0;
            for (double f : freqs) s += Math.sin(2 * Math.PI * f * i / SR);
            x[i] = (float) (0.3 * s / freqs.length);
        }
        return x;
    }

    @Test
    public void clickTrack120_tempoNear120() {
        float[] x = new float[20 * SR];
        int period = SR / 2;
        for (int p = 0; p < x.length; p += period) {
            for (int k = 0; k < 200 && p + k < x.length; k++) {
                x[p + k] = (float) (0.9 * Math.exp(-k / 30.0) * Math.sin(2 * Math.PI * 1000 * k / SR));
            }
        }
        AudioFeatures f = FeatureExtractor.extract(x, SR);
        assertNotNull(f);
        assertEquals(120.0, f.tempoBpm, 3.0);
    }

    @Test
    public void cMajorTriad_isMajor() {
        AudioFeatures f = FeatureExtractor.extract(sines(10, 261.63, 329.63, 392.00), SR);
        assertNotNull(f);
        assertTrue(f.majorness >= 0.05);
    }

    @Test
    public void dMajorTriad_isMajor() {
        AudioFeatures f = FeatureExtractor.extract(sines(10, 293.66, 369.99, 440.00), SR);
        assertNotNull(f);
        assertTrue(f.majorness >= 0.05);
    }

    private static float[] harmonicChord(double seconds, double... freqs) {
        float[] x = new float[(int) (seconds * SR)];
        for (int i = 0; i < x.length; i++) {
            double s = 0;
            for (double f : freqs) {
                for (int h = 1; h <= 4; h++) s += Math.sin(2 * Math.PI * f * h * i / SR) / h;
            }
            x[i] = (float) (0.1 * s);
        }
        return x;
    }

    @Test
    public void harmonicRichChords_majorBeatsMinor() {
        AudioFeatures maj = FeatureExtractor.extract(harmonicChord(10, 261.63, 329.63, 392.00), SR);
        AudioFeatures min = FeatureExtractor.extract(harmonicChord(10, 220.0, 261.63, 329.63), SR);
        assertNotNull(maj);
        assertNotNull(min);
        assertTrue(maj.majorness > min.majorness + 0.2);
    }

    @Test
    public void aMinorTriad_isMinor() {
        AudioFeatures f = FeatureExtractor.extract(sines(10, 220.0, 261.63, 329.63), SR);
        assertNotNull(f);
        assertTrue(f.majorness <= -0.15);
    }

    @Test
    public void brighterSignal_hasHigherCentroid() {
        AudioFeatures hi = FeatureExtractor.extract(sines(6, 4000), SR);
        AudioFeatures lo = FeatureExtractor.extract(sines(6, 200), SR);
        assertNotNull(hi);
        assertNotNull(lo);
        assertTrue(hi.centroidHz > lo.centroidHz);
    }

    @Test
    public void silence_returnsNull() {
        assertNull(FeatureExtractor.extract(new float[10 * SR], SR));
    }

    @Test
    public void tooShort_returnsNull() {
        assertNull(FeatureExtractor.extract(sines(3, 440), SR));
    }

    @Test
    public void nonFiniteAndEmpty_neverThrow() {
        assertNull(FeatureExtractor.extract(new float[0], SR));
        float[] x = sines(6, 440);
        x[100] = Float.NaN;
        x[200] = Float.POSITIVE_INFINITY;
        assertNotNull(FeatureExtractor.extract(x, SR));
    }

    private static float[] clickTrack(double seconds, double bpm) {
        float[] x = new float[(int) (seconds * SR)];
        int period = (int) Math.round(SR * 60.0 / bpm);
        for (int p = 0; p < x.length; p += period) {
            for (int k = 0; k < 200 && p + k < x.length; k++) {
                x[p + k] = (float) (0.9 * Math.exp(-k / 30.0) * Math.sin(2 * Math.PI * 1000 * k / SR));
            }
        }
        return x;
    }

    @Test
    public void slowSparseClickTrack_isNotDoubled() {
        AudioFeatures f = FeatureExtractor.extract(clickTrack(20, 70), SR);
        assertNotNull(f);
        assertEquals(70.0, f.tempoBpm, 3.0);
    }

    @Test
    public void octave_slowTempoWithDensePulse_isDoubled() {
        assertEquals(140.0, FeatureExtractor.correctOctave(70.0, 4.1), 1e-9);
        assertEquals(168.0, FeatureExtractor.correctOctave(84.0, 3.7), 1e-9);
    }

    @Test
    public void octave_genuinelySlowSong_isNotDoubled() {
        assertEquals(70.0, FeatureExtractor.correctOctave(70.0, 1.8), 1e-9);
        assertEquals(75.0, FeatureExtractor.correctOctave(75.0, 3.3), 1e-9);
    }

    @Test
    public void octave_fastTempoWithSparsePulse_isHalved() {
        assertEquals(85.0, FeatureExtractor.correctOctave(170.0, 1.5), 1e-9);
    }

    @Test
    public void octave_fastTempoWithDensePulse_isKept() {
        assertEquals(170.0, FeatureExtractor.correctOctave(170.0, 4.0), 1e-9);
    }

    @Test
    public void octave_midTempo_isKept() {
        assertEquals(120.0, FeatureExtractor.correctOctave(120.0, 6.0), 1e-9);
        assertEquals(120.0, FeatureExtractor.correctOctave(120.0, 0.5), 1e-9);
    }

    @Test
    public void fft_impulse_isFlat() {
        double[] re = new double[8];
        double[] im = new double[8];
        re[0] = 1;
        Fft.transform(re, im);
        for (int i = 0; i < 8; i++) {
            assertEquals(1.0, Math.hypot(re[i], im[i]), 1e-9);
        }
    }
}
