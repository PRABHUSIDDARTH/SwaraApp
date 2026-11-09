package com.psthetech.swara.wrapped;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.data.db.dao.WrappedDao;
import com.psthetech.swara.data.db.entity.ListenDaily;
import com.psthetech.swara.data.db.entity.ListenHourly;
import com.psthetech.swara.data.db.entity.MoodOverride;
import com.psthetech.swara.data.db.entity.Recap;
import com.psthetech.swara.data.db.entity.SongMood;
import com.psthetech.swara.data.db.entity.SongSnapshot;
import com.psthetech.swara.data.repository.ArtworkRepository;
import com.psthetech.swara.data.repository.CustomArtworkStore;
import com.psthetech.swara.vibe.MoodLabel;
import com.psthetech.swara.vibe.MoodResolver;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/** Loads Wrapped stats (live or stored) and slide artwork. Worker threads only. */
public final class RecapRepository {

    private static final int ART_PX = 720;

    private final Context context;
    private final WrappedDao dao;
    private final AppDatabase db;

    public RecapRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.db = AppDatabase.getInstance(this.context);
        this.dao = db.wrappedDao();
    }

    public static int today() {
        return Day.of(System.currentTimeMillis(), TimeZone.getDefault());
    }

    public static Period thisMonth() {
        Calendar c = Calendar.getInstance();
        return Period.month(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
    }

    public static Period thisYear() {
        return Period.yearToDate(Calendar.getInstance().get(Calendar.YEAR), today());
    }

    @WorkerThread
    public Period allTime() {
        Integer first = dao.firstDay();
        int t = today();
        return Period.allTime(first != null ? first : t, t);
    }

    /** Computes stats for {@code p} from the live aggregates. */
    @WorkerThread
    @NonNull
    public WrappedStats statsFor(@NonNull Period p) {
        List<DailyRow> in = toRows(dao.daily(p.start, p.end));
        List<DailyRow> before = toRows(dao.dailyBefore(p.start));
        List<HourlyRow> hourly = new ArrayList<>();
        for (ListenHourly h : dao.hourly(p.start, p.end)) hourly.add(new HourlyRow(h.day, h.hour, h.playedMs));
        Map<Long, SongInfo> songs = new HashMap<>();
        for (SongSnapshot s : dao.snapshots()) {
            songs.put(s.songId, new SongInfo(s.songId, s.title, s.artist, s.artistKey, s.album, s.albumId, s.firstHeardDay));
        }
        return WrappedStatsEngine.compute(p, in, before, hourly, songs, resolvedMoods());
    }

    /** Stored moods with the user's corrections applied (no on-demand analysis). */
    private Map<Long, double[]> resolvedMoods() {
        Map<Long, double[]> analyzed = new HashMap<>();
        for (SongMood m : db.moodDao().getAll()) {
            if (m.status == SongMood.STATUS_OK) analyzed.put(m.songId, new double[]{m.valence, m.energy});
        }
        Map<Long, double[]> out = new HashMap<>(analyzed);
        for (MoodOverride o : db.moodOverrideDao().getAll()) {
            out.put(o.songId, MoodResolver.resolve(analyzed.get(o.songId), MoodLabel.fromName(o.label)));
        }
        return out;
    }

    private static List<DailyRow> toRows(List<ListenDaily> rows) {
        List<DailyRow> out = new ArrayList<>(rows.size());
        for (ListenDaily r : rows) {
            out.add(new DailyRow(r.day, r.songId, r.playedMs, r.plays, r.finishes, r.skips, r.repeats, r.vibeStarts));
        }
        return out;
    }

    @WorkerThread
    @Nullable
    public WrappedStats stored(@NonNull String key) {
        Recap r = dao.recap(key);
        if (r == null) return null;
        try {
            return WrappedStats.fromJson(r.statsJson);
        } catch (IllegalArgumentException bad) {
            return null;
        }
    }

    @WorkerThread
    public void store(@NonNull String key, @NonNull WrappedStats s) {
        Recap r = dao.recap(key);
        if (r == null) {
            r = new Recap();
            r.recapKey = key;
        }
        r.periodStart = s.period.start;
        r.periodEnd = s.period.end;
        r.statsJson = s.toJson();
        // A rebuild keeps the first creation time so its Home banner doesn't come back.
        if (r.createdAt == 0) r.createdAt = System.currentTimeMillis();
        dao.upsertRecap(r);
    }

    @WorkerThread
    public void markSeen(@NonNull String key) {
        dao.markSeen(key);
    }

    /** Artwork for every slide that shows one, keyed by song id. */
    @WorkerThread
    @NonNull
    public Map<Long, Bitmap> artFor(@NonNull WrappedStats s) {
        Map<Long, Bitmap> out = new LinkedHashMap<>();
        for (SlideType t : SlideType.values()) {
            long id = com.psthetech.swara.wrapped.render.SlideRenderer.artSongFor(t, s);
            if (id == -1 || out.containsKey(id)) continue;
            Bitmap b = loadArt(s.songs.get(id), id);
            if (b != null) out.put(id, b);
        }
        return out;
    }

    @Nullable
    private Bitmap loadArt(@Nullable SongInfo info, long songId) {
        ContentResolver cr = context.getContentResolver();
        Uri custom = new CustomArtworkStore(context).getCustomArtworkUri(songId);
        Bitmap b = custom != null ? decode(cr, custom) : null;
        if (b == null && info != null && info.albumId > 0) b = decode(cr, ArtworkRepository.getAlbumArtUri(info.albumId));
        if (b == null && songId > 0) {
            // Not try-with-resources: MediaMetadataRetriever.close() needs API 29.
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            try {
                mmr.setDataSource(context, ArtworkRepository.getSongUri(songId));
                byte[] pic = mmr.getEmbeddedPicture();
                if (pic != null) b = sampled(pic);
            } catch (Exception ignored) {
                // no embedded art
            } finally {
                try {
                    mmr.release();
                } catch (Exception ignored) {
                    // already released
                }
            }
        }
        return b;
    }

    @Nullable
    private static Bitmap decode(ContentResolver cr, @Nullable Uri uri) {
        if (uri == null) return null;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) return null;
            byte[] data = readAll(in);
            return sampled(data);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static Bitmap sampled(byte[] data) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= ART_PX && o.outHeight / (sample * 2) >= ART_PX) sample *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(data, 0, data.length, o);
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }
}
