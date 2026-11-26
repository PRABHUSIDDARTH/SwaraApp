package com.psthetech.swara.ui.deck;

/** Pure mapping between a playback value and the position of the fader cap. */
public final class DeckFaderMath {

    private DeckFaderMath() { }

    /** {@code progress / max} clamped to [0, 1]; 0 when there is no range. */
    public static float fraction(long progress, long max) {
        if (max <= 0) return 0f;
        return Math.max(0f, Math.min(1f, (float) progress / (float) max));
    }

    /** Centre of the cap for a fraction, with the cap travelling from {@code left} to {@code right}. */
    public static float capCentre(float fraction, float left, float right) {
        return left + Math.max(0f, right - left) * fraction;
    }
}
