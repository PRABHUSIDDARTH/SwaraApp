package com.psthetech.swara.wrapped;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.data.db.dao.WrappedDao;
import com.psthetech.swara.data.db.entity.ListenEvent;
import com.psthetech.swara.data.db.entity.PlayHistory;
import com.psthetech.swara.data.db.entity.SongSnapshot;
import com.psthetech.swara.data.repository.MusicRepository;
import com.psthetech.swara.domain.model.Song;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;

/**
 * One-time conversion of the pre-Wrapped history (play_history + listen_events) into the
 * Wrapped aggregates, so a fresh upgrade already has stats. Idempotent: rows that already
 * exist are never added again.
 */
public final class HistoryBackfill {

    private static final String TAG = "HistoryBackfill";
    private static final String PREFS = "swara_wrapped";
    private static final String KEY_DONE = "wrapped_backfill_done";
    /** Listening credited to a play with no recorded end. */
    static final long UNMATCHED_PLAY_MS = 30_000L;

    private HistoryBackfill() {}

    /** Converted rows: daily per (day, songId) and hourly ms per (day, hour). */
    public static final class Result {
        public final List<DailyRow> daily;
        /** key = day * 100 + hour → ms. */
        final Map<Long, Long> hourly;

        Result(List<DailyRow> daily, Map<Long, Long> hourly) {
            this.daily = daily;
            this.hourly = hourly;
        }

        public Long hourlyMs(int day, int hour) {
            return hourly.get((long) day * 100 + hour);
        }

        /** Each element is {day, hour}. */
        public List<int[]> hourKeys() {
            List<int[]> out = new ArrayList<>();
            for (Long k : hourly.keySet()) out.add(new int[]{(int) (k / 100), (int) (k % 100)});
            return out;
        }
    }

    static String dailyKey(int day, long songId) {
        return day + ":" + songId;
    }

    static String hourlyKey(int day, int hour) {
        return day + ":" + hour;
    }

    public static Result convert(@NonNull List<PlayHistory> plays, @NonNull List<ListenEvent> events,
                                 @NonNull Map<Long, Long> durationBySong, @NonNull TimeZone tz) {
        Map<String, DailyRow> daily = new LinkedHashMap<>();
        Map<Long, Long> hourly = new TreeMap<>();
        Calendar cal = Calendar.getInstance(tz);

        List<ListenEvent> sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparingLong((ListenEvent e) -> e.timestamp).thenComparingLong(e -> e.id));

        // Open PLAY per song: timestamp of its start.
        Map<Long, Long> openPlay = new LinkedHashMap<>();
        // Song plays covered by events, for matching play_history.
        Map<Long, List<long[]>> sessions = new LinkedHashMap<>();

        for (ListenEvent e : sorted) {
            long dur = durationBySong.getOrDefault(e.songId, 0L);
            if (e.type == ListenEvent.TYPE_PLAY) {
                // Any other song still open was left without a recorded end.
                for (Map.Entry<Long, Long> o : new ArrayList<>(openPlay.entrySet())) {
                    long oDur = durationBySong.getOrDefault(o.getKey(), 0L);
                    credit(daily, hourly, cal, o.getKey(), o.getValue(), capped(oDur), 0, 0, 0);
                    sessions.computeIfAbsent(o.getKey(), k -> new ArrayList<>()).add(new long[]{o.getValue(), e.timestamp});
                    openPlay.remove(o.getKey());
                }
                openPlay.put(e.songId, e.timestamp);
                row(daily, cal, e.songId, e.timestamp).plays += 1;
            } else {
                Long start = openPlay.remove(e.songId);
                long at = start != null ? start : e.timestamp;
                long played = e.type == ListenEvent.TYPE_FINISH && dur > 0 ? dur : Math.max(0, e.positionMs);
                if (dur > 0) played = Math.min(played, dur);
                credit(daily, hourly, cal, e.songId, at, played,
                        e.type == ListenEvent.TYPE_FINISH ? 1 : 0,
                        e.type == ListenEvent.TYPE_SKIP ? 1 : 0,
                        e.type == ListenEvent.TYPE_REPEAT ? 1 : 0);
                sessions.computeIfAbsent(e.songId, k -> new ArrayList<>()).add(new long[]{at, e.timestamp});
            }
        }
        for (Map.Entry<Long, Long> o : openPlay.entrySet()) {
            long oDur = durationBySong.getOrDefault(o.getKey(), 0L);
            credit(daily, hourly, cal, o.getKey(), o.getValue(), capped(oDur), 0, 0, 0);
            sessions.computeIfAbsent(o.getKey(), k -> new ArrayList<>()).add(new long[]{o.getValue(), o.getValue()});
        }

