package com.psthetech.swara.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psthetech.swara.domain.model.Artist;
import com.psthetech.swara.domain.model.Song;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Centralized helper for artist identity normalization, compound credit parsing,
 * canonical grouping, and deduplication across Swara.
 *
 * Requirements:
 * 1. Normalize whitespace and obvious punctuation spacing (e.g. "A. R. Rahman" vs "A.R. Rahman").
 * 2. Handle common multi-artist separators (, & feat. featuring ft. / ;).
 * 3. Extract individual artist credits conservatively without splitting legitimate artist names.
 * 4. Produce a canonical grouping key so variant credits collapse to a single artist identity.
 * 5. Preserve clean display names while ensuring a song with multiple artists contributes to each artist.
 */
public final class ArtistIdentityHelper {

    // Regex for featuring credits inside parentheses or brackets: (feat. XYZ) or [featuring XYZ]
    private static final Pattern PAREN_FEAT_PATTERN = Pattern.compile(
            "(?i)[\\(\\[]\\s*(?:feat\\.?|featuring|ft\\.?)\\s+([^\\]\\)]+)[\\)\\]]"
    );

    // Regex for inline featuring separators: "Artist A feat. Artist B"
    private static final Pattern INLINE_FEAT_PATTERN = Pattern.compile(
            "(?i)\\s+(?:featuring|feat\\.?|ft\\.?)\\s+"
    );

    // Regex for comma separator (handles with or without space: "A, B" or "A,B")
    private static final Pattern COMMA_PATTERN = Pattern.compile("\\s*,\\s*");

    // Regex for semicolon or slash separators (with whitespace or between distinct words)
    private static final Pattern SEMICOLON_OR_SLASH_PATTERN = Pattern.compile("\\s*[;]\\s*|\\s+[/\\\\]\\s+");

    // Regex for ampersand separator when flanked by spaces: "Artist A & Artist B"
    private static final Pattern AMPERSAND_PATTERN = Pattern.compile("\\s+&\\s+");

    // Common junk suffix tags like " - MassTamilan.com", " - MassTa...", " [MassTamilan]", web URLs, etc.
    private static final Pattern JUNK_TAGS_PATTERN = Pattern.compile(
            "(?i)\\s*-\\s*(?:masstamilan|massta|sensongs|starmusiq|isaimini|tamiltunes|songspk|www\\.[^,]+|[^,]+\\.com|[^,]+\\.net|[^,]+\\.org)[^,]*"
    );

    private ArtistIdentityHelper() {}

    /**
     * Extracts individual, cleaned artist names from a raw artist string.
     * Example:
     *   "A.R. Rahman, Shreya Ghoshal" -> ["A.R. Rahman", "Shreya Ghoshal"]
     *   "A. R. Rahman,Shreya Ghoshal" -> ["A.R. Rahman", "Shreya Ghoshal"]
     *   "Eminem feat. Rihanna" -> ["Eminem", "Rihanna"]
     *   "Daft Punk featuring Pharrell Williams" -> ["Daft Punk", "Pharrell Williams"]
     */
    @NonNull
    public static List<String> extractArtists(@Nullable String rawArtist) {
        if (rawArtist == null || rawArtist.trim().isEmpty()) {
            return Collections.singletonList("Unknown Artist");
        }

        String cleaned = rawArtist.trim();
        if (cleaned.equalsIgnoreCase("<unknown>") || cleaned.equalsIgnoreCase("unknown")) {
            return Collections.singletonList("Unknown Artist");
        }

        // Clean website promotion suffixes (e.g. " - MassTamilan...")
        cleaned = JUNK_TAGS_PATTERN.matcher(cleaned).replaceAll("");

        // Check for parenthetical featuring credits: "Artist A (feat. Artist B)"
        List<String> extractedFromParens = new ArrayList<>();
        Matcher parenMatcher = PAREN_FEAT_PATTERN.matcher(cleaned);
        while (parenMatcher.find()) {
            String featured = parenMatcher.group(1);
            if (featured != null && !featured.trim().isEmpty()) {
                extractedFromParens.add(featured.trim());
            }
        }
        cleaned = PAREN_FEAT_PATTERN.matcher(cleaned).replaceAll("");

        // Split on standard multi-artist delimiters
        // First split on inline feat. / featuring / ft.
        String[] featParts = INLINE_FEAT_PATTERN.split(cleaned);
        List<String> rawTokens = new ArrayList<>();
        for (String fp : featParts) {
            // Split on comma
            String[] commaParts = COMMA_PATTERN.split(fp);
            for (String cp : commaParts) {
                // Split on &
                String[] ampParts = AMPERSAND_PATTERN.split(cp);
                for (String ap : ampParts) {
                    // Split on semicolon or slash
                    String[] slashParts = SEMICOLON_OR_SLASH_PATTERN.split(ap);
                    for (String sp : slashParts) {
                        if (!sp.trim().isEmpty()) {
                            rawTokens.add(sp.trim());
                        }
                    }
                }
            }
        }

        // Add any featured artists found in parentheses
        for (String p : extractedFromParens) {
            String[] sub = COMMA_PATTERN.split(p);
            for (String s : sub) {
                if (!s.trim().isEmpty()) {
                    rawTokens.add(s.trim());
                }
            }
        }

        // Normalize each individual token
        Set<String> distinctCanonical = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();

        for (String token : rawTokens) {
            String normalizedDisplay = normalizeDisplayName(token);
            if (normalizedDisplay.isEmpty() || normalizedDisplay.equalsIgnoreCase("unknown")) {
                continue;
            }
            String canonicalKey = getCanonicalKey(normalizedDisplay);
            if (distinctCanonical.add(canonicalKey)) {
                result.add(normalizedDisplay);
            }
        }

        if (result.isEmpty()) {
            return Collections.singletonList("Unknown Artist");
        }

        return result;
    }

