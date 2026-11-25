package com.psthetech.swara.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Pattern;

/** Shared stripping of junk/noise from music tags. */
public final class TagNoise {

    // Common junk suffix tags like " - MassTamilan.com", " - MassTa...", " [MassTamilan]", web URLs, etc.
    public static final Pattern JUNK_TAGS_PATTERN = Pattern.compile(
            "(?i)\\s*-\\s*(?:masstamilan|massta|sensongs|starmusiq|isaimini|tamiltunes|songspk|www\\.[^,]+|[^,]+\\.com|[^,]+\\.net|[^,]+\\.org)[^,]*"
    );

    private static final Pattern BRACKETED_NOISE = Pattern.compile(
            "(?i)\\s*[(\\[][^)\\]]*\\b(?:from|original|motion picture|soundtrack|ost|score|deluxe|remaster(?:ed)?)\\b[^)\\]]*[)\\]]");
    private static final Pattern TRAILING_NOISE = Pattern.compile(
            "(?i)\\s+(?:original motion picture soundtrack|motion picture soundtrack|original soundtrack|soundtrack|ost)\\s*$");

    private TagNoise() {}

    @NonNull
    public static String stripAlbumNoise(@Nullable String album) {
        if (album == null) return "";
        String s = JUNK_TAGS_PATTERN.matcher(album).replaceAll("");
        s = BRACKETED_NOISE.matcher(s).replaceAll("");
        s = KorokaeMatcher.normalizeString(s);
        s = TRAILING_NOISE.matcher(s).replaceAll("");
        return s.trim();
    }
}
