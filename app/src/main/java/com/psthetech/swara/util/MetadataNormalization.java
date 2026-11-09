package com.psthetech.swara.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.Song;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Centralized utility for normalizing song metadata, handling missing values,
 * and determining which metadata fields are incomplete.
 */
public final class MetadataNormalization {

    private MetadataNormalization() {}

    private static final String[] UNKNOWN_STRINGS = {
            "unknown",
            "<unknown>",
            "unknown artist",
            "unknown album",
            "unknown song",
            "unknown genre",
            "untitled",
            "track",
            "none",
            "null"
    };

    /**
     * Normalizes a raw metadata string.
     * Returns null if the string is null, empty, whitespace-only, or matches known "unknown" tokens.
     */
    @Nullable
    public static String normalizeValue(@Nullable String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;

        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (String unknown : UNKNOWN_STRINGS) {
            if (lower.equals(unknown)) {
                return null;
            }
        }
        return trimmed;
    }

    /**
     * Returns a clean, displayable string, or a fallback if normalized value is missing.
     */
    @NonNull
    public static String getDisplayValue(@Nullable String raw, @NonNull String fallback) {
        String norm = normalizeValue(raw);
        return norm != null ? norm : fallback;
    }

    /**
     * Safe string for editing forms: empty string if missing/unknown, so user can easily enter fresh data.
     */
    @NonNull
    public static String getEditableValue(@Nullable String raw) {
        String norm = normalizeValue(raw);
        return norm != null ? norm : "";
    }

    // ===== Missing Field Checkers =====

    public static boolean isMissingTitle(@Nullable String title) {
        return normalizeValue(title) == null;
    }

    public static boolean isMissingArtist(@Nullable String artist) {
        return normalizeValue(artist) == null;
    }

    public static boolean isMissingAlbum(@Nullable String album) {
        return normalizeValue(album) == null;
    }

    public static boolean isMissingGenre(@Nullable String genre) {
        return normalizeValue(genre) == null;
    }

    public static boolean isMissingAnyCoreMetadata(@NonNull Song song) {
        return isMissingTitle(song.getTitle())
                || isMissingArtist(song.getArtist())
                || isMissingAlbum(song.getAlbum());
    }

    public static boolean isMissingAny(@NonNull Song song, @Nullable String genre) {
        return isMissingAnyCoreMetadata(song) || isMissingGenre(genre);
    }

    /**
     * Returns a list of human-readable tags describing which fields are missing.
     */
    @NonNull
    public static List<String> getMissingFieldTags(@Nullable String title,
                                                  @Nullable String artist,
                                                  @Nullable String album,
                                                  @Nullable String genre) {
        List<String> tags = new ArrayList<>();
        if (isMissingTitle(title)) tags.add("Title");
        if (isMissingArtist(artist)) tags.add("Artist");
        if (isMissingAlbum(album)) tags.add("Album");
        if (isMissingGenre(genre)) tags.add("Genre");
        return tags;
    }

    /**
     * Returns a formatted summary string of missing fields, e.g. "Missing: Artist, Album".
     */
    @NonNull
    public static String getMissingSummary(@NonNull List<String> tags) {
        if (tags.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("Missing: ");
        for (int i = 0; i < tags.size(); i++) {
            sb.append(tags.get(i));
            if (i < tags.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }

    // ===== Numeric Validation =====

    /**
     * Parses and validates year string. Returns -1 if invalid, 0 if empty/omitted.
     */
    public static int parseYear(@Nullable String yearStr) {
        if (yearStr == null || yearStr.trim().isEmpty()) return 0;
        try {
            int y = Integer.parseInt(yearStr.trim());
            if (y >= 1800 && y <= 2100) return y;
            return -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Parses and validates track number. Returns -1 if invalid, 0 if empty/omitted.
     */
    public static int parseTrackNumber(@Nullable String trackStr) {
        if (trackStr == null || trackStr.trim().isEmpty()) return 0;
        try {
            // Support "1" or "1/12"
            String cleaned = trackStr.trim();
            int slash = cleaned.indexOf('/');
            if (slash > 0) {
                cleaned = cleaned.substring(0, slash).trim();
            }
            int t = Integer.parseInt(cleaned);
            if (t > 0 && t <= 999) return t;
            return -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Parses and validates disc number. Returns -1 if invalid, 0 if empty/omitted.
     */
    public static int parseDiscNumber(@Nullable String discStr) {
        if (discStr == null || discStr.trim().isEmpty()) return 0;
        try {
            String cleaned = discStr.trim();
            int slash = cleaned.indexOf('/');
            if (slash > 0) {
                cleaned = cleaned.substring(0, slash).trim();
            }
            int d = Integer.parseInt(cleaned);
            if (d > 0 && d <= 99) return d;
            return -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