    /**
     * Produces a canonical grouping key for an artist name.
     * Ensures "A. R. Rahman", "A.R. Rahman", and "A.R.Rahman" map to the exact same key.
     */
    @NonNull
    public static String getCanonicalKey(@Nullable String artistName) {
        if (artistName == null) return "unknown artist";
        String s = artistName.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.equals("<unknown>")) {
            return "unknown artist";
        }

        // Normalize spaces after periods between initials: "a. r." -> "a.r."
        s = s.replaceAll("(?<=\\b[a-z])\\.\\s+(?=[a-z]\\b)", ".");
        // Ensure space between initial dot and subsequent word: "a.r.rahman" -> "a.r. rahman"
        s = s.replaceAll("(?<=\\b[a-z]\\.)(?=[a-z]{2,}\\b)", " ");

        // Collapse all multiple whitespace to single space
        s = s.replaceAll("\\s+", " ").trim();

        // Remove trailing punctuation (periods, hyphens)
        s = s.replaceAll("[\\.\\-_,;]+$", "").trim();

        return s.isEmpty() ? "unknown artist" : s;
    }

    /**
     * Normalizes an artist name for presentation.
     * Fixes initial spacing (e.g., "A. R. Rahman" -> "A.R. Rahman") while preserving casing.
     */
    @NonNull
    public static String normalizeDisplayName(@Nullable String rawName) {
        if (rawName == null) return "Unknown Artist";
        String s = rawName.trim();
        if (s.isEmpty() || s.equalsIgnoreCase("<unknown>")) {
            return "Unknown Artist";
        }

        // Normalize spaces between initials with periods: "A. R. Rahman" -> "A.R. Rahman"
        s = s.replaceAll("(?<=\\b[A-Za-z])\\.\\s+(?=[A-Za-z]\\b)", ".");
        // Ensure space between initial dot and full surname: "A.R.Rahman" -> "A.R. Rahman"
        s = s.replaceAll("(?<=\\b[A-Za-z]\\.)(?=[A-Za-z]{2,}\\b)", " ");
        // Strip trailing dashes/colons/periods that might be artifacts of bad ID3 tags
        s = s.replaceAll("\\s*-\\s*$", "");
        s = s.replaceAll("\\s+", " ").trim();

        return s.isEmpty() ? "Unknown Artist" : s;
    }

    /**
     * Checks if a raw artist credit contains a specific target artist, matching by canonical key.
     * Example:
     *   containsArtist("A.R. Rahman, Shreya Ghoshal", "A.R. Rahman") -> true
     *   containsArtist("A.R. Rahman, Shreya Ghoshal", "shreya ghoshal") -> true
     *   containsArtist("A.R. Rahman, Shreya Ghoshal", "Anirudh") -> false
     */
    public static boolean containsArtist(@Nullable String rawArtistCredit, @Nullable String targetArtistOrKey) {
        if (rawArtistCredit == null || targetArtistOrKey == null) return false;
        String targetKey = getCanonicalKey(targetArtistOrKey);
        List<String> constituents = extractArtists(rawArtistCredit);
        for (String c : constituents) {
            if (getCanonicalKey(c).equals(targetKey)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Builds a canonical artist index mapping each canonical artist key to its full list of songs.
     * Deduplicates songs per artist by stable Song ID and sorts them alphabetically by title.
     */
    @NonNull
    public static Map<String, List<Song>> buildCanonicalArtistIndex(@Nullable List<Song> songs) {
        if (songs == null || songs.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Map<Long, Song>> artistSongsMap = new LinkedHashMap<>();

        for (Song song : songs) {
            if (song == null) continue;
            List<String> individualArtists = extractArtists(song.getArtist());
            for (String artistName : individualArtists) {
                String canonicalKey = getCanonicalKey(artistName);
                Map<Long, Song> songMap = artistSongsMap.computeIfAbsent(canonicalKey, k -> new LinkedHashMap<>());
                // Deduplicate by stable Song ID
                songMap.putIfAbsent(song.getId(), song);
            }
        }

        Map<String, List<Song>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Long, Song>> entry : artistSongsMap.entrySet()) {
            List<Song> artistSongList = new ArrayList<>(entry.getValue().values());
            artistSongList.sort((a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()));
            result.put(entry.getKey(), Collections.unmodifiableList(artistSongList));
        }

        return result;
    }

    /**
     * Resolves all songs belonging to an artist (by display name or canonical key).
     * Inspects constituent artist credits for multi-artist songs.
     * Deduplicates by stable Song ID and sorts songs alphabetically by title.
     */
    @NonNull
    public static List<Song> getSongsForArtist(@Nullable String artistOrKey, @Nullable List<Song> songs) {
        if (artistOrKey == null || songs == null || songs.isEmpty()) {
            return Collections.emptyList();
        }

        String targetKey = getCanonicalKey(artistOrKey);
        Map<Long, Song> matched = new LinkedHashMap<>();

        for (Song song : songs) {
            if (song == null) continue;
            if (containsArtist(song.getArtist(), targetKey)) {
                matched.putIfAbsent(song.getId(), song);
            }
        }

        List<Song> result = new ArrayList<>(matched.values());
        result.sort((a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()));
        return result;
    }

    /**
     * Builds canonical, deduplicated Artist domain models from a list of songs.
     * Each song contributes to all of its constituent artists.
     */
    @NonNull
    public static List<Artist> buildArtists(@Nullable List<Song> songs) {
        if (songs == null || songs.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Map<Long, Song>> artistSongsMap = new LinkedHashMap<>();
        Map<String, String> displayNames = new HashMap<>();

        for (Song song : songs) {
            if (song == null) continue;
            List<String> individualArtists = extractArtists(song.getArtist());
            for (String artistName : individualArtists) {
                String canonicalKey = getCanonicalKey(artistName);

                Map<Long, Song> songMap = artistSongsMap.computeIfAbsent(canonicalKey, k -> new LinkedHashMap<>());
                songMap.putIfAbsent(song.getId(), song);

                String currentDisplay = displayNames.get(canonicalKey);
                if (currentDisplay == null) {
                    displayNames.put(canonicalKey, artistName);
                } else if (!artistName.equalsIgnoreCase("Unknown Artist")) {
                    if (currentDisplay.equalsIgnoreCase("Unknown Artist")
                            || shouldPreferDisplayName(artistName, currentDisplay)) {
                        displayNames.put(canonicalKey, artistName);
                    }
                }
            }
        }

        List<Artist> result = new ArrayList<>();
        for (Map.Entry<String, Map<Long, Song>> entry : artistSongsMap.entrySet()) {
            String key = entry.getKey();
            List<Song> artistSongList = new ArrayList<>(entry.getValue().values());
            // Sort songs by title
            artistSongList.sort((a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()));

            long distinctAlbums = artistSongList.stream().map(Song::getAlbumId).distinct().count();
            long repAlbumId = artistSongList.isEmpty() ? -1L : artistSongList.get(0).getAlbumId();
            String displayName = displayNames.getOrDefault(key, "Unknown Artist");

            result.add(new Artist(displayName, key, artistSongList.size(), (int) distinctAlbums, repAlbumId, artistSongList));
        }

        // Sort artists alphabetically
        Collections.sort(result, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return result;
    }

    private static boolean shouldPreferDisplayName(String candidate, String current) {
        // Prefer Title Case or names with capital letters over all-lowercase
        int candUpper = countUpperCase(candidate);
        int curUpper = countUpperCase(current);
        if (candUpper != curUpper) {
            return candUpper > curUpper;
        }
        // Prefer longer display names if they have periods properly placed
        return candidate.length() > current.length();
    }

    private static int countUpperCase(String s) {
        int count = 0;
        for (char c : s.toCharArray()) {
            if (Character.isUpperCase(c)) count++;
        }
        return count;
    }
}
