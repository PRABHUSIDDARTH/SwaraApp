package com.psthetech.swara.wrapped;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class WrappedStatsJsonTest {
    @Test
    public void json_roundTrip_equal() {
        Map<Long, SongInfo> s = new HashMap<>();
        s.put(1L, new SongInfo(1L, "Song \"1\"", "Anirudh", "anirudh", "Al", 4L, 20261001));
        s.put(2L, new SongInfo(2L, "Two", "Yuvan", "yuvan", null, 5L, 20261002));
        Map<Long, double[]> moods = new HashMap<>();
        moods.put(1L, new double[]{0.8, 0.9});
        WrappedStats w = WrappedStatsEngine.compute(Period.month(2026, 10),
                Arrays.asList(new DailyRow(20261001, 1, 60_000, 3, 1, 0, 3, 1),
                        new DailyRow(20261002, 2, 30_000, 1, 0, 3, 0, 0)),
                Arrays.asList(), Arrays.asList(new HourlyRow(20261001, 23, 90_000)), s, moods);
        WrappedStats back = WrappedStats.fromJson(w.toJson());
        assertEquals(w.toJson(), back.toJson());
        assertEquals(w.period.key(), back.period.key());
        assertEquals("Song \"1\"", back.songs.get(1L).title);
        assertEquals(w.topSongIds, back.topSongIds);
        assertEquals(w.moodMs, back.moodMs);
    }
}
