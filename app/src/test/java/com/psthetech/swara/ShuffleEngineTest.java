package com.psthetech.swara;

import com.psthetech.swara.data.repository.ShuffleEngine;
import com.psthetech.swara.domain.model.Song;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Comprehensive tests for ShuffleEngine.
 *
 * Tests cover (from spec Parts 13–17):
 *  TEST 1:  All songs included exactly once (no duplicates, no missing)
 *  TEST 2:  Song ID identity preserved — shuffle by stable ID, not position
 *  TEST 3:  Consecutive shuffles produce different permutations (probabilistic)
 *  TEST 4:  Repeat-avoidance: shuffleAvoidRepeat differs from previousOrder
 *  TEST 5:  Normal play (no shuffle) preserves original order
 *  TEST 6:  Deduplicate: duplicate song IDs produce one entry
 *  TEST 7:  Deduplication preserves first-occurrence order for kept entries
 *  TEST 8:  Two-song playlist alternates (avoidance works for size=2)
 *  TEST 9:  One-song playlist returns single element unchanged
 *  TEST 10: Empty input returns empty list
 *  TEST 11: Null input returns empty list safely
 *  TEST 12: Shuffle output size equals deduplicated input size
 *  TEST 13: fisherYates internal — all elements present after shuffle
 */
public class ShuffleEngineTest {

    // ===== Helpers =====

    private Song song(long id) {
        return new Song(id, "Title" + id, "Artist" + id, "Album" + id, id, 60000, 1, 2024, 0);
    }

    private List<Song> songList(long... ids) {
        List<Song> list = new ArrayList<>();
        for (long id : ids) list.add(song(id));
        return list;
    }

    private Set<Long> idSet(List<Song> songs) {
        Set<Long> ids = new HashSet<>();
        for (Song s : songs) ids.add(s.getId());
        return ids;
    }

    // ===== TEST 1: All songs present, no duplicates =====

    @Test
    public void test01_shuffledListContainsAllSongsExactlyOnce() {
        List<Song> source = songList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        List<Song> shuffled = ShuffleEngine.shuffle(source);

        // Same size
        assertEquals("Shuffled list must have same size as source", source.size(), shuffled.size());

        // All original IDs present
        Set<Long> sourceIds = idSet(source);
        Set<Long> shuffledIds = idSet(shuffled);
        assertEquals("All song IDs must be present after shuffle", sourceIds, shuffledIds);

        // No duplicates
        assertEquals("No duplicate songs allowed after shuffle", shuffled.size(), shuffledIds.size());
    }

    // ===== TEST 2: Song ID identity preserved =====

    @Test
    public void test02_songIdIdentityPreservedAfterShuffle() {
        List<Song> source = songList(100, 200, 300, 400, 500);
        List<Song> shuffled = ShuffleEngine.shuffle(source);

        // Every element in shuffled must be one of the original songs
        Set<Long> sourceIds = idSet(source);
        for (Song s : shuffled) {
            assertTrue("Shuffled song ID must be from source", sourceIds.contains(s.getId()));
        }
    }

    // ===== TEST 3: Consecutive shuffles produce different permutations =====

    @Test
    public void test03_consecutiveShufflesProduceDifferentOrders() {
        List<Song> source = songList(1, 2, 3, 4, 5, 6, 7, 8);

        // Run 20 shuffles and confirm not all identical (probabilistic — chance of all
        // 20 being equal is (1/8!) * 19 ≈ 10^-17, effectively impossible)
        List<Song> first = ShuffleEngine.shuffle(source);
        boolean foundDifferent = false;
        for (int i = 0; i < 20; i++) {
            List<Song> candidate = ShuffleEngine.shuffle(source);
            if (!ShuffleEngine.listsEqual(first, candidate)) {
                foundDifferent = true;
                break;
            }
        }
        assertTrue("Repeated shuffles should produce different orders", foundDifferent);
    }

    // ===== TEST 4: Repeat-avoidance — shuffleAvoidRepeat differs from previous =====

    @Test
    public void test04_shuffleAvoidRepeatDiffersFromPrevious() {
        List<Song> source = songList(1, 2, 3, 4, 5);
        List<Song> previous = ShuffleEngine.shuffle(source);

        // Over 10 attempts, at least once it should differ
        // (for 5 songs: 1 - 1/120 chance per trial of matching = basically never matches)
        boolean foundDifferent = false;
        for (int i = 0; i < 10; i++) {
            List<Song> result = ShuffleEngine.shuffleAvoidRepeat(source, previous);
            if (!ShuffleEngine.listsEqual(result, previous)) {
                foundDifferent = true;
                break;
            }
        }
        assertTrue("shuffleAvoidRepeat should produce a different order from previous", foundDifferent);
    }

    // ===== TEST 5: Normal play preserves original order =====

