package com.psthetech.swara.wrapped.backup;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.psthetech.swara.SwaraApplication;
import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.data.db.dao.WrappedDao;
import com.psthetech.swara.data.db.entity.ListenDaily;
import com.psthetech.swara.data.db.entity.ListenHourly;
import com.psthetech.swara.data.db.entity.MoodOverride;
import com.psthetech.swara.data.db.entity.Recap;
import com.psthetech.swara.data.db.entity.SongSnapshot;
import com.psthetech.swara.data.repository.MusicRepository;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.wrapped.DailyRow;
import com.psthetech.swara.wrapped.HourlyRow;
import com.psthetech.swara.wrapped.RoomListenSink;
import com.psthetech.swara.wrapped.SongInfo;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Saves Wrapped listening history to a file the user picks, and merges one back in. */
public final class HistoryBackupRepository {

    private static final String TAG = "HistoryBackup";

    public interface Callback<T> {
        void onDone(T result);

        void onError(@NonNull Exception e);
    }

    public static final class ImportResult {
        public final int matchedSongs;
        public final int unmatchedSongs;
        public final int daysAdded;

        ImportResult(int matchedSongs, int unmatchedSongs, int daysAdded) {
            this.matchedSongs = matchedSongs;
            this.unmatchedSongs = unmatchedSongs;
            this.daysAdded = daysAdded;
        }
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());

