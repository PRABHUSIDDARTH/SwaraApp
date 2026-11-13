package com.psthetech.swara.voice.match;

import androidx.annotation.NonNull;

import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.util.ArtistAliasResolver;
import com.psthetech.swara.util.ArtistIdentityHelper;
import com.psthetech.swara.util.ArtistNameTokens;
import com.psthetech.swara.util.MetadataNormalization;
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
        /** Display name as the scorer sees it (album noise stripped), for exact-title comparison. */
        final String compareName;
        final String fullKey;
        final List<String> tokens;
        final List<Song> songs;
        /** Artist initials (e.g. "a", "r", "ar") that queries may omit or spell either way. */
        final Set<String> initials;
        /** Merged artists only: token lists of each credit spelling, so "Anirudh" fully matches. */
        List<List<String>> variantTokens = Collections.emptyList();

        Entry(MatchKind kind, String key, String display, String detail,
              List<String> tokens, List<Song> songs, Set<String> initials) {
            this.kind = kind;
            this.key = key;
            this.display = display;
            this.detail = detail;
            this.compareName = kind == MatchKind.ALBUM ? TagNoise.stripAlbumNoise(display) : display;
            this.tokens = tokens;
            this.fullKey = String.join(" ", tokens);
            this.songs = songs;
            this.initials = initials;
        }
    }


    private final List<Entry> entries;

    private LibraryIndex(List<Entry> entries) {
        this.entries = entries;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
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
        return build(library, ArtistAliasResolver.identity());
    }

    @NonNull
    public static LibraryIndex build(List<Song> library, @NonNull ArtistAliasResolver resolver) {
        List<Entry> entries = new ArrayList<>();
        if (library == null) return new LibraryIndex(entries);

        Map<Long, List<Song>> albums = new LinkedHashMap<>();
        // Artists group by the letters of the canonical key, so "A.R." / "A. R." / "AR" collapse.
        Map<String, List<Song>> artistSongs = new LinkedHashMap<>();
        Map<String, Map<Long, Song>> artistDedup = new HashMap<>();
        Map<String, Map<String, Integer>> artistVariants = new HashMap<>();
        Map<String, String> mergedGroups = new HashMap<>();   // group key -> resolved artist key

        for (Song s : library) {
            if (s == null) continue;
            List<String> t = tokensOf(s.getTitle());
            entries.add(new Entry(MatchKind.SONG, String.valueOf(s.getId()), s.getTitle(),
                    s.getArtist() + (s.getAlbum() == null || s.getAlbum().isEmpty() ? "" : " · " + s.getAlbum()),
                    t, Collections.singletonList(s), Collections.emptySet()));

            if (s.getAlbumId() > 0 && !MetadataNormalization.isMissingAlbum(s.getAlbum())) {
                albums.computeIfAbsent(s.getAlbumId(), k -> new ArrayList<>()).add(s);
            }

            for (String name : ArtistIdentityHelper.extractArtists(s.getArtist())) {
                String canon = ArtistIdentityHelper.getCanonicalKey(name);
                if (canon.equals("unknown artist")) continue;
                String rk = resolver.resolve(canon);
                // Merged credits group by the resolved key; unmerged ones keep the token key.
                String gk = groupKey(name);
                if (!rk.equals(canon)) {
                    String target = resolver.displayName(rk);
                    gk = groupKey(target.equals(rk) ? rk : target);
                    mergedGroups.put(gk, rk);
                }
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
                    albumDetail(songs), tokens, songs, Collections.emptySet()));
        }

        for (Map.Entry<String, List<Song>> e : artistSongs.entrySet()) {
            Map<String, Integer> variants = artistVariants.get(e.getKey());
            String display = mostFrequent(variants);
            String mergedKey = mergedGroups.get(e.getKey());
            if (mergedKey != null) {
                String d = resolver.displayName(mergedKey);
                if (!d.equals(mergedKey)) display = d;
            }
            Set<String> initials = new LinkedHashSet<>();
            List<String> tokens = artistTokens(variants.keySet(), initials);
            if (tokens.isEmpty()) continue;
            Entry entry = new Entry(MatchKind.ARTIST, e.getKey(), display,
                    e.getValue().size() + " songs", tokens, e.getValue(), initials);
            if (mergedKey != null) {
                List<List<String>> vts = new ArrayList<>();
                for (String v : variants.keySet()) {
                    List<String> vt = artistTokens(Collections.singleton(v), new LinkedHashSet<>());
                    if (!vt.isEmpty()) vts.add(vt);
                }
                entry.variantTokens = vts;
            }
            entries.add(entry);
        }
        return new LibraryIndex(entries);
    }

    /** Group key: initials runs collapse ("a.r." / "a. r." / "AR" -> "ar"); other words keep boundaries. */
    static String groupKey(String name) {
        String k = String.join(" ", ArtistNameTokens.tokenList(name));
        return k.isEmpty() ? name : k;
    }

    /** Significant tokens of an artist group; fills {@code initials} with ignorable initial forms. */
    private static List<String> artistTokens(Set<String> variants, Set<String> initials) {
        List<List<String>> wordLists = new ArrayList<>();
        for (String v : variants) {
            List<String> ws = ArtistNameTokens.words(v);
            wordLists.add(ws);
            StringBuilder run = new StringBuilder();
            for (String w : ws) {
                if (ArtistNameTokens.isInitial(w)) {
                    initials.add(ArtistNameTokens.plain(w));
                    run.append(ArtistNameTokens.plain(w));
                } else {
                    if (run.length() > 1) initials.add(run.toString());
                    run.setLength(0);
                }
            }
            if (run.length() > 1) initials.add(run.toString());
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (List<String> ws : wordLists) {
            for (String w : ws) {
                if (ArtistNameTokens.isInitial(w) || initials.contains(w)) continue;
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
            List<String> credited = ArtistIdentityHelper.extractArtists(s.getArtist());
            if (!credited.isEmpty()) artists.merge(credited.get(0), 1, Integer::sum);
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
