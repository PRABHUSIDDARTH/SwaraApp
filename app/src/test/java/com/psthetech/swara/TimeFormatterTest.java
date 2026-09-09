package com.psthetech.swara;

import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.util.TimeFormatter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TimeFormatterTest {

    @Test
    public void testFormatMs_Zero() {
        assertEquals("0:00", TimeFormatter.formatMs(0));
    }

    @Test
    public void testFormatMs_StandardSong() {
        // 3 minutes 45 seconds = 225,000 ms
        assertEquals("3:45", TimeFormatter.formatMs(225000));
    }

    @Test
    public void testFormatMs_LongSong() {
        // 65 minutes 5 seconds = 3,905,000 ms
        assertEquals("1:05:05", TimeFormatter.formatMs(3905000));
    }

    @Test
    public void testSongDurationFormatting() {
        Song song = new Song(1L, "Test Song", "Test Artist", "Test Album", 100L, 185000L, 1, 2024, System.currentTimeMillis());
        assertEquals("3:05", song.getFormattedDuration());
    }
}
