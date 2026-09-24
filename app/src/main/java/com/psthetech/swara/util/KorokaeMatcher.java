package com.psthetech.swara.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psthetech.swara.domain.model.Song;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic matching engine for finding local karaoke and instrumental tracks.
 *
 * Requirements:
 * - Recognizes common karaoke/instrumental indicators (karaoke, instrumental, minus one, backing track, music only, 伴奏).
 * - Normalizes case, whitespace, punctuation, and common separators.
 * - Rejects false positives (live, acoustic, remix, cover, radio edit, etc.).
 * - Validates artist/album compatibility.
 * - Scores candidates by title match quality, artist match, and duration proximity.
 */
public final class KorokaeMatcher {

    private KorokaeMatcher() { /* static only */ }

    // Positive karaoke / instrumental indicators
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

    // False positive keywords that disqualify a candidate unless present in the original track
    private static final String[] FALSE_POSITIVE_KEYWORDS = {
            "live",
            "acoustic",
            "remix",
            "mix",
            "cover",
            "radio edit",
            "extended",
            "demo",
            "tribute",
            "unplugged",
            "acapella",
            "a cappella",
            "slowed",
            "reverb"
    };

    // Generic karaoke publishers/artists that shouldn't trigger an artist mismatch
    private static final List<String> GENERIC_KARAOKE_ARTISTS = Arrays.asList(
            "karaoke",
            "the karaoke channel",
            "karaoke version",
            "instrumental",
            "soundtrack",
            "backing track",
            "backing tracks",
            "unknown artist",
            "unknown",
            "various artists"
    );

    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[\\p{Punct}&&[^\\w\\s]]");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");

    /**
     * Find the best matching karaoke / instrumental track for the given target song
     * from a candidate list of songs.
     *
     * @param targetSong Currently playing or target song.
     * @param candidates List of songs to search across (typically full local library).
     * @return Best matching Song candidate, or null if no valid match is found.
     */
    @Nullable
    public static Song findBestMatch(@Nullable Song targetSong, @Nullable List<Song> candidates) {
        if (targetSong == null || candidates == null || candidates.isEmpty()) {
            return null;
        }

        Song bestCandidate = null;
        int highestScore = -1;

        for (Song candidate : candidates) {
            if (candidate == null || candidate.getId() == targetSong.getId()) {
                continue; // Cannot match with itself
            }

            int score = scoreMatch(targetSong, candidate);
            if (score > highestScore && score >= 50) { // 50 is minimum confidence threshold
                highestScore = score;
                bestCandidate = candidate;
            }
        }

        return bestCandidate;
    }

    /**
     * Evaluates a candidate song against the target song and returns a confidence score (0 - 200).
     * Returns -1 if the candidate is rejected (e.g. false positive, artist mismatch, or not karaoke).
     */
    public static int scoreMatch(@NonNull Song targetSong, @NonNull Song candidate) {
        String targetTitle = targetSong.getTitle();
        String candidateTitle = candidate.getTitle();

        if (targetTitle == null || candidateTitle == null) {
            return -1;
        }

        String normTargetTitle = normalizeString(targetTitle);
        String normCandidateTitle = normalizeString(candidateTitle);

        // 1. Candidate must contain a recognized karaoke indicator
        boolean candidateHasKaraokeIndicator = hasKaraokeIndicator(normCandidateTitle)
                || hasKaraokeIndicator(normalizeString(candidate.getAlbum()));

        if (!candidateHasKaraokeIndicator) {
            return -1;
        }

        // 2. Filter out false positives (e.g. "Live", "Acoustic", "Remix")
        if (hasFalsePositive(normCandidateTitle, normTargetTitle)) {
            return -1;
        }

        // 3. Artist compatibility check
        if (!isArtistCompatible(targetSong, candidate)) {
            return -1;
        }

        // 4. Extract base titles (removing the karaoke indicators and parenthesized tags)
        String baseTarget = extractBaseTitle(normTargetTitle);
        String baseCandidate = extractBaseTitle(normCandidateTitle);

        if (baseTarget.isEmpty() || baseCandidate.isEmpty()) {
            return -1;
        }

        // 5. Title match scoring
        int score = 0;
        if (baseTarget.equals(baseCandidate)) {
            score += 100;
        } else if (baseCandidate.startsWith(baseTarget) || baseTarget.startsWith(baseCandidate)) {
            score += 70;
        } else {
            return -1; // Titles do not match
        }

        // 6. Artist match scoring
        String normTargetArtist = normalizeString(targetSong.getArtist());
        String normCandidateArtist = normalizeString(candidate.getArtist());
        if (!normTargetArtist.isEmpty() && normTargetArtist.equals(normCandidateArtist)) {
            score += 40;
        } else if (isGenericKaraokeArtist(normCandidateArtist)) {
            score += 20;
        }

        // 7. Album match bonus
        String normTargetAlbum = normalizeString(targetSong.getAlbum());
        String normCandidateAlbum = normalizeString(candidate.getAlbum());
        if (!normTargetAlbum.isEmpty() && normTargetAlbum.equals(normCandidateAlbum)) {
            score += 20;
        }

        // 8. Duration proximity scoring (karaoke tracks are almost always very close in length)
        long targetDur = targetSong.getDuration();
        long candidateDur = candidate.getDuration();
        if (targetDur > 0 && candidateDur > 0) {
            long diffMs = Math.abs(targetDur - candidateDur);
            if (diffMs <= 5_000) { // within 5 seconds
                score += 30;
            } else if (diffMs <= 15_000) { // within 15 seconds
                score += 20;
            } else if (diffMs <= 30_000) { // within 30 seconds
                score += 10;
            } else if (diffMs > 60_000) { // > 1 minute difference
                score -= 30;
            }
        }

        return score;
    }

    /**
     * Checks if the normalized string contains any recognized karaoke/instrumental indicators.
     */
    public static boolean hasKaraokeIndicator(@Nullable String normalizedText) {
        if (normalizedText == null || normalizedText.isEmpty()) return false;
        for (String indicator : KARAOKE_INDICATORS) {
            if (normalizedText.contains(indicator)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the candidate contains a false positive indicator that wasn't already in the target.
     */
    public static boolean hasFalsePositive(@NonNull String normCandidateTitle, @NonNull String normTargetTitle) {
        for (String fp : FALSE_POSITIVE_KEYWORDS) {
            if (containsWord(normCandidateTitle, fp) && !containsWord(normTargetTitle, fp)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verifies that the candidate's artist is compatible with the target's artist.
     */
    public static boolean isArtistCompatible(@NonNull Song targetSong, @NonNull Song candidate) {
        String targetArtist = normalizeString(targetSong.getArtist());
        String candidateArtist = normalizeString(candidate.getArtist());

        // Empty artist is treated as compatible
        if (targetArtist.isEmpty() || candidateArtist.isEmpty()) {
            return true;
        }

        // Exact artist match
        if (targetArtist.equals(candidateArtist)) {
            return true;
        }

        // Known karaoke publishers/generic artists are compatible
        if (isGenericKaraokeArtist(candidateArtist)) {
            return true;
        }

        // Substring / containment (e.g. "Ed Sheeran feat. Beyoncé" vs "Ed Sheeran")
        if (candidateArtist.contains(targetArtist) || targetArtist.contains(candidateArtist)) {
            return true;
        }

        // Album match can also reconcile artist differences
        String targetAlbum = normalizeString(targetSong.getAlbum());
        String candidateAlbum = normalizeString(candidate.getAlbum());
        if (!targetAlbum.isEmpty() && targetAlbum.equals(candidateAlbum)) {
            return true;
        }

        return false;
    }

    private static boolean isGenericKaraokeArtist(@NonNull String normArtist) {
        for (String generic : GENERIC_KARAOKE_ARTISTS) {
            if (normArtist.equals(generic) || normArtist.contains(generic)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Strips karaoke indicators, brackets, parentheses, and trailing hyphens to leave the core title.
     */
    @NonNull
    public static String extractBaseTitle(@NonNull String normalizedTitle) {
        String base = normalizedTitle;

        // Strip known karaoke phrases
        for (String indicator : KARAOKE_INDICATORS) {
            base = base.replace(indicator, "");
        }

        // Strip remaining brackets and punctuation
        base = PUNCTUATION_PATTERN.matcher(base).replaceAll(" ");
        base = MULTI_SPACE_PATTERN.matcher(base).replaceAll(" ").trim();

        return base;
    }

    /**
     * Normalizes a string: decomposes Unicode (NFKD), strips accents, converts to lowercase,
     * strips extraneous punctuation, and collapses multiple whitespace.
     */
    @NonNull
    public static String normalizeString(@Nullable String input) {
        if (input == null) return "";
        // Decompose unicode characters (e.g. accents)
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKD);
        // Remove diacritical marks
        normalized = normalized.replaceAll("\\p{M}", "");
        // Lowercase
        normalized = normalized.toLowerCase(Locale.ROOT);
        // Remove apostrophes and quotes so "don't" matches "dont"
        normalized = normalized.replace("'", "")
                .replace("`", "")
                .replace("\"", "")
                .replace("’", "")
                .replace("‘", "");
        // Replace common brackets and dashes with spaces for clean tokenization
        normalized = normalized.replace('(', ' ')
                .replace(')', ' ')
                .replace('[', ' ')
                .replace(']', ' ')
                .replace('{', ' ')
                .replace('}', ' ')
                .replace('-', ' ')
                .replace('_', ' ')
                .replace('/', ' ')
                .replace('\\', ' ');
        // Collapse spaces and trim
        return MULTI_SPACE_PATTERN.matcher(normalized).replaceAll(" ").trim();
    }

    private static boolean containsWord(@NonNull String text, @NonNull String word) {
        String padded = " " + text + " ";
        return padded.contains(" " + word + " ");
    }
}
