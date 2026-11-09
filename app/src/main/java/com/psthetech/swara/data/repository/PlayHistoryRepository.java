package com.psthetech.swara.data.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;

import com.psthetech.swara.SwaraApplication;
import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.data.db.dao.PlayHistoryDao;
import com.psthetech.swara.data.db.entity.PlayHistory;
import com.psthetech.swara.domain.model.Song;

import java.util.List;

/**
 * Repository for play history — records meaningful playback events.
 *
 * A play is recorded only when:
 *   1. Position >= min(30_000ms, duration * 0.4) — meaningful playback threshold
 *   2. The same song has NOT been recorded in the last 30 seconds — debounce guard
 *
 * History is trimmed to 200 entries after every insert.
 */
public class PlayHistoryRepository {

    private static final String TAG = "PlayHistoryRepository";
    private static final long DEBOUNCE_MS = 30_000L;

    private final PlayHistoryDao dao;

    public PlayHistoryRepository(AppDatabase db) {
        this.dao = db.playHistoryDao();
    }

    public LiveData<List<PlayHistory>> getRecentlyPlayedLive() {
        return dao.getRecentlyPlayedLive();
    }

    /**
     * Records a song play if it meets the meaningful-playback threshold.
     *
     * @param song         The song being played
     * @param positionMs   Current playback position in milliseconds
     */
    public void maybeRecordPlay(Song song, long positionMs) {
        SwaraApplication.getInstance().getDbExecutor().execute(() -> {
            Song canonical = MusicRepository.getCanonicalSong(song.getId());
            long duration = song.getDuration() > 0 ? song.getDuration() : (canonical != null ? canonical.getDuration() : 0);
            long albumId = song.getAlbumId() != 0 ? song.getAlbumId() : (canonical != null ? canonical.getAlbumId() : 0);
            long threshold = Math.min(30_000L, (long)(duration * 0.4));
            if (positionMs < threshold) {
                return; // Not enough meaningful playback yet
            }

            // Debounce: skip if this song was already recorded in the last 30 seconds
            long cutoff = System.currentTimeMillis() - DEBOUNCE_MS;
            int recentCount = dao.countRecentEntries(song.getId(), cutoff);
            if (recentCount > 0) {
                Log.d(TAG, "Debounced duplicate play for: " + song.getTitle());
                return;
            }

            PlayHistory history = new PlayHistory(
                    song.getId(), song.getTitle(), song.getArtist(),
                    song.getAlbum(), albumId, duration,
                    System.currentTimeMillis()
            );
            dao.recordPlay(history);
            dao.trimHistory(); // keep table capped at 200
            Log.d(TAG, "Recorded play: " + song.getTitle() + " at position " + positionMs + "ms");
        });
    }

    public void deleteHistoryForSong(long songId) {
        SwaraApplication.getInstance().getDbExecutor().execute(() -> dao.deleteHistoryForSong(songId));
    }

    public void clearHistory() {
        SwaraApplication.getInstance().getDbExecutor().execute(dao::clearHistory);
    }
}
