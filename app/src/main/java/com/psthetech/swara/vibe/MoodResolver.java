package com.psthetech.swara.vibe;

import androidx.annotation.Nullable;

/**
 * The single place that decides which mood a song has: a user override always wins over the
 * analyzed mood. Pure Java.
 */
public final class MoodResolver {

    private MoodResolver() {}

    /**
     * Returns a fresh {valence, energy}: the override's values if set, else a copy of
     * {@code analyzed}, else null (unknown mood).
     */
    @Nullable
    public static double[] resolve(@Nullable double[] analyzed, @Nullable MoodLabel override) {
        if (override != null) return override.mood();
        return analyzed == null ? null : analyzed.clone();
    }
}
