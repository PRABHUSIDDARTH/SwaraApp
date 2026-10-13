package com.psthetech.swara.ui.widget;

/**
 * CircularSeekHelper — Pure mathematical utilities for circular seeking.
 *
 * Handles:
 *  - Touch angle calculation relative to center
 *  - Signed angular delta calculation with wrap-around (-180° to +180°)
 *  - Conversion of angular delta to playback position changes
 *  - Boundary clamping [0, duration]
 *  - Zero-duration guards
 */
public final class CircularSeekHelper {

    /** Default degrees to consider a touch move intentional (dead-zone). */
    public static final float DEFAULT_DEAD_ZONE_DEG = 4.0f;

    /** Default degrees of touch rotation mapped to 1 second of playback change. */
    public static final float DEFAULT_DEG_PER_SECOND = 30.0f;

    private CircularSeekHelper() {
        // Utility class
    }

    /**
     * Calculates touch angle in degrees [0, 360) clockwise from 3 o'clock.
     *
     * @param x  Touch X coordinate
     * @param y  Touch Y coordinate
     * @param cx Center X coordinate
     * @param cy Center Y coordinate
     * @return Angle in degrees in range [0, 360)
     */
    public static float calculateAngleDeg(float x, float y, float cx, float cy) {
        float deg = (float) Math.toDegrees(Math.atan2(y - cy, x - cx));
        if (deg < 0) {
            deg += 360f;
        }
        return deg;
    }

    /**
     * Calculates signed angular delta between two angles in degrees.
     * Returns delta in [-180, 180].
     * Positive = clockwise = seek forward.
     * Negative = counter-clockwise = seek backward.
     *
     * Correctly handles 0° / 360° wrap-around so crossing 12 o'clock (270°)
     * or 3 o'clock (0°) produces a smooth small delta instead of a massive jump.
     *
     * @param fromAngle Starting angle in degrees
     * @param toAngle   Ending angle in degrees
     * @return Signed difference in [-180, 180]
     */
    public static float angularDelta(float fromAngle, float toAngle) {
        float d = toAngle - fromAngle;
        while (d > 180f) d -= 360f;
        while (d < -180f) d += 360f;
        return d;
    }

    /**
     * Computes the new playback position in milliseconds from an angular delta.
     *
     * @param currentPositionMs Current playback position in ms
     * @param deltaDegrees      Angular change in degrees (positive = CW, negative = CCW)
     * @param degPerSecond      Degrees per second ratio (> 0)
     * @param durationMs        Total media duration in ms
     * @return Clamped new position in ms in range [0, durationMs].
     *         If durationMs <= 0, returns currentPositionMs unchanged.
     */
    public static long computeNewPosition(long currentPositionMs, float deltaDegrees,
                                          float degPerSecond, long durationMs) {
        if (durationMs <= 0) {
            return currentPositionMs;
        }

        float effectiveDegPerSec = degPerSecond > 0 ? degPerSecond : DEFAULT_DEG_PER_SECOND;
        float deltaSeconds = deltaDegrees / effectiveDegPerSec;
        long deltaMs = (long) (deltaSeconds * 1000f);

        long newPos = currentPositionMs + deltaMs;
        if (newPos < 0) {
            newPos = 0;
        } else if (newPos > durationMs) {
            newPos = durationMs;
        }
        return newPos;
    }

    /**
     * Converts an angle in degrees [0, 360) (where 0° is 3 o'clock)
     * to a sweep angle starting from 12 o'clock (-90° / 270°).
     *
     * @param angleDeg Angle clockwise from 3 o'clock
     * @return Angle clockwise from 12 o'clock in range [0, 360)
     */
    public static float angleTo12OClockProgress(float angleDeg) {
        float sweep = angleDeg + 90f; // 270° (12 o'clock) + 90° = 360° -> 0°
        while (sweep >= 360f) sweep -= 360f;
        while (sweep < 0f) sweep += 360f;
        return sweep / 360f;
    }
}
