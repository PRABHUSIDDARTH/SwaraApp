package com.psthetech.swara.wrapped;

import androidx.annotation.NonNull;

/** Listener type for the Personality slide. Rules are checked in declaration order (spec §4). */
public enum Personality {
    NIGHT_OWL, EARLY_RISER, MOOD_LIFTER, LOYALIST, EXPLORER, ENERGY_SEEKER, BALANCED;

    static final double NIGHT_SHARE = 0.40;
    static final double MORNING_SHARE = 0.40;
    static final int MOOD_LIFTER_VIBE_STARTS = 5;
    static final double LOYALIST_SHARE = 0.15;
    static final double EXPLORER_NEW_SHARE = 0.30;
    static final double ENERGY_SHARE = 0.40;

    @NonNull
    public static Personality of(@NonNull WrappedStats s) {
        if (s.totalMs > 0 && s.nightMs >= NIGHT_SHARE * s.totalMs) return NIGHT_OWL;
        if (s.totalMs > 0 && s.morningMs >= MORNING_SHARE * s.totalMs) return EARLY_RISER;
        if (s.vibeStarts >= MOOD_LIFTER_VIBE_STARTS) return MOOD_LIFTER;
        if (s.totalMs > 0 && s.topSongMs >= LOYALIST_SHARE * s.totalMs) return LOYALIST;
        if (s.distinctSongs > 0 && s.newSongs >= EXPLORER_NEW_SHARE * s.distinctSongs) return EXPLORER;
        Long energetic = s.moodMs.get(MoodBucket.ENERGETIC);
        if (s.moodKnownMs > 0 && energetic != null && energetic >= ENERGY_SHARE * s.moodKnownMs) {
            return ENERGY_SEEKER;
        }
        return BALANCED;
    }
}
