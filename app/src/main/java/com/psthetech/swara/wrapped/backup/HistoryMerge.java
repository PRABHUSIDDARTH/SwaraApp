package com.psthetech.swara.wrapped.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psthetech.swara.wrapped.DailyRow;
import com.psthetech.swara.wrapped.HourlyRow;
import com.psthetech.swara.wrapped.SongInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides which imported rows to add. Rows that already exist here win, so importing the same file
 * twice adds nothing.
 */
public final class HistoryMerge {

    private static final String[] SONG_ID_FIELDS =
            {"topVibeSongId", "mostRepeatedSongId", "mostSkippedSongId", "rediscoveredSongId"};

    public static final class Plan {
        public final List<DailyRow> daily = new ArrayList<>();
        public final List<HourlyRow> hourly = new ArrayList<>();
        /** Imported tags under this phone's ids. */
        public final List<SongInfo> snapshots = new ArrayList<>();
        public final List<HistoryData.RecapRow> recaps = new ArrayList<>();
        public final List<HistoryData.OverrideRow> overrides = new ArrayList<>();
        public int matchedSongs;
        public int unmatchedSongs;
    }

    private HistoryMerge() {}

    public static String dailyKey(int day, long songId) {
        return day + ":" + songId;
    }

    public static String hourlyKey(int day, int hour) {
        return day + ":" + hour;
    }

    /**
     * @param ids imported song id → id on this phone (from {@link SongMatcher#match})
     */
    @NonNull
    public static Plan plan(@NonNull HistoryData d, @NonNull Map<Long, Long> ids,
                            @NonNull Set<String> existingDaily, @NonNull Set<String> existingHourly,
                            @NonNull Set<String> existingRecaps, @NonNull Set<Long> existingOverrides) {
        Plan p = new Plan();
        // Two imported songs can land on one local song; their rows add up.
        Map<String, DailyRow> daily = new LinkedHashMap<>();
        for (DailyRow r : d.daily) {
            long id = map(ids, r.songId);
            String k = dailyKey(r.day, id);
            if (existingDaily.contains(k)) continue;
            DailyRow m = daily.get(k);
            if (m == null) {
                daily.put(k, new DailyRow(r.day, id, r.playedMs, r.plays, r.finishes, r.skips, r.repeats, r.vibeStarts));
            } else {
                m.playedMs += r.playedMs;
                m.plays += r.plays;
                m.finishes += r.finishes;
                m.skips += r.skips;
                m.repeats += r.repeats;
                m.vibeStarts += r.vibeStarts;
            }
        }
        p.daily.addAll(daily.values());
        for (HourlyRow h : d.hourly) {
            if (!existingHourly.contains(hourlyKey(h.day, h.hour))) p.hourly.add(h);
        }
        for (SongInfo s : d.songs) {
            long id = map(ids, s.songId);
            if (id > 0) p.matchedSongs++;
            else p.unmatchedSongs++;
            p.snapshots.add(new SongInfo(id, s.title, s.artist, s.artistKey, s.album, 0, s.firstHeardDay));
        }
        for (HistoryData.RecapRow r : d.recaps) {
            if (existingRecaps.contains(r.key)) continue;
            String stats = remapStats(r.statsJson, ids);
            if (stats == null) continue;
            p.recaps.add(new HistoryData.RecapRow(r.key, r.periodStart, r.periodEnd, stats, r.createdAt, r.seen, true));
        }
        for (HistoryData.OverrideRow o : d.overrides) {
            long id = map(ids, o.songId);
            if (id > 0 && !existingOverrides.contains(id)) p.overrides.add(new HistoryData.OverrideRow(id, o.label, o.setAt));
        }
        return p;
    }

    /** A song id from the other phone with no tags can't be matched, so it never reuses a local id. */
    private static long map(Map<Long, Long> ids, long id) {
        Long m = ids.get(id);
        if (m != null) return m;
        return id > 0 ? SongMatcher.syntheticId("id:" + id) : id;
    }

    /** The recap's song ids rewritten to this phone's ids; null when unreadable. */
    @Nullable
    static String remapStats(String json, Map<Long, Long> ids) {
        try {
            JSONObject o = new JSONObject(json);
            // Album ids differ between phones; the album slide is left out.
            if (o.has("topAlbumId")) o.put("topAlbumId", -1);
            JSONArray top = o.optJSONArray("topSongIds");
            if (top != null) {
                for (int i = 0; i < top.length(); i++) top.put(i, mapKeepingNone(ids, top.getLong(i)));
            }
            for (String f : SONG_ID_FIELDS) {
                if (o.has(f)) o.put(f, mapKeepingNone(ids, o.getLong(f)));
            }
            JSONArray songs = o.optJSONArray("songs");
            if (songs != null) {
                for (int i = 0; i < songs.length(); i++) {
                    JSONObject s = songs.getJSONObject(i);
                    s.put("id", mapKeepingNone(ids, s.getLong("id")));
                    s.put("albumId", 0);
                }
            }
            return o.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    private static long mapKeepingNone(Map<Long, Long> ids, long id) {
        return id == -1 ? -1 : map(ids, id);
    }
}
