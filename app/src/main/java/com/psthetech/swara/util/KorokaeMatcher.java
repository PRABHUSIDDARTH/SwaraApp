package com.psthetech.swara.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.psthetech.swara.domain.model.Song;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic matching engine for finding local karaoke and instrumental tracks.
 * Keyword indicator recognition and normalization.
 */
public final class KorokaeMatcher {

    private KorokaeMatcher() { /* static only */ }

    private static final String[] KARAOKE_INDICATORS = {
            "karaoke version",
            "instrumental version",
            "karaoke",
            "instrumental",
            "minus one",
            "backing track",
            "music only",
            "伴奏"
    };

    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[\\p{Punct}&&[^\\w\\s]]");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");

    public static boolean hasKaraokeIndicator(@Nullable String normalizedText) {
        if (normalizedText == null || normalizedText.isEmpty()) return false;
        for (String ind : KARAOKE_INDICATORS) {
            if (normalizedText.contains(ind)) return true;
        }
        return false;
    }

    public static String normalizeString(@Nullable String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        normalized = PUNCTUATION_PATTERN.matcher(normalized).replaceAll(" ");
        normalized = MULTI_SPACE_PATTERN.matcher(normalized).replaceAll(" ");
        return normalized.trim().toLowerCase(Locale.ROOT);
    }
}
