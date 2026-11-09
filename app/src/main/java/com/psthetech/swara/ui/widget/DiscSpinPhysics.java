package com.psthetech.swara.ui.widget;

/**
 * Pure maths for the Now Playing disc spin (no Android dependencies, unit-testable).
 *
 * Velocities are in degrees per millisecond; positive is clockwise.
 */
public final class DiscSpinPhysics {

    /** Weight given to the newest touch sample when smoothing finger velocity. */
    static final float VELOCITY_SMOOTHING = 0.6f;

    private DiscSpinPhysics() { }

    /** Wraps an angle into [0, 360). */
    public static float wrap(float deg) {
        float r = deg % 360f;
        return r < 0f ? r + 360f : r;
    }

    /**
     * Exponentially moves {@code current} toward {@code target}. {@code tauMs} is the time constant:
     * after one tau the remaining gap is about 37%. Frame-rate independent.
     */
    public static float approach(float current, float target, float dtMs, float tauMs) {
        if (dtMs <= 0f) return current;
        if (tauMs <= 0f) return target;
        float k = (float) Math.exp(-dtMs / tauMs);
        return target + (current - target) * k;
    }

    /**
     * Updates a smoothed finger angular velocity from a new move sample.
     *
     * @param previous   smoothed velocity so far (deg/ms)
     * @param deltaDeg   signed angle moved since the last sample
     * @param deltaMs    time since the last sample
     */
    public static float trackVelocity(float previous, float deltaDeg, long deltaMs) {
        if (deltaMs <= 0) return previous;
        float instant = deltaDeg / (float) deltaMs;
        return previous + (instant - previous) * VELOCITY_SMOOTHING;
    }
}
