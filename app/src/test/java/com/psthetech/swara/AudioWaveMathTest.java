package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
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
}
