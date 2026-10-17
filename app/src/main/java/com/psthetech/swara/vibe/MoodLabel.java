package com.psthetech.swara.vibe;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A mood the user picked for a song ("This song feels…"). Each label maps to a fixed
 * {valence, energy} point in the same [0, 1] space as the analyzed moods. Stored by
 * {@link #name()}, so constants must never be renamed.
 */
public enum MoodLabel {
    SAD(0.15, 0.25),
    CALM(0.50, 0.25),
    HAPPY(0.80, 0.60),
    ENERGETIC(0.85, 0.90);

    public final double valence;
    public final double energy;

    MoodLabel(double valence, double energy) {
        this.valence = valence;
        this.energy = energy;
    }

    /** A fresh {valence, energy} array. */
    @NonNull
    public double[] mood() {
        return new double[]{valence, energy};
    }

    /** The label stored as {@code name}, or null when unknown (e.g. written by a newer app). */
    @Nullable
    public static MoodLabel fromName(@Nullable String name) {
        if (name == null) return null;
        for (MoodLabel l : values()) {
            if (l.name().equals(name)) return l;
        }
        return null;
    }
}
