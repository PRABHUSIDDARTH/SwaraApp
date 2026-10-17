package com.psthetech.swara.wrapped;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every number the Wrapped slides need for one period. Immutable; serializable to JSON. */
public final class WrappedStats {

    public final Period period;
    public final long totalMs;
    public final List<Long> topSongIds;
    public final List<String> topArtistKeys;
    public final long topAlbumId;
    public final Map<MoodBucket, Long> moodMs;
    public final long moodKnownMs;
    public final int vibeStarts;
    public final long topVibeSongId;
    public final int peakHour;
    public final int busiestWeekday;
    public final int longestStreakDays;
    public final int listeningDays;
    public final long mostRepeatedSongId;
    /** Repeat count of {@link #mostRepeatedSongId} (0 when none). */
    public final int mostRepeatedCount;
    public final long mostSkippedSongId;
    public final int newSongs;
    public final long rediscoveredSongId;
    public final int distinctSongs;
    public final long topSongMs;
    public final long nightMs;
    public final long morningMs;
    /** Display names for each top artist key (same order as topArtistKeys). */
    public final List<String> topArtistNames;
    /** Info for every song id referenced above. */
    public final Map<Long, SongInfo> songs;

    WrappedStats(Builder b) {
        period = b.period;
        totalMs = b.totalMs;
        topSongIds = Collections.unmodifiableList(new ArrayList<>(b.topSongIds));
        topArtistKeys = Collections.unmodifiableList(new ArrayList<>(b.topArtistKeys));
        topArtistNames = Collections.unmodifiableList(new ArrayList<>(b.topArtistNames));
        topAlbumId = b.topAlbumId;
        EnumMap<MoodBucket, Long> m = new EnumMap<>(MoodBucket.class);
        m.putAll(b.moodMs);
        moodMs = Collections.unmodifiableMap(m);
        moodKnownMs = b.moodKnownMs;
        vibeStarts = b.vibeStarts;
        topVibeSongId = b.topVibeSongId;
        peakHour = b.peakHour;
        busiestWeekday = b.busiestWeekday;
        longestStreakDays = b.longestStreakDays;
        listeningDays = b.listeningDays;
        mostRepeatedSongId = b.mostRepeatedSongId;
        mostRepeatedCount = b.mostRepeatedCount;
        mostSkippedSongId = b.mostSkippedSongId;
        newSongs = b.newSongs;
        rediscoveredSongId = b.rediscoveredSongId;
        distinctSongs = b.distinctSongs;
        topSongMs = b.topSongMs;
        nightMs = b.nightMs;
        morningMs = b.morningMs;
        songs = Collections.unmodifiableMap(new LinkedHashMap<>(b.songs));
    }

    static final class Builder {
        Period period;
        long totalMs;
        List<Long> topSongIds = new ArrayList<>();
        List<String> topArtistKeys = new ArrayList<>();
        List<String> topArtistNames = new ArrayList<>();
        long topAlbumId = -1;
        Map<MoodBucket, Long> moodMs = new EnumMap<>(MoodBucket.class);
        long moodKnownMs;
        int vibeStarts;
        long topVibeSongId = -1;
        int peakHour = -1;
        int busiestWeekday = -1;
        int longestStreakDays;
        int listeningDays;
        long mostRepeatedSongId = -1;
        int mostRepeatedCount;
        long mostSkippedSongId = -1;
        int newSongs;
        long rediscoveredSongId = -1;
        int distinctSongs;
        long topSongMs;
        long nightMs;
        long morningMs;
        Map<Long, SongInfo> songs = new LinkedHashMap<>();
    }

    // ===== JSON =====

