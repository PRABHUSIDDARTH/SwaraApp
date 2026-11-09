package com.psthetech.swara.ui.playlists;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psthetech.swara.domain.model.Song;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure logic for turning a "For you" mix's song ids into the songs shown and saved. */
public final class MixSongs {

    /** Looks up one song by id; returns null when it is no longer in the library. */
    public interface SongLookup {
        @Nullable
        Song find(long id);
    }

    private MixSongs() {
    }

    /**
     * Resolves {@code ids} in mix order, skipping songs that are missing or whose lookup
     * throws, and keeping only the first occurrence of a repeated id.
     */
    @NonNull
    public static List<Song> resolveInOrder(@Nullable List<Long> ids, @NonNull SongLookup lookup) {
        List<Song> out = new ArrayList<>();
        if (ids == null) return out;
        Set<Long> seen = new HashSet<>();
        for (Long id : ids) {
            if (id == null || !seen.add(id)) continue;
            Song s;
            try {
                s = lookup.find(id);
            } catch (RuntimeException e) {
                s = null;
            }
            if (s != null) out.add(s);
        }
        return out;
    }
}
