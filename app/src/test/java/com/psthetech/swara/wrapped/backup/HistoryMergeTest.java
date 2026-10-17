package com.psthetech.swara.wrapped.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.wrapped.DailyRow;
import com.psthetech.swara.wrapped.HourlyRow;
import com.psthetech.swara.wrapped.SongInfo;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HistoryMergeTest {

    private static HistoryData data() {
        String stats = "{\"topSongIds\":[7,8],\"topVibeSongId\":7,\"mostRepeatedSongId\":-1,"
                + "\"mostSkippedSongId\":8,\"rediscoveredSongId\":-1,\"songs\":[{\"id\":7},{\"id\":8}]}";
        return new HistoryData(
                Arrays.asList(new DailyRow(20261017, 7, 60_000, 1, 1, 0, 0, 0),
                        new DailyRow(20261017, 8, 30_000, 1, 0, 1, 0, 0)),
                Arrays.asList(new HourlyRow(20261017, 23, 90_000)),
                Arrays.asList(new SongInfo(7, "A", "X", null, null, 1, 20261017),
                        new SongInfo(8, "B", "Y", null, null, 2, 20261017)),
                Arrays.asList(new HistoryData.RecapRow("month-2026-09", 20260901, 20260930, stats, 5L, true, true)),
                Arrays.asList(new HistoryData.OverrideRow(7, "SAD", 1L), new HistoryData.OverrideRow(8, "HAPPY", 1L)));
    }

    private static Map<Long, Long> ids() {
        Map<Long, Long> m = new HashMap<>();
        m.put(7L, 700L);   // matched
        m.put(8L, -555L);  // not on this phone
        return m;
    }

    @Test
    public void mapsIdsAndKeepsUnmatchedHistory() {
        HistoryMerge.Plan p = HistoryMerge.plan(data(), ids(), new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>());
        assertEquals(2, p.daily.size());
        assertEquals(700L, p.daily.get(0).songId);
        assertEquals(-555L, p.daily.get(1).songId);
        assertEquals(2, p.snapshots.size());
        assertEquals(1, p.hourly.size());
    }

    @Test
    public void reimportAddsNothing() {
        HistoryMerge.Plan first = HistoryMerge.plan(data(), ids(), new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>());
        Set<String> daily = new HashSet<>();
        for (DailyRow r : first.daily) daily.add(HistoryMerge.dailyKey(r.day, r.songId));
        Set<String> hourly = new HashSet<>();
        for (HourlyRow h : first.hourly) hourly.add(HistoryMerge.hourlyKey(h.day, h.hour));
        Set<String> recaps = new HashSet<>(Collections.singletonList("month-2026-09"));
        Set<Long> overrides = new HashSet<>(Collections.singletonList(700L));
        HistoryMerge.Plan second = HistoryMerge.plan(data(), ids(), daily, hourly, recaps, overrides);
        assertTrue(second.daily.isEmpty());
        assertTrue(second.hourly.isEmpty());
        assertTrue(second.recaps.isEmpty());
        assertTrue(second.overrides.isEmpty());
    }

    @Test
    public void existingRecapKept() {
        HistoryMerge.Plan p = HistoryMerge.plan(data(), ids(), new HashSet<>(), new HashSet<>(),
                new HashSet<>(Collections.singletonList("month-2026-09")), new HashSet<>());
        assertTrue(p.recaps.isEmpty());
    }

    @Test
    public void overrideOnlyForMatchedSongWithoutOne() {
        HistoryMerge.Plan p = HistoryMerge.plan(data(), ids(), new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>());
        assertEquals(1, p.overrides.size());
        assertEquals(700L, p.overrides.get(0).songId);
        HistoryMerge.Plan q = HistoryMerge.plan(data(), ids(), new HashSet<>(), new HashSet<>(), new HashSet<>(),
                new HashSet<>(Collections.singletonList(700L)));
        assertTrue(q.overrides.isEmpty());
    }

    @Test
    public void recapSongIdsRemapped_andBannerNotShownAgain() throws Exception {
        HistoryMerge.Plan p = HistoryMerge.plan(data(), ids(), new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>());
        JSONObject o = new JSONObject(p.recaps.get(0).statsJson);
        assertEquals(700L, o.getJSONArray("topSongIds").getLong(0));
        assertEquals(-555L, o.getJSONArray("topSongIds").getLong(1));
        assertEquals(700L, o.getLong("topVibeSongId"));
        assertEquals(-1L, o.getLong("mostRepeatedSongId"));
        assertEquals(-555L, o.getLong("mostSkippedSongId"));
        assertEquals(700L, o.getJSONArray("songs").getJSONObject(0).getLong("id"));
        assertTrue(p.recaps.get(0).bannerDismissed);
    }
}
