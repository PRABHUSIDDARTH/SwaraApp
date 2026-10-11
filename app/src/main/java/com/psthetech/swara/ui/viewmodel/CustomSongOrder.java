package com.psthetech.swara.ui.viewmodel;

import com.psthetech.swara.domain.model.Song;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The user's hand-arranged song order (drag to reorder in the Songs tab).
 *
 * Stored as a comma-separated list of song IDs. Songs missing from the saved order
 * (e.g. newly added files) are appended after the arranged ones, newest first.
 */
public final class CustomSongOrder {

    private CustomSongOrder() { }

    public static String encode(List<Song> songs) {
        StringBuilder sb = new StringBuilder();
        if (songs == null) return "";
        for (Song s : songs) {
            if (sb.length() > 0) sb.append(',');
            sb.append(s.getId());
        }
        return sb.toString();
    }

    public static List<Long> decode(String raw) {
        List<Long> ids = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return ids;
        for (String part : raw.split(",")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // Skip corrupt entries rather than discarding the whole order.
            }
        }
        return ids;
    }

    /** Returns a new list: saved order first, then unknown songs (newest added first). */
    public static List<Song> apply(List<Song> songs, List<Long> orderIds) {
        if (songs == null) return Collections.emptyList();
        Map<Long, Integer> rank = new HashMap<>();
        if (orderIds != null) {
            for (int i = 0; i < orderIds.size(); i++) rank.putIfAbsent(orderIds.get(i), i);
        }
        List<Song> result = new ArrayList<>(songs);
        result.sort((a, b) -> {
            Integer ra = rank.get(a.getId());
            Integer rb = rank.get(b.getId());
            if (ra != null && rb != null) return Integer.compare(ra, rb);
            if (ra != null) return -1;
            if (rb != null) return 1;
            return Long.compare(b.getDateAdded(), a.getDateAdded());
        });
        return result;
    }
}
