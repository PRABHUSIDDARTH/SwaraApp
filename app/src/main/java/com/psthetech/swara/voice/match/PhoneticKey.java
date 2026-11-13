package com.psthetech.swara.voice.match;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Sound-alike key for matching spoken names against transliterated library tags.
 * Pure Java; never returns null ("" for null/blank input).
 */
public final class PhoneticKey {

    private PhoneticKey() {}

    @NonNull
    public static String of(@Nullable String input) {
        if (input == null) return "";
        String s = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z ]", "")
                .trim();
        if (s.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (String word : s.split(" +")) {
            String k = keyWord(word);
            if (k.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(k);
        }
        return out.toString();
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
        w = w.replaceAll("(?<=[bcdfghjklmnpqrstvwxyz])e(?=[bcdfghjklmnpqrstvwxyz])", "a");
        // Drop h after a consonant, collapse doubled letters, drop trailing h
        w = w.replaceAll("(?<=[bcdfghjklmnpqrstvwxyz])h", "");
        w = w.replaceAll("(.)\\1+", "$1");
        w = w.replaceAll("h$", "");
        // Trailing y sounds like i (Thalapathy / Talapati); minimal adjustment to brief rules
        w = w.replaceAll("y$", "i");
        return w;
    }
}
