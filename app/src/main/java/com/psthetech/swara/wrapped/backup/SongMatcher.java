package com.psthetech.swara.wrapped.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psthetech.swara.wrapped.SongInfo;
import com.psthetech.swara.wrapped.TitleCleaner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Finds imported songs in this phone's library by title and artist, since MediaStore ids differ
 * between phones. Songs that are not here get a stable synthetic id below -1 so their history
 * still counts.
 */
public final class SongMatcher {

    private SongMatcher() {}

    @NonNull
    public static String normalize(@Nullable String s) {
        return TitleCleaner.clean(s).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{M}\\p{N}]+", " ").trim();
    }

    @NonNull
    public static String key(@Nullable String title, @Nullable String artist) {
        return normalize(title) + "|" + normalize(artist);
    }

    /** Negative, never -1 or 0, and the same for the same key on every phone. */
    public static long syntheticId(@NonNull String key) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < key.length(); i++) {
            h ^= key.charAt(i);
            h *= 0x100000001b3L;
        }
        return -2L - ((h & Long.MAX_VALUE) % 1_000_000_000_000_000L);
    }

    /** Imported song id → local song id (or a synthetic id when not on this phone). */
    @NonNull
    public static Map<Long, Long> match(@NonNull List<SongInfo> imported, @NonNull List<SongInfo> local) {
        Map<String, List<SongInfo>> byKey = new HashMap<>();
        for (SongInfo s : local) {
            String k = key(s.title, s.artist);
            List<SongInfo> l = byKey.get(k);
            if (l == null) byKey.put(k, l = new ArrayList<>());
            l.add(s);
        }
        Map<Long, Long> out = new HashMap<>();
        for (SongInfo s : imported) {
            String k = key(s.title, s.artist);
            List<SongInfo> candidates = byKey.get(k);
            out.put(s.songId, candidates == null ? syntheticId(k) : pick(candidates, s.album).songId);
        }
        return out;
    }

    private static SongInfo pick(List<SongInfo> candidates, @Nullable String album) {
        String a = normalize(album);
        for (SongInfo c : candidates) {
            if (normalize(c.album).equals(a)) return c;
        }
        return candidates.get(0);
    }
}
