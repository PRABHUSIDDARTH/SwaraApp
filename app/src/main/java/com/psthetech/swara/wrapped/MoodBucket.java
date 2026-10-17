package com.psthetech.swara.wrapped;

import androidx.annotation.Nullable;

/** Coarse mood of a song's music, used by the mood slides. Thresholds are spec §4. */
public enum MoodBucket {
    SAD, CALM, HAPPY, ENERGETIC;

    public static final double SAD_BELOW_VALENCE = 0.35;
    public static final double ENERGETIC_FROM_ENERGY = 0.70;
    public static final double HAPPY_FROM_VALENCE = 0.60;

    /** {@code mood} = {valence, energy}; null when unknown. */
    @Nullable
    public static MoodBucket of(@Nullable double[] mood) {
        if (mood == null || mood.length < 2 || Double.isNaN(mood[0]) || Double.isNaN(mood[1])) return null;
        if (mood[0] < SAD_BELOW_VALENCE) return SAD;
        if (mood[1] >= ENERGETIC_FROM_ENERGY) return ENERGETIC;
        if (mood[0] >= HAPPY_FROM_VALENCE) return HAPPY;
        return CALM;
    }
}
