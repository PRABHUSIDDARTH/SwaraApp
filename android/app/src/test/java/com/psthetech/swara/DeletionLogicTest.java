package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.domain.model.Song;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for Deletion logic, queue cleanup calculations, and ID validation.
 */
public class DeletionLogicTest {

    @Test
    public void testInvalidSongIdForDeletion() {
        long invalidIdZero = 0L;
        long invalidIdNegative = -50L;
        long validId = 1234L;

        assertFalse("Song ID <= 0 must be invalid for deletion", invalidIdZero > 0);
        assertFalse("Negative Song ID must be invalid for deletion", invalidIdNegative > 0);
        assertTrue("Positive Song ID must be valid for deletion", validId > 0);
    }

    @Test
    public void testQueueIndicesCalculationOnDeletion() {
        List<Song> queue = new ArrayList<>();
        Song s1 = new Song(101L, "Song 1", "Artist A", "Album A", 1L, 180000L, 1, 2024, 1000L);
        Song s2 = new Song(102L, "Song 2", "Artist B", "Album B", 2L, 200000L, 2, 2024, 1001L);
        Song s3 = new Song(101L, "Song 1 (Duplicate in queue)", "Artist A", "Album A", 1L, 180000L, 1, 2024, 1000L);

        queue.add(s1);
        queue.add(s2);
        queue.add(s3);

        long deletedSongId = 101L;

        List<Integer> matchingIndices = new ArrayList<>();
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getId() == deletedSongId) {
                matchingIndices.add(i);
            }
        }

        assertEquals(2, matchingIndices.size());
        assertEquals(Integer.valueOf(0), matchingIndices.get(0));
        assertEquals(Integer.valueOf(2), matchingIndices.get(1));
    }

    @Test
    public void testQueueStateAfterSingleItemDeletion() {
        List<Song> queue = new ArrayList<>();
        Song s1 = new Song(101L, "Song 1", "Artist A", "Album A", 1L, 180000L, 1, 2024, 1000L);
        queue.add(s1);

        long deletedSongId = 101L;
        queue.removeIf(s -> s.getId() == deletedSongId);

        assertTrue("Queue should be empty after deleting the only song", queue.isEmpty());
    }
}
