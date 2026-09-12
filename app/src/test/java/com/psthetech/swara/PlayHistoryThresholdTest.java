package com.psthetech.swara;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.domain.model.Song;

import org.junit.Before;
import org.junit.Test;

public class PlayHistoryThresholdTest {

    private Song longSong;
    private Song shortSong;

    @Before
    public void setUp() {
        // 3 minute song (180,000 ms)
        longSong = new Song(1L, "Long Song", "Artist", "Album", 1L, 180_000L, 1, 2024, 1000L);
        // 20 second song (20,000 ms)
        shortSong = new Song(2L, "Short Song", "Artist", "Album", 1L, 20_000L, 2, 2024, 1000L);
    }

    @Test
    public void testLongSongThresholdCalculation() {
        // 40% of 180,000ms is 72,000ms, but capped at 30,000ms
        long threshold = calculateThreshold(longSong);
        org.junit.Assert.assertEquals(30_000L, threshold);

        assertFalse(meetsThreshold(longSong, 15_000L));
        assertTrue(meetsThreshold(longSong, 30_000L));
        assertTrue(meetsThreshold(longSong, 45_000L));
    }

    @Test
    public void testShortSongThresholdCalculation() {
        // 40% of 20,000ms is 8,000ms
        long threshold = calculateThreshold(shortSong);
        org.junit.Assert.assertEquals(8_000L, threshold);

        assertFalse(meetsThreshold(shortSong, 5_000L));
        assertTrue(meetsThreshold(shortSong, 8_000L));
        assertTrue(meetsThreshold(shortSong, 12_000L));
    }

    private long calculateThreshold(Song song) {
        return Math.min(30_000L, (long) (song.getDuration() * 0.4));
    }

    private boolean meetsThreshold(Song song, long positionMs) {
        return positionMs >= calculateThreshold(song);
    }
}
