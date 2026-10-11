package com.psthetech.swara.data.repository;

import com.psthetech.swara.domain.model.Song;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * ShuffleEngine — unbiased Fisher-Yates shuffle with per-session randomisation.
 *
 * Design rules:
 *  - Never uses a deterministic seed (no playlist-ID-based seed).
 *  - Never caches or reuses a previous shuffled order.
 *  - Always produces a fresh permutation using SecureRandom.
 *  - Supports repeat-avoidance: if the new permutation is identical to the
 *    previous one, it retries up to MAX_RETRY times (bounded, handles tiny playlists).
 *  - All methods are static; no instance state.
 *  - No Android dependencies → fully testable in plain JUnit.
 *
 * Shuffle contract (from spec Parts 13–17):
 *  - Contains every source song exactly once.
 *  - Does not duplicate songs.
 *  - Does not lose songs.
 *  - Uses stable Song IDs (not adapter positions).
 *  - Does NOT automatically reshuffle on every next-song event.
 *  - Does NOT break Repeat-One or Repeat-All semantics.
 */
public final class ShuffleEngine {

    private static final int MAX_RETRY = 5;

    private ShuffleEngine() { /* static utility */ }

    // ===== Public API =====

    /**
     * Fisher-Yates shuffle — always generates a fresh, unbiased permutation.
     * Uses SecureRandom internally for non-deterministic results.
     *
     * @param source Input list (not modified).
     * @return A new shuffled list containing the same elements.
     */
    public static <T> List<T> shuffle(List<T> source) {
        if (source == null || source.isEmpty()) return new ArrayList<>();
        List<T> copy = new ArrayList<>(source);
        fisherYates(copy, new SecureRandom());
        return copy;
    }

    /**
     * Shuffle with repeat-avoidance.
     *
     * If the freshly generated order is identical to {@code previousOrder},
     * retries up to MAX_RETRY times to produce a different permutation.
     * For playlists of size 1, the single element is always returned as-is.
     * For playlists of size 2, the two elements will naturally alternate.
     *
     * @param source        Input list (not modified).
     * @param previousOrder The previous shuffled order (may be null or empty).
     * @return A new shuffled list that is different from previousOrder when possible.
     */
    public static <T> List<T> shuffleAvoidRepeat(List<T> source, List<T> previousOrder) {
        if (source == null || source.isEmpty()) return new ArrayList<>();
        if (source.size() == 1) return new ArrayList<>(source);

        Random rng = new SecureRandom();
        List<T> result = null;

        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            List<T> candidate = new ArrayList<>(source);
            fisherYates(candidate, rng);
            if (!listsEqual(candidate, previousOrder)) {
                result = candidate;
                break;
            }
        }

        // If every retry produced the same order (extremely unlikely), return last candidate
        if (result == null) {
            result = new ArrayList<>(source);
            fisherYates(result, rng);
        }

        return result;
    }

    /**
     * Deduplicate a song list by stable Song ID, preserving first-occurrence order.
     * Uses Song.getId() as the stable content identity key (same as MediaItem.mediaId).
     *
     * @param songs Input list (not modified).
     * @return New list with duplicates removed, original order preserved.
     */
    public static List<Song> deduplicateSongs(List<Song> songs) {
        if (songs == null || songs.isEmpty()) return new ArrayList<>();
        Map<Long, Song> seen = new LinkedHashMap<>();
        for (Song s : songs) {
            if (s != null && !seen.containsKey(s.getId())) {
                seen.put(s.getId(), s);
            }
        }
        return new ArrayList<>(seen.values());
    }

    /**
     * Shuffle a Song list with deduplication and repeat-avoidance in one step.
     * This is the recommended method for Library and Playlist shuffle actions.
     *
     * @param source        Input list (not modified).
     * @param previousOrder The previous shuffle result (null = no constraint).
     * @return Deduplicated, freshly shuffled list different from previousOrder when possible.
     */
    public static List<Song> shuffleSongs(List<Song> source, List<Song> previousOrder) {
        List<Song> deduped = deduplicateSongs(source);
        return shuffleAvoidRepeat(deduped, previousOrder);
    }

    // ===== Internal =====

    /**
     * In-place Fisher-Yates (Knuth) shuffle.
     * Each element at index i is swapped with a randomly chosen element at index [i, n-1].
     * This produces an unbiased permutation in O(n) time.
     */
    public static <T> void fisherYates(List<T> list, Random rng) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    /**
     * Structural equality check — same elements in same positions.
     * Used only for repeat-avoidance; Song equality uses getId().
     */
    public static <T> boolean listsEqual(List<T> a, List<T> b) {
        if (a == b) return true;
        if (a == null || b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            T ea = a.get(i);
            T eb = b.get(i);
            if (ea instanceof Song && eb instanceof Song) {
                if (((Song) ea).getId() != ((Song) eb).getId()) return false;
            } else {
                if (!java.util.Objects.equals(ea, eb)) return false;
            }
        }
        return true;
    }
}