    @NonNull
    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("v", 1);
            o.put("kind", period.kind.name()).put("start", period.start).put("end", period.end);
            o.put("totalMs", totalMs);
            JSONArray ts = new JSONArray();
            for (Long id : topSongIds) ts.put(id);
            o.put("topSongIds", ts);
            JSONArray ta = new JSONArray();
            for (String k : topArtistKeys) ta.put(k);
            o.put("topArtistKeys", ta);
            JSONArray tn = new JSONArray();
            for (String n : topArtistNames) tn.put(n);
            o.put("topArtistNames", tn);
            o.put("topAlbumId", topAlbumId);
            JSONObject mood = new JSONObject();
            for (Map.Entry<MoodBucket, Long> e : moodMs.entrySet()) mood.put(e.getKey().name(), e.getValue());
            o.put("moodMs", mood);
            o.put("moodKnownMs", moodKnownMs);
            o.put("vibeStarts", vibeStarts).put("topVibeSongId", topVibeSongId);
            o.put("peakHour", peakHour).put("busiestWeekday", busiestWeekday);
            o.put("longestStreakDays", longestStreakDays).put("listeningDays", listeningDays);
            o.put("mostRepeatedCount", mostRepeatedCount);
            o.put("mostRepeatedSongId", mostRepeatedSongId).put("mostSkippedSongId", mostSkippedSongId);
            o.put("newSongs", newSongs).put("rediscoveredSongId", rediscoveredSongId);
            o.put("distinctSongs", distinctSongs).put("topSongMs", topSongMs);
            o.put("nightMs", nightMs).put("morningMs", morningMs);
            JSONArray ss = new JSONArray();
            for (SongInfo s : songs.values()) {
                JSONObject j = new JSONObject();
                j.put("id", s.songId).put("albumId", s.albumId).put("first", s.firstHeardDay);
                if (s.title != null) j.put("title", s.title);
                if (s.artist != null) j.put("artist", s.artist);
                if (s.artistKey != null) j.put("artistKey", s.artistKey);
                if (s.album != null) j.put("album", s.album);
                ss.put(j);
            }
            o.put("songs", ss);
            return o.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    @NonNull
    public static WrappedStats fromJson(@NonNull String json) {
        try {
            JSONObject o = new JSONObject(json);
            Builder b = new Builder();
            b.period = new Period(Period.Kind.valueOf(o.getString("kind")), o.getInt("start"), o.getInt("end"));
            b.totalMs = o.getLong("totalMs");
            JSONArray ts = o.getJSONArray("topSongIds");
            for (int i = 0; i < ts.length(); i++) b.topSongIds.add(ts.getLong(i));
            JSONArray ta = o.getJSONArray("topArtistKeys");
            for (int i = 0; i < ta.length(); i++) b.topArtistKeys.add(ta.getString(i));
            JSONArray tn = o.optJSONArray("topArtistNames");
            if (tn != null) for (int i = 0; i < tn.length(); i++) b.topArtistNames.add(tn.getString(i));
            b.topAlbumId = o.getLong("topAlbumId");
            JSONObject mood = o.getJSONObject("moodMs");
            for (MoodBucket m : MoodBucket.values()) {
                if (mood.has(m.name())) b.moodMs.put(m, mood.getLong(m.name()));
            }
            b.moodKnownMs = o.getLong("moodKnownMs");
            b.vibeStarts = o.getInt("vibeStarts");
            b.topVibeSongId = o.getLong("topVibeSongId");
            b.peakHour = o.getInt("peakHour");
            b.busiestWeekday = o.getInt("busiestWeekday");
            b.longestStreakDays = o.getInt("longestStreakDays");
            b.listeningDays = o.getInt("listeningDays");
            b.mostRepeatedSongId = o.getLong("mostRepeatedSongId");
            b.mostRepeatedCount = o.optInt("mostRepeatedCount", 0);
            b.mostSkippedSongId = o.getLong("mostSkippedSongId");
            b.newSongs = o.getInt("newSongs");
            b.rediscoveredSongId = o.getLong("rediscoveredSongId");
            b.distinctSongs = o.getInt("distinctSongs");
            b.topSongMs = o.getLong("topSongMs");
            b.nightMs = o.getLong("nightMs");
            b.morningMs = o.getLong("morningMs");
            JSONArray ss = o.getJSONArray("songs");
            for (int i = 0; i < ss.length(); i++) {
                JSONObject j = ss.getJSONObject(i);
                long id = j.getLong("id");
                b.songs.put(id, new SongInfo(id,
                        j.has("title") ? j.getString("title") : null,
                        j.has("artist") ? j.getString("artist") : null,
                        j.has("artistKey") ? j.getString("artistKey") : null,
                        j.has("album") ? j.getString("album") : null,
                        j.getLong("albumId"), j.getInt("first")));
            }
            return new WrappedStats(b);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Bad Wrapped stats JSON", e);
        }
    }
}
