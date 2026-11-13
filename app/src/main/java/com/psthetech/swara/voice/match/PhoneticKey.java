package com.psthetech.swara.voice.match;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Sound-alike key for matching spoken names against transliterated library tags.
 * Pure Java; never returns null ("" for null/blank input).
 */
public final class PhoneticKey {

    private static final String CONS = "[bcdfghjklmnpqrstvwxyz]";
    private static final Pattern MARKS = Pattern.compile("\\p{M}");
    private static final Pattern NON_ALPHA = Pattern.compile("[^a-z ]");
    private static final Pattern SPACES = Pattern.compile(" +");
    private static final Pattern E_BETWEEN_CONS = Pattern.compile("(?<=" + CONS + ")e(?=" + CONS + ")");
    private static final Pattern H_AFTER_CONS = Pattern.compile("(?<=" + CONS + ")h");
    private static final Pattern DOUBLED = Pattern.compile("(.)\\1+");
    private static final Pattern TRAILING_H = Pattern.compile("h$");
    private static final Pattern FINAL_H = Pattern.compile("h$");
    private static final Pattern TRAILING_Y = Pattern.compile("y$");

    private PhoneticKey() {}

    @NonNull
    public static String of(@Nullable String input) {
        if (input == null) return "";
        String s = Normalizer.normalize(input, Normalizer.Form.NFD);
        s = MARKS.matcher(s).replaceAll("");
        s = NON_ALPHA.matcher(s.toLowerCase(Locale.ROOT)).replaceAll("").trim();
        if (s.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (String word : SPACES.split(s)) {
            String k = keyWord(word);
            if (k.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(k);
        }
        return out.toString();
    }

    /** Key with the word breaks removed, for matching run-together / split-up speech. */
    @NonNull
    public static String joined(@Nullable String key) {
        return key == null ? "" : key.replace(" ", "");
    }

    private static String keyWord(String w) {
        // Digraphs
        w = w.replace("zh", "l").replace("th", "t").replace("dh", "d")
                .replace("ph", "f").replace("sh", "s").replace("ch", "c")
                .replace("kh", "k").replace("gh", "g").replace("bh", "b")
                .replace('w', 'v');
        // Vowels
        w = w.replace("aa", "a").replace("ee", "i").replace("ii", "i")
                .replace("oo", "u").replace("uu", "u").replace("ai", "e");
        w = E_BETWEEN_CONS.matcher(w).replaceAll("a");
        // Drop h after a consonant, collapse doubled letters, drop trailing h
        w = H_AFTER_CONS.matcher(w).replaceAll("");
        w = DOUBLED.matcher(w).replaceAll("$1");
        w = TRAILING_H.matcher(w).replaceAll("");
        // Trailing y sounds like i (Thalapathy / Talapati); minimal adjustment to brief rules
        w = TRAILING_Y.matcher(w).replaceAll("i");
        // Indian-name folding (applied last): Anirudh / An-erode / Anirud sound alike
        w = w.replace('e', 'i').replace('o', 'u');
        w = FINAL_H.matcher(w).replaceAll("");
        return w;
    }
}
