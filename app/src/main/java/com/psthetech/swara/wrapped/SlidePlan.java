package com.psthetech.swara.wrapped;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/** Which slides a recap shows; a slide without enough data is skipped (spec §4). */
public final class SlidePlan {

    /** No recap or story below 30 minutes of listening. */
    public static final long MIN_TOTAL_MS = 30L * 60_000L;
    static final int MIN_TOP_ENTRIES = 3;
    static final int MIN_HABIT_DAYS = 3;

    private SlidePlan() {}

    @NonNull
    public static List<SlideType> plan(@NonNull WrappedStats s) {
        List<SlideType> out = new ArrayList<>();
        if (s.totalMs < MIN_TOTAL_MS) return out;
        boolean moodKnown = s.moodKnownMs * 2 >= s.totalMs;
        for (SlideType t : SlideType.values()) {
            boolean show;
            switch (t) {
                case TOP_SONGS: show = s.topSongIds.size() >= MIN_TOP_ENTRIES; break;
                case TOP_ARTISTS: show = s.topArtistKeys.size() >= MIN_TOP_ENTRIES; break;
                case TOP_ALBUM: show = s.topAlbumId != -1; break;
                case MOOD_MIX:
                case MOOD_OF_PERIOD: show = moodKnown && s.moodKnownMs > 0; break;
                case VIBE: show = s.vibeStarts > 0; break;
                case HABITS: show = s.listeningDays >= MIN_HABIT_DAYS; break;
                case REPEAT_SKIP: show = s.mostRepeatedSongId != -1 || s.mostSkippedSongId != -1; break;
                case DISCOVERY: show = s.newSongs > 0 || s.rediscoveredSongId != -1; break;
                default: show = true; // INTRO, MINUTES, PERSONALITY, SUMMARY
            }
            if (show) out.add(t);
        }
        return out;
    }
}