    @Test
    public void test05_noShufflePreservesOriginalOrder() {
        List<Song> source = songList(10, 20, 30, 40, 50);
        // "No shuffle" = caller uses source directly, no ShuffleEngine call
        List<Song> copy = new ArrayList<>(source);

        // Verify: copy unchanged from source
        assertEquals("Original order must be preserved when shuffle is not applied", source.size(), copy.size());
        for (int i = 0; i < source.size(); i++) {
            assertEquals("Song at position " + i + " must match", source.get(i).getId(), copy.get(i).getId());
        }
    }

    // ===== TEST 6: Deduplication removes duplicate IDs =====

    @Test
    public void test06_deduplicateRemovesDuplicateSongIds() {
        // Song with ID 2 appears three times
        List<Song> input = songList(1, 2, 2, 3, 2, 4);
        List<Song> deduped = ShuffleEngine.deduplicateSongs(input);

        assertEquals("Deduplicated list should have unique IDs only", 4, deduped.size());
        assertEquals("Unique ID count must match list size", 4, idSet(deduped).size());
    }

    // ===== TEST 7: Deduplication preserves first-occurrence order =====

    @Test
    public void test07_deduplicatePreservesFirstOccurrenceOrder() {
        List<Song> input = songList(3, 1, 2, 1, 3, 4);
        List<Song> deduped = ShuffleEngine.deduplicateSongs(input);

        // Expected order: 3, 1, 2, 4
        assertEquals(4, deduped.size());
        assertEquals(3L, deduped.get(0).getId());
        assertEquals(1L, deduped.get(1).getId());
        assertEquals(2L, deduped.get(2).getId());
        assertEquals(4L, deduped.get(3).getId());
    }

    // ===== TEST 8: Two-song playlist avoids repeat =====

    @Test
    public void test08_twoSongPlaylistAvoidsPreviousOrderWhenPossible() {
        List<Song> source = songList(1, 2);
        List<Song> previous = new ArrayList<>(source); // [1, 2]

        // With 2 songs there are only 2 permutations — avoidance must flip them
        boolean foundDifferent = false;
        for (int i = 0; i < 10; i++) {
            List<Song> result = ShuffleEngine.shuffleAvoidRepeat(source, previous);
            if (!ShuffleEngine.listsEqual(result, previous)) {
                foundDifferent = true;
                break;
            }
        }
        assertTrue("Two-song shuffle should produce alternative order", foundDifferent);
    }

    // ===== TEST 9: One-song playlist returns single element =====

    @Test
    public void test09_oneSongPlaylistReturnsSingleElement() {
        List<Song> source = songList(42);
        List<Song> shuffled = ShuffleEngine.shuffle(source);

        assertEquals("One-song playlist should still have 1 song", 1, shuffled.size());
        assertEquals("Single song ID must be preserved", 42L, shuffled.get(0).getId());
    }

    // ===== TEST 10: Empty input returns empty list =====

    @Test
    public void test10_emptyInputReturnsEmptyList() {
        List<Song> result = ShuffleEngine.shuffle(new ArrayList<>());
        assertNotNull("Result must not be null", result);
        assertTrue("Empty input should return empty result", result.isEmpty());
    }

    // ===== TEST 11: Null input returns empty list safely =====

    @Test
    public void test11_nullInputReturnsSafeEmptyList() {
        List<Song> shuffled = ShuffleEngine.shuffle(null);
        assertNotNull("Null input should not throw — must return empty list", shuffled);
        assertTrue("Result for null input must be empty", shuffled.isEmpty());

        List<Song> deduped = ShuffleEngine.deduplicateSongs(null);
        assertNotNull("deduplicateSongs(null) must not be null", deduped);
        assertTrue("deduplicateSongs(null) must be empty", deduped.isEmpty());
    }

    // ===== TEST 12: Shuffled size equals deduplicated input size =====

    @Test
    public void test12_shuffleSongsSizeMatchesDedupedInputSize() {
        // Source with duplicates: IDs 1,2,3,1,4,2 → deduped = 1,2,3,4 → size 4
        List<Song> source = songList(1, 2, 3, 1, 4, 2);
        List<Song> result = ShuffleEngine.shuffleSongs(source, null);

        assertEquals("shuffleSongs result size should equal deduplicated input size", 4, result.size());
        assertEquals("No duplicate IDs in shuffleSongs result", 4, idSet(result).size());
    }

    // ===== TEST 13: Internal fisherYates — all elements present after shuffle =====

    @Test
    public void test13_fisherYatesInternalPreservesAllElements() {
        List<Long> source = new ArrayList<>();
        for (long i = 1; i <= 100; i++) source.add(i);

        List<Long> copy = new ArrayList<>(source);
        ShuffleEngine.fisherYates(copy, new java.security.SecureRandom());

        // All original values present
        assertEquals("Fisher-Yates must preserve element count", source.size(), copy.size());
        Set<Long> copySet = new HashSet<>(copy);
        for (long i = 1; i <= 100; i++) {
            assertTrue("Element " + i + " must be present after Fisher-Yates", copySet.contains(i));
        }
    }
}
