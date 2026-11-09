package com.psthetech.swara.wrapped;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Pattern;

/**
 * Removes download-site tags from titles and artists for display
 * ("Kannukulla - MassTamilan.dev" → "Kannukulla"). Case is kept.
 */
public final class TitleCleaner {

    private static final String SITES =
            "masstamilan|pagalworldl?|isongs|starmusiq|tamilwire|kuttyweb|tamiltunes|"
                    + "orutamilsong|fun2desi|koshalworld|djmaza|gospejingle|mrjatt|naasongs";

    /** A trailing "- site.tld", "(site.Com)", "[site]" or bare site name. */
    private static final Pattern TRAILING = Pattern.compile(
            "\\s*[-–]?\\s*[(\\[]?\\s*(?:[\\w-]*(?:" + SITES + ")[\\w-]*(?:\\.[a-z]{2,6})?"
                    + "|[\\w-]+\\.(?:com|dev|so|io|org|fm|my|info|net|in|co))\\s*[)\\]]?\\s*$",
            Pattern.CASE_INSENSITIVE);
    /** A leading "[site.info]" tag. */
    private static final Pattern LEADING_TAG = Pattern.compile("^\\s*\\[[^\\]]*\\]\\s*");
    /** A leading track number such as "02 - ". */
    private static final Pattern LEADING_TRACK = Pattern.compile("^\\d{1,3}\\s*[-.]\\s+");

    private TitleCleaner() {}

    @NonNull
    public static String clean(@Nullable String s) {
        if (s == null) return "";
        String out = s.trim();
        String prev;
        do {
            prev = out;
            out = LEADING_TAG.matcher(out).replaceFirst("");
            out = LEADING_TRACK.matcher(out).replaceFirst("");
            out = TRAILING.matcher(out).replaceFirst("").trim();
        } while (!out.equals(prev));
        return out;
    }
}
