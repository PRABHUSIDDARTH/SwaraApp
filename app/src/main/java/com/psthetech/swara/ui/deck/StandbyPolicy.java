package com.psthetech.swara.ui.deck;

/**
 * When the standby screen should close, and how it moves to spread screen wear. Pure maths;
 * times are elapsed-realtime milliseconds, so clock changes do not matter.
 */
public final class StandbyPolicy {

    /** Close after this long without a touch. */
    public static final long IDLE_LIMIT_MS = 15 * 60_000L;
    /** Close after this long paused. */
    public static final long PAUSE_LIMIT_MS = 60_000L;

    private StandbyPolicy() { }

    /** @param pausedSinceMs when playback paused, or negative while playing */
    public static boolean shouldClose(long nowMs, long lastTouchMs, long pausedSinceMs) {
        if (nowMs - lastTouchMs >= IDLE_LIMIT_MS) return true;
        // A touch while paused restarts the countdown.
        return pausedSinceMs >= 0
                && nowMs - Math.max(pausedSinceMs, lastTouchMs) >= PAUSE_LIMIT_MS;
    }

    /** Horizontal shift for a given minute, in -max..max. */
    public static int shiftX(long minute, int max) {
        return wave(minute, 6, max);
    }

    /** Vertical shift; a different period from x, so the pattern covers an area. */
    public static int shiftY(long minute, int max) {
        return wave(minute, 10, max);
    }

    /** A triangle wave between -max and max that starts at 0; {@code steps} (even) each way. */
    private static int wave(long minute, int steps, int max) {
        long period = 2L * steps;
        long at = (((minute + steps / 2) % period) + period) % period;
        long up = at <= steps ? at : period - at;
        return Math.round(max * (2f * up / steps - 1f));
    }
}
