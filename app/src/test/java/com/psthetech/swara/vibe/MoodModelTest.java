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
