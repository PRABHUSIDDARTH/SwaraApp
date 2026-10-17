package com.psthetech.swara.wrapped.backup;

import androidx.annotation.NonNull;

import com.psthetech.swara.wrapped.DailyRow;
import com.psthetech.swara.wrapped.HourlyRow;
import com.psthetech.swara.wrapped.SongInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** JSON form of {@link HistoryData} (format "swara-wrapped-history", version 1). */
public final class HistoryFile {

    public static final String FORMAT = "swara-wrapped-history";
    public static final int VERSION = 1;

    private HistoryFile() {}

    @NonNull
    public static String encode(@NonNull HistoryData d) {
        try {
            JSONObject o = new JSONObject();
            o.put("format", FORMAT).put("version", VERSION);
            JSONArray daily = new JSONArray();
            for (DailyRow r : d.daily) {
                daily.put(new JSONArray().put(r.day).put(r.songId).put(r.playedMs).put(r.plays)
                        .put(r.finishes).put(r.skips).put(r.repeats).put(r.vibeStarts));
            }
            o.put("daily", daily);
            JSONArray hourly = new JSONArray();
            for (HourlyRow h : d.hourly) hourly.put(new JSONArray().put(h.day).put(h.hour).put(h.playedMs));
            o.put("hourly", hourly);
            JSONArray songs = new JSONArray();
            for (SongInfo s : d.songs) {
                JSONObject j = new JSONObject();
                j.put("id", s.songId).put("first", s.firstHeardDay);
                if (s.title != null) j.put("title", s.title);
                if (s.artist != null) j.put("artist", s.artist);
                if (s.artistKey != null) j.put("artistKey", s.artistKey);
                if (s.album != null) j.put("album", s.album);
                songs.put(j);
            }
            o.put("songs", songs);
            JSONArray recaps = new JSONArray();
            for (HistoryData.RecapRow r : d.recaps) {
                recaps.put(new JSONObject().put("key", r.key).put("start", r.periodStart).put("end", r.periodEnd)
                        .put("stats", r.statsJson).put("createdAt", r.createdAt)
                        .put("seen", r.seen).put("bannerDismissed", r.bannerDismissed));
            }
            o.put("recaps", recaps);
            JSONArray overrides = new JSONArray();
            for (HistoryData.OverrideRow r : d.overrides) {
                overrides.put(new JSONObject().put("id", r.songId).put("label", r.label).put("setAt", r.setAt));
            }
            o.put("moodOverrides", overrides);
            return o.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @throws IllegalArgumentException when {@code json} is not a history file this app can read. */
    @NonNull
    public static HistoryData decode(@NonNull String json) {
        try {
            JSONObject o = new JSONObject(json);
            if (!FORMAT.equals(o.optString("format")) || o.optInt("version") < 1 || o.optInt("version") > VERSION) {
                throw new IllegalArgumentException("not a Swara listening history file");
            }
            List<DailyRow> daily = new ArrayList<>();
            JSONArray a = o.getJSONArray("daily");
            for (int i = 0; i < a.length(); i++) {
                JSONArray r = a.getJSONArray(i);
                daily.add(new DailyRow(r.getInt(0), r.getLong(1), r.getLong(2), r.getInt(3), r.getInt(4),
                        r.getInt(5), r.getInt(6), r.getInt(7)));
            }
            List<HourlyRow> hourly = new ArrayList<>();
            a = o.getJSONArray("hourly");
            for (int i = 0; i < a.length(); i++) {
                JSONArray r = a.getJSONArray(i);
                hourly.add(new HourlyRow(r.getInt(0), r.getInt(1), r.getLong(2)));
            }
            List<SongInfo> songs = new ArrayList<>();
            a = o.getJSONArray("songs");
            for (int i = 0; i < a.length(); i++) {
                JSONObject j = a.getJSONObject(i);
                songs.add(new SongInfo(j.getLong("id"), opt(j, "title"), opt(j, "artist"), opt(j, "artistKey"),
                        opt(j, "album"), 0, j.optInt("first")));
            }
            List<HistoryData.RecapRow> recaps = new ArrayList<>();
            a = o.getJSONArray("recaps");
            for (int i = 0; i < a.length(); i++) {
                JSONObject j = a.getJSONObject(i);
                recaps.add(new HistoryData.RecapRow(j.getString("key"), j.getInt("start"), j.getInt("end"),
                        j.getString("stats"), j.optLong("createdAt"), j.optBoolean("seen"),
                        j.optBoolean("bannerDismissed")));
            }
            List<HistoryData.OverrideRow> overrides = new ArrayList<>();
            a = o.getJSONArray("moodOverrides");
            for (int i = 0; i < a.length(); i++) {
                JSONObject j = a.getJSONObject(i);
                overrides.add(new HistoryData.OverrideRow(j.getLong("id"), j.getString("label"), j.optLong("setAt")));
            }
            return new HistoryData(daily, hourly, songs, recaps, overrides);
        } catch (JSONException e) {
            throw new IllegalArgumentException("unreadable listening history file", e);
        }
    }

    private static String opt(JSONObject j, String name) {
        return j.has(name) ? j.optString(name) : null;
    }
}
