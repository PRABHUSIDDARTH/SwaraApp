package com.psthetech.swara.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Shared word/initials tokenisation of artist names, used by the voice LibraryIndex and
 * ArtistAliasResolver so both agree on what "A.R. Rahman" / "AR Rahman" mean.
 *
 * Words are lower-cased runs of letters, combining marks and digits (so Devanagari and Tamil
 * vowel signs survive). Initials are a Latin-only concept: a single Latin letter ("A." "R."), or
 * a leading all-caps 2-letter Latin word followed by a longer word ("AR Rahman"). Initials are
 * marked with a leading {@link #INITIAL_MARK}; use {@link #isInitial} and {@link #plain}.
 */
public final class ArtistNameTokens {

    private static final char INITIAL_MARK = '\u0002';
    private static final Pattern WORD_SPLIT = Pattern.compile("[.\\s]+");
    private static final Pattern NON_TOKEN = Pattern.compile("[^\\p{L}\\p{M}\\p{N}]");
    private static final Pattern SINGLE_LATIN = Pattern.compile("[A-Za-z]");
    private static final Pattern CAPS_PAIR = Pattern.compile("[A-Z]{2}");

    private ArtistNameTokens() {}

    /** Words of a name with initials marked. */
    @NonNull
    public static List<String> words(@Nullable String name) {
        List<String> out = new ArrayList<>();
        if (name == null) return out;
        boolean leadingCapsPair = false;
        for (String w : WORD_SPLIT.split(name.trim())) {
            String letters = NON_TOKEN.matcher(w.toLowerCase(Locale.ROOT)).replaceAll("");
            if (letters.isEmpty()) continue;
            if (SINGLE_LATIN.matcher(w).matches()) {
                out.add(INITIAL_MARK + letters);
            } else {
                if (out.isEmpty() && CAPS_PAIR.matcher(w).matches()) leadingCapsPair = true;
                out.add(letters);
            }
        }
        if (leadingCapsPair && out.size() > 1 && !isInitial(out.get(1)) && out.get(1).length() > 2) {
            out.set(0, INITIAL_MARK + out.get(0));
        }
        return out;
    }

    public static boolean isInitial(@NonNull String word) {
        return !word.isEmpty() && word.charAt(0) == INITIAL_MARK;
    }

    @NonNull
    public static String plain(@NonNull String word) {
        return isInitial(word) ? word.substring(1) : word;
    }

    /** Tokens with each run of initials collapsed into one token ("a.r." / "AR" -> "ar"). */
    @NonNull
    public static List<String> tokenList(@Nullable String name) {
        List<String> out = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        for (String w : words(name)) {
            if (isInitial(w)) {
                run.append(plain(w));
            } else {
                if (run.length() > 0) { out.add(run.toString()); run.setLength(0); }
                out.add(w);
            }
        }
        if (run.length() > 0) out.add(run.toString());
        return out;
    }

    /** True when the name has no word besides initials ("A.R.", "AR" alone, empty). */
    public static boolean isInitialsOnly(@Nullable String name) {
        List<String> ws = words(name);
        for (String w : ws) if (!isInitial(w)) return false;
        return true;
    }
}