    public HistoryBackupRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    public void export(@NonNull Uri target, @NonNull Callback<Integer> callback) {
        SwaraApplication.getInstance().getIoExecutor().execute(() -> {
            try {
                HistoryData d = read();
                byte[] bytes = HistoryFile.encode(d).getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = context.getContentResolver().openOutputStream(target, "wt")) {
                    if (out == null) throw new java.io.IOException("cannot open " + target);
                    out.write(bytes);
                }
                main.post(() -> callback.onDone(d.songs.size()));
            } catch (Exception e) {
                Log.w(TAG, "Export failed", e);
                main.post(() -> callback.onError(e));
            }
        });
    }

    public void importFrom(@NonNull Uri source, @NonNull Callback<ImportResult> callback) {
        SwaraApplication.getInstance().getIoExecutor().execute(() -> {
            try {
                String json;
                try (InputStream in = context.getContentResolver().openInputStream(source)) {
                    if (in == null) throw new java.io.IOException("cannot open " + source);
                    json = readAll(in);
                }
                ImportResult r = merge(HistoryFile.decode(json));
                main.post(() -> callback.onDone(r));
            } catch (Exception e) {
                Log.w(TAG, "Import failed", e);
                main.post(() -> callback.onError(e));
            }
        });
    }

    @WorkerThread
    @NonNull
    HistoryData read() {
        AppDatabase db = AppDatabase.getInstance(context);
        WrappedDao dao = db.wrappedDao();
        List<DailyRow> daily = new ArrayList<>();
        for (ListenDaily r : dao.allDaily()) {
            daily.add(new DailyRow(r.day, r.songId, r.playedMs, r.plays, r.finishes, r.skips, r.repeats, r.vibeStarts));
        }
        List<HourlyRow> hourly = new ArrayList<>();
        for (ListenHourly h : dao.allHourly()) hourly.add(new HourlyRow(h.day, h.hour, h.playedMs));
        List<SongInfo> songs = new ArrayList<>();
        for (SongSnapshot s : dao.snapshots()) {
            songs.add(new SongInfo(s.songId, s.title, s.artist, s.artistKey, s.album, 0, s.firstHeardDay));
        }
        List<HistoryData.RecapRow> recaps = new ArrayList<>();
        for (Recap r : dao.recaps()) {
            recaps.add(new HistoryData.RecapRow(r.recapKey, r.periodStart, r.periodEnd, r.statsJson,
                    r.createdAt, r.seen, r.bannerDismissed));
        }
        List<HistoryData.OverrideRow> overrides = new ArrayList<>();
        for (MoodOverride o : db.moodOverrideDao().getAll()) {
            overrides.add(new HistoryData.OverrideRow(o.songId, o.label, o.setAt));
        }
        return new HistoryData(daily, hourly, songs, recaps, overrides);
    }

    @WorkerThread
    @NonNull
    ImportResult merge(@NonNull HistoryData d) {
        AppDatabase db = AppDatabase.getInstance(context);
        WrappedDao dao = db.wrappedDao();
        List<Song> library = new MusicRepository(context).loadAllSongsBlocking();
        Map<Long, Song> byId = new HashMap<>();
        List<SongInfo> local = new ArrayList<>(library.size());
        for (Song s : library) {
            byId.put(s.getId(), s);
            local.add(new SongInfo(s.getId(), s.getTitle(), s.getArtist(), null, s.getAlbum(), s.getAlbumId(), 0));
        }
        Map<Long, Long> ids = SongMatcher.match(d.songs, local);

        Set<String> daily = new HashSet<>();
        for (ListenDaily r : dao.allDaily()) daily.add(HistoryMerge.dailyKey(r.day, r.songId));
        Set<String> hourly = new HashSet<>();
        for (ListenHourly h : dao.allHourly()) hourly.add(HistoryMerge.hourlyKey(h.day, h.hour));
        Set<String> recaps = new HashSet<>(dao.recapKeys());
        Set<Long> overrides = new HashSet<>();
        for (MoodOverride o : db.moodOverrideDao().getAll()) overrides.add(o.songId);

        HistoryMerge.Plan p = HistoryMerge.plan(d, ids, daily, hourly, recaps, overrides);
        db.runInTransaction(() -> {
            for (DailyRow r : p.daily) {
                ListenDaily e = new ListenDaily();
                e.day = r.day;
                e.songId = r.songId;
                e.playedMs = r.playedMs;
                e.plays = r.plays;
                e.finishes = r.finishes;
                e.skips = r.skips;
                e.repeats = r.repeats;
                e.vibeStarts = r.vibeStarts;
                dao.putDaily(e);
            }
            for (HourlyRow h : p.hourly) {
                ListenHourly e = new ListenHourly();
                e.day = h.day;
                e.hour = h.hour;
                e.playedMs = h.playedMs;
                dao.putHourly(e);
            }
            for (SongInfo s : p.snapshots) {
                Song song = byId.get(s.songId);
                // Songs on this phone keep their own tags; only the first-heard day can move earlier.
                SongSnapshot snap = song != null ? RoomListenSink.toSnapshot(song, s.firstHeardDay) : fromInfo(s);
                dao.upsertSnapshot(snap);
            }
            for (HistoryData.RecapRow r : p.recaps) {
                Recap e = new Recap();
                e.recapKey = r.key;
                e.periodStart = r.periodStart;
                e.periodEnd = r.periodEnd;
                e.statsJson = r.statsJson;
                e.createdAt = r.createdAt;
                e.seen = r.seen;
                e.bannerDismissed = r.bannerDismissed;
                dao.upsertRecap(e);
            }
            for (HistoryData.OverrideRow o : p.overrides) {
                db.moodOverrideDao().upsert(new MoodOverride(o.songId, o.label, o.setAt));
            }
        });
        Set<Integer> days = new HashSet<>();
        for (DailyRow r : p.daily) days.add(r.day);
        return new ImportResult(p.matchedSongs, p.unmatchedSongs, days.size());
    }

    private static SongSnapshot fromInfo(SongInfo s) {
        SongSnapshot snap = new SongSnapshot();
        snap.songId = s.songId;
        snap.title = s.title;
        snap.artist = s.artist;
        snap.artistKey = s.artistKey;
        snap.album = s.album;
        snap.albumId = 0;
        snap.firstHeardDay = s.firstHeardDay;
        return snap;
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toString(StandardCharsets.UTF_8.name());
    }
}
