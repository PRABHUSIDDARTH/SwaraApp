package com.psthetech.swara.wrapped;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WrappedStatsEngineTest {
    private static final Period OCT = Period.month(2026, 10);

    private static DailyRow d(int day, long id, long ms) {
        return new DailyRow(day, id, ms, 1, 0, 0, 0, 0);
    }

    private static Map<Long, SongInfo> songs(Object... idArtistAlbum) {
        Map<Long, SongInfo> m = new HashMap<>();
        for (int i = 0; i < idArtistAlbum.length; i += 3) {
            long id = (Long) idArtistAlbum[i];
            String artist = (String) idArtistAlbum[i + 1];
            long album = (Long) idArtistAlbum[i + 2];
            m.put(id, new SongInfo(id, "Song " + id, artist, artist.toLowerCase(), "Album " + album, album, 20261001));
        }
        return m;
    }

    private static WrappedStats run(List<DailyRow> in, List<DailyRow> before, List<HourlyRow> hourly,
                                    Map<Long, SongInfo> s, Map<Long, double[]> moods) {
        return WrappedStatsEngine.compute(OCT, in, before, hourly, s, moods);
    }

    @Test
    public void totalMinutes() {
        WrappedStats w = run(Arrays.asList(d(20261001, 1, 60_000), d(20261002, 2, 120_000)),
                Collections.emptyList(), Collections.emptyList(), songs(1L, "A", 1L, 2L, "B", 2L), new HashMap<>());
        assertEquals(180_000, w.totalMs);
    }

    @Test
    public void topSongs_tiesBySongId() {
        WrappedStats w = run(Arrays.asList(d(20261001, 5, 1000), d(20261001, 3, 1000), d(20261001, 9, 5000)),
                Collections.emptyList(), Collections.emptyList(), songs(5L, "A", 1L, 3L, "A", 1L, 9L, "B", 2L),
                new HashMap<>());
        assertEquals(Arrays.asList(9L, 3L, 5L), w.topSongIds);
        assertEquals(5000, w.topSongMs);
    }

    @Test
    public void topArtists_byCanonicalKey() {
        WrappedStats w = run(Arrays.asList(d(20261001, 1, 1000), d(20261001, 2, 1000), d(20261001, 3, 5000)),
                Collections.emptyList(), Collections.emptyList(), songs(1L, "Anirudh", 1L, 2L, "Anirudh", 1L, 3L, "Yuvan", 2L),
                new HashMap<>());
        assertEquals(Arrays.asList("yuvan", "anirudh"), w.topArtistKeys);
    }

    @Test
    public void topAlbum_needsThreePlays() {
        List<DailyRow> rows = new ArrayList<>(Arrays.asList(d(20261001, 1, 9000), d(20261002, 1, 1000)));
        WrappedStats w = run(rows, Collections.emptyList(), Collections.emptyList(), songs(1L, "A", 7L), new HashMap<>());
        assertEquals(-1, w.topAlbumId); // 2 plays
        rows.add(d(20261003, 1, 1000));
        w = run(rows, Collections.emptyList(), Collections.emptyList(), songs(1L, "A", 7L), new HashMap<>());
        assertEquals(7, w.topAlbumId);
    }

    @Test
    public void moodMix_bucketsAndUnknownExcluded() {
        Map<Long, double[]> moods = new HashMap<>();
        moods.put(1L, new double[]{0.1, 0.2}); // SAD
        moods.put(2L, new double[]{0.8, 0.9}); // ENERGETIC
        WrappedStats w = run(Arrays.asList(d(20261001, 1, 1000), d(20261001, 2, 3000), d(20261001, 3, 6000)),
                Collections.emptyList(), Collections.emptyList(), songs(1L, "A", 1L, 2L, "A", 1L, 3L, "A", 1L), moods);
        assertEquals(1000L, (long) w.moodMs.get(MoodBucket.SAD));
        assertEquals(3000L, (long) w.moodMs.get(MoodBucket.ENERGETIC));
        assertEquals(4000, w.moodKnownMs);
    }

    @Test
    public void peakHour_andNightMorning() {
        List<HourlyRow> h = Arrays.asList(new HourlyRow(20261001, 23, 5000), new HourlyRow(20261001, 2, 1000),
                new HourlyRow(20261001, 6, 3000), new HourlyRow(20261002, 23, 1000));
        WrappedStats w = run(Collections.singletonList(d(20261001, 1, 10_000)), Collections.emptyList(), h,
                songs(1L, "A", 1L), new HashMap<>());
        assertEquals(23, w.peakHour);
        assertEquals(7000, w.nightMs);
        assertEquals(3000, w.morningMs);
    }

    @Test
    public void busiestWeekday_tieMondayFirst() {
        // 2026-10-19 is a Monday, 2026-10-20 a Tuesday
        WrappedStats w = run(Arrays.asList(d(20261020, 1, 5000), d(20261019, 2, 5000)),
                Collections.emptyList(), Collections.emptyList(), songs(1L, "A", 1L, 2L, "B", 2L), new HashMap<>());
        assertEquals(0, w.busiestWeekday);
    }

    @Test
    public void longestStreak_acrossMonthAndYear() {
        Period p = Period.year(2026);
        List<DailyRow> rows = Arrays.asList(d(20260130, 1, 1), d(20260131, 1, 1), d(20260201, 1, 1),
                d(20260202, 1, 1), d(20261231, 1, 1));
        WrappedStats w = WrappedStatsEngine.compute(p, rows, Collections.emptyList(), Collections.emptyList(),
                songs(1L, "A", 1L), new HashMap<>());
        assertEquals(4, w.longestStreakDays);
        assertEquals(5, w.listeningDays);
    }

    @Test
    public void newSongs_byFirstHeardDay() {
        Map<Long, SongInfo> s = songs(1L, "A", 1L, 2L, "B", 2L);
        s.put(2L, new SongInfo(2L, "Old", "B", "b", "Al", 2L, 20250101));
        WrappedStats w = run(Arrays.asList(d(20261005, 1, 1000), d(20261005, 2, 1000)),
                Collections.emptyList(), Collections.emptyList(), s, new HashMap<>());
        assertEquals(1, w.newSongs);
        assertEquals(2, w.distinctSongs);
    }

    @Test
    public void rediscovered_needs60DayGap() {
        List<DailyRow> before = Arrays.asList(d(20260601, 1, 1000), d(20260915, 2, 1000));
        WrappedStats w = run(Arrays.asList(d(20261003, 1, 2000), d(20261003, 2, 9000)), before,
                Collections.emptyList(), songs(1L, "A", 1L, 2L, "B", 2L), new HashMap<>());
        assertEquals(1, w.rediscoveredSongId); // song 2 was played 16 days before the period
    }

    @Test
    public void repeatAndSkip_needThree() {
        DailyRow r = new DailyRow(20261001, 1, 1000, 1, 0, 3, 2, 0);
        DailyRow s = new DailyRow(20261001, 2, 1000, 1, 0, 1, 3, 0);
        WrappedStats w = run(Arrays.asList(r, s), Collections.emptyList(), Collections.emptyList(),
                songs(1L, "A", 1L, 2L, "B", 2L), new HashMap<>());
        assertEquals(2, w.mostRepeatedSongId);
        assertEquals(1, w.mostSkippedSongId);
        assertEquals(3, w.mostRepeatedCount);
    }

    @Test
    public void vibeStarts_summedAndTopSong() {
        DailyRow a = new DailyRow(20261001, 1, 1000, 1, 0, 0, 0, 2);
        DailyRow b = new DailyRow(20261002, 2, 1000, 1, 0, 0, 0, 3);
        WrappedStats w = run(Arrays.asList(a, b), Collections.emptyList(), Collections.emptyList(),
                songs(1L, "A", 1L, 2L, "B", 2L), new HashMap<>());
        assertEquals(5, w.vibeStarts);
        assertEquals(2, w.topVibeSongId);
    }

    @Test
    public void rowsOutsidePeriod_ignored() {
        WrappedStats w = run(Arrays.asList(d(20260930, 1, 9999), d(20261001, 1, 1000)),
                Collections.emptyList(), Collections.emptyList(), songs(1L, "A", 1L), new HashMap<>());
        assertEquals(1000, w.totalMs);
    }
}
