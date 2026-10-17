package com.psthetech.swara.wrapped;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.psthetech.swara.SwaraApplication;
import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.data.db.dao.WrappedDao;
import com.psthetech.swara.data.db.entity.SongSnapshot;
import com.psthetech.swara.data.repository.MusicRepository;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.util.ArtistIdentityHelper;

import java.util.TimeZone;

/** {@link ListenTracker.Sink} that writes the Wrapped tables on the DB executor. */
public final class RoomListenSink implements ListenTracker.Sink {

    private static final String TAG = "RoomListenSink";
    private final Context context;

    public RoomListenSink(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /** The tracker used by the playback service. */
    public static ListenTracker newTracker(@NonNull Context context) {
        TimeZone tz = TimeZone.getDefault();
        return new ListenTracker(
                new ListenClock(android.os.SystemClock::elapsedRealtime, System::currentTimeMillis, tz),
                new RoomListenSink(context), System::currentTimeMillis, tz);
    }

    private WrappedDao dao() {
        return AppDatabase.getInstance(context).wrappedDao();
    }

    private void run(Runnable r) {
        SwaraApplication.getInstance().getDbExecutor().execute(() -> {
            try {
                r.run();
            } catch (Exception e) {
                Log.w(TAG, "Wrapped write failed", e);
            }
        });
    }

    @Override
    public void addDaily(int day, long songId, long playedMs, int plays, int finishes, int skips,
                         int repeats, int vibeStarts) {
        run(() -> dao().addDaily(day, songId, playedMs, plays, finishes, skips, repeats, vibeStarts));
    }

    @Override
    public void addHourly(int day, int hour, long playedMs) {
        run(() -> dao().addHourly(day, hour, playedMs));
    }

    @Override
    public void snapshot(long songId, int day) {
        Song song = MusicRepository.getCanonicalSong(songId);
        if (song == null) return; // tags unknown right now; the next play or backfill fills it
        SongSnapshot s = toSnapshot(song, day);
        run(() -> dao().upsertSnapshot(s));
    }

    /** Refreshes a song's stored tags after a metadata edit (keeps firstHeardDay). */
    public static void refreshSnapshot(@NonNull Context context, @NonNull Song song) {
        SongSnapshot s = toSnapshot(song, 0);
        SwaraApplication.getInstance().getDbExecutor().execute(() -> {
            try {
                WrappedDao dao = AppDatabase.getInstance(context).wrappedDao();
                if (dao.snapshot(song.getId()) != null) dao.upsertSnapshot(s);
            } catch (Exception e) {
                Log.w(TAG, "Snapshot refresh failed", e);
            }
        });
    }

    public static SongSnapshot toSnapshot(@NonNull Song song, int day) {
        SongSnapshot s = new SongSnapshot();
        s.songId = song.getId();
        s.title = song.getTitle();
        s.artist = song.getArtist();
        s.artistKey = ArtistIdentityHelper.getCanonicalKey(song.getArtist());
        s.album = song.getAlbum();
        s.albumId = song.getAlbumId();
        s.firstHeardDay = day;
        return s;
    }
}
