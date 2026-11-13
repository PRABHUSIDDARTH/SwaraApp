package com.psthetech.swara.voice.match;

import androidx.annotation.NonNull;

import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.util.ArtistIdentityHelper;
import com.psthetech.swara.util.TagNoise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Phonetic keys for every song, artist and album, computed once per library. */
public final class LibraryIndex {

    /** One indexable entity. Tokens exclude single letters / artist initials. */
    static final class Entry {
        final MatchKind kind;
        final String key;
        final String display;
        final String detail;
        final String fullKey;
        final List<String> tokens;
        final List<Song> songs;

        Entry(MatchKind kind, String key, String display, String detail,
              List<String> tokens, List<Song> songs) {
            this.kind = kind;
            this.key = key;
            this.display = display;
            this.detail = detail;
            this.tokens = tokens;
            this.fullKey = String.join(" ", tokens);
            this.songs = songs;
        }
    }

    private static final Pattern WORD_SPLIT = Pattern.compile("[.\\s]+");
    private static final Pattern NON_LETTER = Pattern.compile("[^a-z]");

    private final List<Entry> entries;

    private LibraryIndex(List<Entry> entries) {
        this.entries = entries;
    }

    List<Entry> entries() {
        return entries;
    }

    /** Significant (multi-letter) phonetic tokens of a phrase. */
    @NonNull
    static List<String> tokensOf(String phrase) {
        String key = PhoneticKey.of(phrase);
        List<String> out = new ArrayList<>();
        if (key.isEmpty()) return out;
        for (String t : key.split(" ")) {
            if (t.length() > 1) out.add(t);
        }
        return out;
    }

    @NonNull
    public static LibraryIndex build(List<Song> library) {
        List<Entry> entries = new ArrayList<>();
        if (library == null) return new LibraryIndex(entries);

        Map<Long, List<Song>> albums = new LinkedHashMap<>();
        // Artists group by the letters of the canonical key, so "A.R." / "A. R." / "AR" collapse.
        Map<String, List<Song>> artistSongs = new LinkedHashMap<>();
        Map<String, Map<Long, Song>> artistDedup = new HashMap<>();
        Map<String, Map<String, Integer>> artistVariants = new HashMap<>();

        for (Song s : library) {
            if (s == null) continue;
            List<String> t = tokensOf(s.getTitle());
            entries.add(new Entry(MatchKind.SONG, String.valueOf(s.getId()), s.getTitle(),
                    s.getArtist() + (s.getAlbum() == null || s.getAlbum().isEmpty() ? "" : " · " + s.getAlbum()),
                    t, Collections.singletonList(s)));

            albums.computeIfAbsent(s.getAlbumId(), k -> new ArrayList<>()).add(s);

            for (String name : ArtistIdentityHelper.extractArtists(s.getArtist())) {
                String canon = ArtistIdentityHelper.getCanonicalKey(name);
                if (canon.equals("unknown artist")) continue;
                String gk = NON_LETTER.matcher(canon).replaceAll("");
                if (gk.isEmpty()) gk = canon;
                Map<Long, Song> dedup = artistDedup.computeIfAbsent(gk, k -> new LinkedHashMap<>());
                if (dedup.putIfAbsent(s.getId(), s) == null) {
                    artistSongs.computeIfAbsent(gk, k -> new ArrayList<>()).add(s);
                }
                artistVariants.computeIfAbsent(gk, k -> new LinkedHashMap<>()).merge(name, 1, Integer::sum);
            }
        }

        for (Map.Entry<Long, List<Song>> e : albums.entrySet()) {
            List<Song> songs = e.getValue();
            String name = songs.get(0).getAlbum();
            List<String> tokens = tokensOf(TagNoise.stripAlbumNoise(name));
            if (tokens.isEmpty()) continue;
            entries.add(new Entry(MatchKind.ALBUM, String.valueOf(e.getKey()), name,
                    albumDetail(songs), tokens, songs));
        }

        for (Map.Entry<String, List<Song>> e : artistSongs.entrySet()) {
            Map<String, Integer> variants = artistVariants.get(e.getKey());
            String display = mostFrequent(variants);
            List<String> tokens = artistTokens(variants.keySet());
            if (tokens.isEmpty()) continue;
            entries.add(new Entry(MatchKind.ARTIST, e.getKey(), display,
                    e.getValue().size() + " songs", tokens, e.getValue()));
        }
        return new LibraryIndex(entries);
    }

    /**
     * Tokens of an artist group, ignoring initials. A word is an initial if it is a single letter
     * in any variant, or the concatenation of a run of single letters in any variant (so the
     * "AR" of "AR Rahman" is ignored once "A.R. Rahman" is in the same group).
     */
    private static List<String> artistTokens(Set<String> variants) {
        Set<String> ignorable = new LinkedHashSet<>();
        List<List<String>> wordLists = new ArrayList<>();
        for (String v : variants) {
            List<String> words = new ArrayList<>();
            for (String w : WORD_SPLIT.split(v.toLowerCase(Locale.ROOT))) {
                w = NON_LETTER.matcher(w).replaceAll("");
                if (!w.isEmpty()) words.add(w);
            }
            wordLists.add(words);
            StringBuilder run = new StringBuilder();
            for (String w : words) {
                if (w.length() == 1) {
                    ignorable.add(w);
                    run.append(w);
                } else {
                    if (run.length() > 1) ignorable.add(run.toString());
                    run.setLength(0);
                }
            }
            if (run.length() > 1) ignorable.add(run.toString());
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (List<String> words : wordLists) {
            for (String w : words) {
                if (w.length() == 1 || ignorable.contains(w)) continue;
                String k = PhoneticKey.of(w);
                if (k.length() > 1) tokens.add(k);
            }
        }
        return new ArrayList<>(tokens);
    }

    private static String albumDetail(List<Song> songs) {
        Map<String, Integer> artists = new LinkedHashMap<>();
        Map<Integer, Integer> years = new LinkedHashMap<>();
        for (Song s : songs) {
            artists.merge(ArtistIdentityHelper.extractArtists(s.getArtist()).get(0), 1, Integer::sum);
            if (s.getYear() > 0) years.merge(s.getYear(), 1, Integer::sum);
        }
        String artist = mostFrequent(artists);
        int year = 0, best = 0;
        for (Map.Entry<Integer, Integer> y : years.entrySet()) {
            if (y.getValue() > best) { best = y.getValue(); year = y.getKey(); }
        }
        return year > 0 ? artist + " · " + year : artist;
    }

    private static String mostFrequent(Map<String, Integer> counts) {
        String best = "";
        int n = -1;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > n) { n = e.getValue(); best = e.getKey(); }
        }
        return best;
    }
}
