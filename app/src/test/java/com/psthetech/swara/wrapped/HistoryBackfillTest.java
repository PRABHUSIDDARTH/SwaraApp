package com.psthetech.swara.wrapped;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.data.db.entity.ListenEvent;
import com.psthetech.swara.data.db.entity.PlayHistory;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public class HistoryBackfillTest {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final long T0 = 1792231200000L; // 2026-10-17T10:00:00Z
    private static final int DAY = 20261017;

    private static ListenEvent ev(long songId, int type, long pos, long ts) {
        ListenEvent e = new ListenEvent();
        e.songId = songId;
        e.type = type;
        e.positionMs = pos;
        e.timestamp = ts;
        return e;
    }

    private static Map<Long, Long> durations() {
        Map<Long, Long> m = new HashMap<>();
        m.put(1L, 200_000L);
        m.put(2L, 300_000L);
        return m;
    }

    private static DailyRow find(List<DailyRow> rows, int day, long songId) {
        for (DailyRow r : rows) if (r.day == day && r.songId == songId) return r;
        return null;
    }

    @Test
    public void finishCountsFullDuration() {
        HistoryBackfill.Result r = HistoryBackfill.convert(Collections.emptyList(), Arrays.asList(
                ev(1, ListenEvent.TYPE_PLAY, 0, T0),
                ev(1, ListenEvent.TYPE_FINISH, 199_000, T0 + 200_000)), durations(), UTC);
        DailyRow d = find(r.daily, DAY, 1);
        assertEquals(200_000, d.playedMs);
        assertEquals(1, d.plays);
        assertEquals(1, d.finishes);
        assertEquals(200_000L, (long) r.hourlyMs(DAY, 10));
    }

    @Test
    public void skipCountsPosition() {
        HistoryBackfill.Result r = HistoryBackfill.convert(Collections.emptyList(), Arrays.asList(
                ev(2, ListenEvent.TYPE_PLAY, 0, T0),
                ev(2, ListenEvent.TYPE_SKIP, 12_000, T0 + 12_000)), durations(), UTC);
        DailyRow d = find(r.daily, DAY, 2);
        assertEquals(12_000, d.playedMs);
        assertEquals(1, d.skips);
    }

    @Test
    public void unmatchedPlayCapped30s() {
        HistoryBackfill.Result r = HistoryBackfill.convert(Collections.emptyList(), Arrays.asList(
                ev(1, ListenEvent.TYPE_PLAY, 0, T0),
                ev(2, ListenEvent.TYPE_PLAY, 0, T0 + 90_000)), durations(), UTC);
        assertEquals(30_000, find(r.daily, DAY, 1).playedMs);
        assertEquals(30_000, find(r.daily, DAY, 2).playedMs);
    }

    @Test
    public void playHistoryWithoutEvents_countsOncePlay() {
        PlayHistory h = new PlayHistory(2, "T", "A", "Al", 3, 300_000, T0);
        HistoryBackfill.Result r = HistoryBackfill.convert(
                Collections.singletonList(h), Collections.emptyList(), durations(), UTC);
        DailyRow d = find(r.daily, DAY, 2);
        assertEquals(30_000, d.playedMs);
        assertEquals(1, d.plays);
    }

    @Test
    public void playHistoryMatchedByEvents_notDoubleCounted() {
        PlayHistory h = new PlayHistory(1, "T", "A", "Al", 3, 200_000, T0 + 200_000);
        HistoryBackfill.Result r = HistoryBackfill.convert(Collections.singletonList(h), Arrays.asList(
                ev(1, ListenEvent.TYPE_PLAY, 0, T0),
                ev(1, ListenEvent.TYPE_FINISH, 199_000, T0 + 200_000)), durations(), UTC);
        DailyRow d = find(r.daily, DAY, 1);
        assertEquals(1, d.plays);
        assertEquals(200_000, d.playedMs);
    }

    @Test
    public void rerunDoesNotDoubleCount() {
        HistoryBackfill.Result r = HistoryBackfill.convert(Collections.emptyList(), Arrays.asList(
                ev(1, ListenEvent.TYPE_PLAY, 0, T0),
                ev(1, ListenEvent.TYPE_FINISH, 199_000, T0 + 200_000)), durations(), UTC);
        Set<String> existingDaily = new HashSet<>();
        Set<String> existingHourly = new HashSet<>();
        HistoryBackfill.Result first = HistoryBackfill.rowsToInsert(r, existingDaily, existingHourly);
        assertEquals(1, first.daily.size());
        for (DailyRow d : first.daily) existingDaily.add(HistoryBackfill.dailyKey(d.day, d.songId));
        for (int[] h : first.hourKeys()) existingHourly.add(HistoryBackfill.hourlyKey(h[0], h[1]));
        HistoryBackfill.Result second = HistoryBackfill.rowsToInsert(r, existingDaily, existingHourly);
        assertTrue(second.daily.isEmpty());
        assertTrue(second.hourKeys().isEmpty());
    }

    @Test
    public void emptyInput_emptyResult() {
        HistoryBackfill.Result r = HistoryBackfill.convert(new ArrayList<>(), new ArrayList<>(), durations(), UTC);
        assertTrue(r.daily.isEmpty());
    }
}