        for (PlayHistory h : plays) {
            if (coveredByEvents(sessions.get(h.songId), h.playedAt)) continue;
            long dur = h.duration > 0 ? h.duration : durationBySong.getOrDefault(h.songId, 0L);
            row(daily, cal, h.songId, h.playedAt).plays += 1;
            credit(daily, hourly, cal, h.songId, h.playedAt, capped(dur), 0, 0, 0);
        }
        return new Result(new ArrayList<>(daily.values()), hourly);
    }

    private static long capped(long duration) {
        return duration > 0 ? Math.min(duration, UNMATCHED_PLAY_MS) : UNMATCHED_PLAY_MS;
    }

    /** A play_history row is covered if an event session of that song ended within a minute of it. */
    private static boolean coveredByEvents(@Nullable List<long[]> s, long playedAt) {
        if (s == null) return false;
        for (long[] x : s) {
            if (playedAt >= x[0] - 60_000L && playedAt <= x[1] + 60_000L) return true;
        }
        return false;
    }

    private static DailyRow row(Map<String, DailyRow> daily, Calendar cal, long songId, long ts) {
        cal.setTimeInMillis(ts);
        int day = Day.of(cal);
        return daily.computeIfAbsent(dailyKey(day, songId), k -> new DailyRow(day, songId, 0, 0, 0, 0, 0, 0));
    }

    private static void credit(Map<String, DailyRow> daily, Map<Long, Long> hourly, Calendar cal,
                               long songId, long ts, long playedMs, int fin, int skip, int rep) {
        DailyRow r = row(daily, cal, songId, ts);
        r.playedMs += playedMs;
        r.finishes += fin;
        r.skips += skip;
        r.repeats += rep;
        if (playedMs > 0) {
            cal.setTimeInMillis(ts);
            long key = (long) Day.of(cal) * 100 + cal.get(Calendar.HOUR_OF_DAY);
            hourly.merge(key, playedMs, Long::sum);
        }
    }

    /** Drops every row whose key already exists, so a re-run never double-counts. */
    static Result rowsToInsert(Result converted, Set<String> existingDaily, Set<String> existingHourly) {
        List<DailyRow> d = new ArrayList<>();
        for (DailyRow r : converted.daily) {
            if (!existingDaily.contains(dailyKey(r.day, r.songId))) d.add(r);
        }
        Map<Long, Long> h = new TreeMap<>();
        for (Map.Entry<Long, Long> e : converted.hourly.entrySet()) {
            int day = (int) (e.getKey() / 100), hour = (int) (e.getKey() % 100);
            if (!existingHourly.contains(hourlyKey(day, hour))) h.put(e.getKey(), e.getValue());
        }
        return new Result(d, h);
    }

    /** Runs the backfill once (DB executor / worker thread only). */
    public static void runOnce(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_DONE, false)) return;
        try {
            AppDatabase db = AppDatabase.getInstance(context);
            List<PlayHistory> plays = db.playHistoryDao().getAllForBackfill();
            List<ListenEvent> events = db.listenEventDao().all();
            Map<Long, Long> durations = new LinkedHashMap<>();
            Map<Long, SongSnapshot> snaps = new LinkedHashMap<>();
            for (PlayHistory h : plays) {
                durations.put(h.songId, h.duration);
                snaps.putIfAbsent(h.songId, snapshotFromHistory(h));
            }
            try {
                new MusicRepository(context).loadAllSongsBlocking(); // warms the canonical cache
            } catch (SecurityException noPermission) {
                // Without media access the history's own tags and capped durations are used.
            }
            for (ListenEvent e : events) {
                Song s = MusicRepository.getCanonicalSong(e.songId);
                if (s != null) {
                    durations.putIfAbsent(e.songId, s.getDuration());
                    snaps.put(e.songId, RoomListenSink.toSnapshot(s, 0));
                }
            }
            Result converted = convert(plays, events, durations, TimeZone.getDefault());
            WrappedDao dao = db.wrappedDao();
            db.runInTransaction(() -> {
                Set<String> existingDaily = new HashSet<>();
                Set<String> existingHourly = new HashSet<>();
                for (DailyRow r : converted.daily) {
                    if (dao.countDailyFor(r.songId, r.day) > 0) existingDaily.add(dailyKey(r.day, r.songId));
                }
                for (int[] k : converted.hourKeys()) {
                    if (dao.hourlyRow(k[0], k[1]) != null) existingHourly.add(hourlyKey(k[0], k[1]));
                }
                Result toInsert = rowsToInsert(converted, existingDaily, existingHourly);
                Map<Long, Integer> firstDay = new LinkedHashMap<>();
                for (DailyRow r : toInsert.daily) {
                    dao.addDaily(r.day, r.songId, r.playedMs, r.plays, r.finishes, r.skips, r.repeats, 0);
                    firstDay.merge(r.songId, r.day, Math::min);
                }
                for (int[] k : toInsert.hourKeys()) {
                    dao.addHourly(k[0], k[1], toInsert.hourlyMs(k[0], k[1]));
                }
                for (Map.Entry<Long, Integer> f : firstDay.entrySet()) {
                    SongSnapshot s = snaps.get(f.getKey());
                    if (s == null) continue;
                    s.firstHeardDay = f.getValue();
                    dao.upsertSnapshot(s);
                }
            });
            prefs.edit().putBoolean(KEY_DONE, true).apply();
            Log.d(TAG, "Backfilled " + converted.daily.size() + " daily rows");
        } catch (Exception e) {
            Log.w(TAG, "Backfill failed; will retry next start", e);
        }
    }

    private static SongSnapshot snapshotFromHistory(PlayHistory h) {
        SongSnapshot s = new SongSnapshot();
        s.songId = h.songId;
        s.title = h.title;
        s.artist = h.artist;
        s.artistKey = com.psthetech.swara.util.ArtistIdentityHelper.getCanonicalKey(h.artist);
        s.album = h.album;
        s.albumId = h.albumId;
        return s;
    }
}
