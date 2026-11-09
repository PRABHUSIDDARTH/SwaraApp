package com.psthetech.swara.vibe;

import androidx.annotation.Nullable;

/** A song eligible for recommendation, with precomputed features. */
public final class Candidate {
    public final long songId;
    public final String artistKey;
    /** {valence, energy}, or null if unknown. */
    @Nullable
    public final double[] mood;
    /** 0 if unknown. */
    public final double tempoBpm;
    /** 0 if never played. */
    public final long lastPlayedMs;
    /** TasteProfile.combined, in [0, 1]. */
    public final double taste;

    public Candidate(long songId, String artistKey, @Nullable double[] mood, double tempoBpm,
                     long lastPlayedMs, double taste) {
        this.songId = songId;
        this.artistKey = artistKey;
        this.mood = mood;
        this.tempoBpm = tempoBpm;
        this.lastPlayedMs = lastPlayedMs;
        this.taste = taste;
    }
}
