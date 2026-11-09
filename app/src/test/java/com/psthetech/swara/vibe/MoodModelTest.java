package com.psthetech.swara.vibe;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.vibe.dsp.AudioFeatures;

import org.junit.Test;

public class MoodModelTest {

    @Test
    public void fasterLouder_isMoreEnergetic() {
        AudioFeatures calm = new AudioFeatures(0.01, 1.0, 70, 1500, 0);
        AudioFeatures driving = new AudioFeatures(0.2, 6.0, 150, 1500, 0);
        assertTrue(RawMoodModel.rawEnergy(driving) > RawMoodModel.rawEnergy(calm));
        double e = RawMoodModel.rawEnergy(driving);
        assertTrue(e >= 0 && e <= 1);
    }

    @Test
    public void major_isHigherValenceThanMinor() {
        AudioFeatures major = new AudioFeatures(0.1, 3, 110, 2000, 0.8);
        AudioFeatures minor = new AudioFeatures(0.1, 3, 110, 2000, -0.8);
        assertTrue(RawMoodModel.rawValence(major) > RawMoodModel.rawValence(minor));
    }

    @Test
    public void extremeInputs_areClampedToUnitRange() {
        AudioFeatures hi = new AudioFeatures(100, 100, 1000, 100000, 5);
        AudioFeatures lo = new AudioFeatures(0, -3, 0, 0, -5);
        assertEquals(1.0, RawMoodModel.rawEnergy(hi), 1e-9);
        assertEquals(1.0, RawMoodModel.rawValence(hi), 1e-9);
        assertEquals(0.0, RawMoodModel.rawEnergy(lo), 1e-9);
        assertEquals(0.0, RawMoodModel.rawValence(lo), 1e-9);
    }

    @Test
    public void energy_ignoresTempo() {
        AudioFeatures slow = new AudioFeatures(0.3, 3.5, 70, 2000, 0);
        AudioFeatures fast = new AudioFeatures(0.3, 3.5, 170, 2000, 0);
        assertEquals(RawMoodModel.rawEnergy(slow), RawMoodModel.rawEnergy(fast), 1e-12);
    }

    @Test
    public void denserOnsets_isMoreEnergetic() {
        AudioFeatures sparse = new AudioFeatures(0.3, 2.0, 120, 2000, 0);
        AudioFeatures dense = new AudioFeatures(0.3, 4.5, 120, 2000, 0);
        assertTrue(RawMoodModel.rawEnergy(dense) > RawMoodModel.rawEnergy(sparse));
    }

    // Typical feature values of the three labelled groups in the calibration set.
    private static final AudioFeatures DANCE = new AudioFeatures(0.37, 3.5, 128, 2300, -0.04);
    private static final AudioFeatures PLEASANT = new AudioFeatures(0.29, 2.4, 109, 2000, 0.02);
    private static final AudioFeatures SAD = new AudioFeatures(0.26, 2.65, 90, 2000, -0.09);

    @Test
    public void danceSong_isHappierThanPleasant_isHappierThanSad() {
        double d = RawMoodModel.rawValence(DANCE);
        double p = RawMoodModel.rawValence(PLEASANT);
        double s = RawMoodModel.rawValence(SAD);
        assertTrue("dance " + d + " vs pleasant " + p, d > p);
        assertTrue("pleasant " + p + " vs sad " + s, p > s);
    }

    @Test
    public void loudDenseMinorishDance_beatsSoftMajorBallad() {
        AudioFeatures dance = new AudioFeatures(0.45, 4.2, 130, 1900, -0.15);
        AudioFeatures ballad = new AudioFeatures(0.25, 2.1, 115, 2000, 0.15);
        assertTrue(RawMoodModel.rawValence(dance) > RawMoodModel.rawValence(ballad));
    }

    @Test
    public void danceableCues_raiseValence() {
        AudioFeatures quiet = new AudioFeatures(0.1, 2.0, 110, 2000, 0);
        AudioFeatures danceable = new AudioFeatures(0.4, 4.5, 110, 2000, 0);
        assertTrue(RawMoodModel.rawValence(danceable) > RawMoodModel.rawValence(quiet));
    }

    @Test
    public void valence_ignoresBrightness() {
        AudioFeatures dull = new AudioFeatures(0.3, 3, 110, 800, 0);
        AudioFeatures bright = new AudioFeatures(0.3, 3, 110, 4500, 0);
        assertEquals(RawMoodModel.rawValence(dull), RawMoodModel.rawValence(bright), 1e-12);
    }

    @Test
    public void normalize_ranks() {
        assertArrayEquals(new double[]{1.0, 0.0, 0.5},
                PercentileNormalizer.normalize(new double[]{3, 1, 2}), 1e-9);
    }

    @Test
    public void normalize_allEqual_isHalf() {
        assertArrayEquals(new double[]{0.5, 0.5, 0.5},
                PercentileNormalizer.normalize(new double[]{2, 2, 2}), 1e-9);
    }

    @Test
    public void normalize_single_isHalf() {
        assertArrayEquals(new double[]{0.5},
                PercentileNormalizer.normalize(new double[]{7}), 1e-9);
    }

    @Test
    public void normalize_nanIsLowest_neverNaN_doesNotMutate() {
        double[] in = {2, Double.NaN, 1};
        double[] out = PercentileNormalizer.normalize(in);
        assertArrayEquals(new double[]{1.0, 0.0, 0.5}, out, 1e-9);
        assertTrue(Double.isNaN(in[1]));
        for (double v : out) assertFalse(Double.isNaN(v));
    }

    @Test
    public void packUnpack_roundTrips() {
        byte[] b = RawMoodModel.pack(0.25, 0.75, 123.5);
        assertEquals(24, b.length);
        assertArrayEquals(new double[]{0.25, 0.75, 123.5}, RawMoodModel.unpack(b), 0.0);
    }

    @Test
    public void unpack_badInput_returnsNull() {
        assertNull(RawMoodModel.unpack(null));
        assertNull(RawMoodModel.unpack(new byte[5]));
    }

    @Test
    public void failedResult_isNotOk() {
        MoodResult r = MoodResult.failed(3);
        assertFalse(r.ok);
        assertEquals(3, r.scorerVersion);
    }

    @Test
    public void normalize_mixedTies() {
        assertArrayEquals(new double[]{0.25, 0.25, 1.0},
                PercentileNormalizer.normalize(new double[]{1, 1, 2}), 1e-9);
    }

    @Test
    public void normalize_multipleNaNs() {
        assertArrayEquals(new double[]{0.25, 0.25, 1.0},
                PercentileNormalizer.normalize(new double[]{Double.NaN, Double.NaN, 5}), 1e-9);
    }

    @Test
    public void normalize_allNaN_isHalf() {
        assertArrayEquals(new double[]{0.5, 0.5, 0.5},
                PercentileNormalizer.normalize(new double[]{Double.NaN, Double.NaN, Double.NaN}), 1e-9);
    }

    @Test
    public void normalize_largeInput_isFast() {
        java.util.Random r = new java.util.Random(42);
        double[] in = new double[20000];
        for (int i = 0; i < in.length; i++) in[i] = r.nextDouble();
        long t = System.nanoTime();
        double[] out = PercentileNormalizer.normalize(in);
        long ms = (System.nanoTime() - t) / 1_000_000;
        assertEquals(in.length, out.length);
        assertTrue("took " + ms + " ms", ms < 500);
    }
}
