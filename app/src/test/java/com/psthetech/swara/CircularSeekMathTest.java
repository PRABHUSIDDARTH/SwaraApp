package com.psthetech.swara;

import static org.junit.Assert.assertEquals;

import com.psthetech.swara.ui.widget.CircularSeekHelper;

import org.junit.Test;

/**
 * Unit tests verifying the mathematical models for circular seeking:
 *  - Clockwise vs counter-clockwise angular deltas
 *  - Boundary wrap-around across 0° / 360°
 */
public class CircularSeekMathTest {

    @Test
    public void testClockwiseDelta() {
        // Dragging clockwise from 30° to 60° should produce +30°
        float delta = CircularSeekHelper.angularDelta(30f, 60f);
        assertEquals(30f, delta, 1e-4);
    }

    @Test
    public void testCounterClockwiseDelta() {
        // Dragging counter-clockwise from 60° to 30° should produce -30°
        float delta = CircularSeekHelper.angularDelta(60f, 30f);
        assertEquals(-30f, delta, 1e-4);
    }

    @Test
    public void testWrapAroundAtZeroAnd360Degrees() {
        // Crossing 0°/360° boundary clockwise (e.g. from 350° to 10°)
        // Must be +20°, NOT -340°
        float cwAcrossBoundary = CircularSeekHelper.angularDelta(350f, 10f);
        assertEquals(20f, cwAcrossBoundary, 1e-4);

        // Crossing 0°/360° boundary counter-clockwise (e.g. from 10° to 350°)
        // Must be -20°, NOT +340°
        float ccwAcrossBoundary = CircularSeekHelper.angularDelta(10f, 350f);
        assertEquals(-20f, ccwAcrossBoundary, 1e-4);
    }

    @Test
    public void testWrapAroundAt12OClock() {
        // 12 o'clock is 270° in standard Cartesian coordinates (clockwise from 3 o'clock)
        // Dragging clockwise across 12 o'clock: 260° to 280°
        float cw = CircularSeekHelper.angularDelta(260f, 280f);
        assertEquals(20f, cw, 1e-4);

        // Dragging counter-clockwise across 12 o'clock: 280° to 260°
        float ccw = CircularSeekHelper.angularDelta(280f, 260f);
        assertEquals(-20f, ccw, 1e-4);
    }

    @Test
    public void testAngleCalculation() {
        float cx = 100f;
        float cy = 100f;

        // Point directly to the right (3 o'clock) -> 0°
        assertEquals(0f, CircularSeekHelper.calculateAngleDeg(150f, 100f, cx, cy), 1e-4);

        // Point directly down (6 o'clock) -> 90°
        assertEquals(90f, CircularSeekHelper.calculateAngleDeg(100f, 150f, cx, cy), 1e-4);

        // Point directly left (9 o'clock) -> 180°
        assertEquals(180f, CircularSeekHelper.calculateAngleDeg(50f, 100f, cx, cy), 1e-4);

        // Point directly up (12 o'clock) -> 270°
        assertEquals(270f, CircularSeekHelper.calculateAngleDeg(100f, 50f, cx, cy), 1e-4);
    }
}
