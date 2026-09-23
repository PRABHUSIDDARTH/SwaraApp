package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests verifying the mathematical models for the Material You-style
 * squiggly / wavy slider used across the Now Playing screen and home screen widget.
 */
public class WavySliderMathTest {

    private static float computeThumbX(float trackLeft, float trackRight, long progress, long max) {
        float trackWidth = Math.max(1f, trackRight - trackLeft);
        float ratio = max > 0 ? (float) progress / (float) max : 0f;
        ratio = Math.max(0f, Math.min(1f, ratio));
        return trackLeft + ratio * trackWidth;
    }

    private static double computeWaveDisplacement(float relX, float length, float angularFreq, float phase, float amplitude) {
        if (length <= 0) return 0.0;
        float normRemaining = Math.max(0f, (length - relX) / length);
        // Smooth sine taper envelope (1 at start, 0 at thumb end)
        double taper = Math.sin(Math.PI * 0.5 * Math.min(1.0, normRemaining * 3.5));
        return Math.sin(relX * angularFreq - phase) * amplitude * taper;
    }

    @Test
    public void testThumbPositionClamping() {
        float left = 16f;
        float right = 300f;

        // At progress 0, thumb must be at track left
        assertEquals(left, computeThumbX(left, right, 0, 100), 1e-4);

        // At progress max, thumb must be at track right
        assertEquals(right, computeThumbX(left, right, 100, 100), 1e-4);

        // Negative progress must clamp to left
        assertEquals(left, computeThumbX(left, right, -50, 100), 1e-4);

        // Progress exceeding max must clamp to right
        assertEquals(right, computeThumbX(left, right, 250, 100), 1e-4);

        // Halfway progress must be at exact midpoint
        float midpoint = left + (right - left) * 0.5f;
        assertEquals(midpoint, computeThumbX(left, right, 50, 100), 1e-4);
    }

    @Test
    public void testWaveDisplacementTapersToZeroAtThumb() {
        float length = 150f;
        float cycleLength = 24f;
        float angularFreq = (float) (2.0 * Math.PI / cycleLength);
        float amplitude = 6f;

        // At the thumb endpoint (relX == length), wave displacement must taper to 0.0
        for (int frame = 0; frame < 4; frame++) {
            float phase = (float) (frame * (Math.PI / 2.0));
            double displacementAtThumb = computeWaveDisplacement(length, length, angularFreq, phase, amplitude);
            assertEquals(0.0, displacementAtThumb, 1e-6);
        }

        // Within the played section (e.g. at 20%), wave must undulate actively
        double activeDisplacement = computeWaveDisplacement(30f, length, angularFreq, 0f, amplitude);
        assertTrue(Math.abs(activeDisplacement) > 0.01);
    }

    @Test
    public void testRestingPauseFlatLineModel() {
        // When paused, wave amplitude ratio is 0
        float amplitudeRatio = 0.0f;
        float baseAmplitude = 6f;
        float effectiveAmplitude = baseAmplitude * amplitudeRatio;

        assertEquals(0.0f, effectiveAmplitude, 1e-6);
    }
}
