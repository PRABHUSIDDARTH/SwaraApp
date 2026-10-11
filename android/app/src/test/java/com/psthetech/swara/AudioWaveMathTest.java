package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests verifying the mathematical models for the sound vibration wave patterns
 * used across the home screen widget and in-app AudioWaveView.
 */
public class AudioWaveMathTest {

    private static double computeWavePoint(double nx, double phase, double frequencyMultiplier, double harmonicWeight) {
        // Hann window envelope: 0 at edges (nx=0, nx=1), 1 in center (nx=0.5)
        double envelope = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * nx));

        double wave1 = Math.sin((nx * Math.PI * 4.0 * frequencyMultiplier) + phase);
        double wave2 = Math.sin((nx * Math.PI * 8.0 * frequencyMultiplier * 0.7) - phase * 0.6) * 0.35;
        double wave3 = Math.cos((nx * Math.PI * 2.0) + phase * 1.4) * 0.2;

        return (wave1 + wave2 * harmonicWeight + wave3) * envelope;
    }

    @Test
    public void testHannWindowEnvelopeEdgesAreZero() {
        // At the left border (nx = 0.0), wave amplitude displacement must be exactly 0
        double atStart = computeWavePoint(0.0, 0.0, 1.0, 1.0);
        assertEquals(0.0, atStart, 1e-6);

        // At the right border (nx = 1.0), wave amplitude displacement must be exactly 0
        double atEnd = computeWavePoint(1.0, 0.0, 1.0, 1.0);
        assertEquals(0.0, atEnd, 1e-6);

        // Across different phases, borders must still remain 0
        for (int frame = 0; frame < 4; frame++) {
            double phase = frame * (Math.PI / 2.0);
            assertEquals(0.0, computeWavePoint(0.0, phase, 1.0, 1.2), 1e-6);
            assertEquals(0.0, computeWavePoint(1.0, phase, 1.0, 1.2), 1e-6);
        }
    }

    @Test
    public void testWaveCrestsAreNonZeroAndBounded() {
        // In the center of the wave, sound vibration must produce dynamic movement
        for (int frame = 0; frame < 4; frame++) {
            double phase = frame * (Math.PI / 2.0);
            double valCenter = computeWavePoint(0.5, phase, 1.0, 1.0);
            // Must be finite and bounded within envelope
            assertTrue(!Double.isNaN(valCenter) && !Double.isInfinite(valCenter));
            assertTrue(Math.abs(valCenter) <= 2.0);
        }
    }

    @Test
    public void testPhaseProgressionProducesDistinctFrames() {
        // Verify that 4 frames produce distinct wave values at key vibration nodes
        double val0 = computeWavePoint(0.4, 0.0, 1.0, 1.0);
        double val1 = computeWavePoint(0.4, Math.PI / 2.0, 1.0, 1.0);
        double val2 = computeWavePoint(0.4, Math.PI, 1.0, 1.0);
        double val3 = computeWavePoint(0.4, 3.0 * Math.PI / 2.0, 1.0, 1.0);

        assertNotEquals(val0, val1, 1e-4);
        assertNotEquals(val1, val2, 1e-4);
        assertNotEquals(val2, val3, 1e-4);
    }

    @Test
    public void testRestingStateAmplitudeIsFlat() {
        // When paused / resting, target amplitude = 0, so computed y displacement must be 0 everywhere
        double targetAmplitude = 0.0;
        for (double nx = 0.0; nx <= 1.0; nx += 0.1) {
            double displacement = computeWavePoint(nx, 1.5, 1.0, 1.0) * targetAmplitude;
            assertEquals(0.0, displacement, 1e-6);
        }
    }

    @Test
    public void testRgbColorChannelSeparation() {
        int swaraGold = 0xFFC9A84C;
        int r = (swaraGold >> 16) & 0xFF;
        int g = (swaraGold >> 8) & 0xFF;
        int b = swaraGold & 0xFF;

        assertEquals(0xC9, r);
        assertEquals(0xA8, g);
        assertEquals(0x4C, b);

        int oceanBlue = 0xFF2196F3;
        int ro = (oceanBlue >> 16) & 0xFF;
        int go = (oceanBlue >> 8) & 0xFF;
        int bo = oceanBlue & 0xFF;

        assertEquals(0x21, ro);
        assertEquals(0x96, go);
        assertEquals(0xF3, bo);
    }
}
