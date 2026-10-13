package com.psthetech.swara.data.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import com.psthetech.swara.SwaraApplication;
import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.data.db.dao.FavoriteDao;
import com.psthetech.swara.data.db.entity.FavoriteSong;
import com.psthetech.swara.domain.model.Song;

import java.util.List;

/**
 * Repository for favorites operations.
 * All write operations run on the dbExecutor (background thread).
 * Read LiveData is observed directly — Room delivers on the appropriate thread.
 */
public class FavoritesRepository {

    private final FavoriteDao dao;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public FavoritesRepository(AppDatabase db) {
        this.dao = db.favoriteDao();
    }

    /** Reactive favorites list — use this in ViewModel for UI */
    public LiveData<List<FavoriteSong>> getFavoritesLive() {
        return dao.getAllFavoritesLive();
    }

    /** Reactive boolean whether a specific song is favorited */
    public LiveData<Boolean> isFavoriteLive(long songId) {
        return dao.isFavoriteLive(songId);
    }

    public void addFavorite(Song song) {
        SwaraApplication.getInstance().getDbExecutor().execute(() -> {
            Song canonical = MusicRepository.getCanonicalSong(song.getId());
            long duration = song.getDuration() > 0 ? song.getDuration() : (canonical != null ? canonical.getDuration() : 0);
            long albumId = song.getAlbumId() != 0 ? song.getAlbumId() : (canonical != null ? canonical.getAlbumId() : 0);
            FavoriteSong fav = new FavoriteSong(
                    song.getId(), song.getTitle(), song.getArtist(),
                    song.getAlbum(), albumId, duration,
                    System.currentTimeMillis()
            );
            dao.addFavorite(fav);
        });
    }

    public void removeFavorite(long songId) {
        SwaraApplication.getInstance().getDbExecutor().execute(() -> dao.removeFavoriteById(songId));
    }

    public void toggleFavorite(Song song) {
        SwaraApplication.getInstance().getDbExecutor().execute(() -> {
            if (dao.isFavorite(song.getId())) {
                dao.removeFavoriteById(song.getId());
            } else {
                Song canonical = MusicRepository.getCanonicalSong(song.getId());
                long duration = song.getDuration() > 0 ? song.getDuration() : (canonical != null ? canonical.getDuration() : 0);
                long albumId = song.getAlbumId() != 0 ? song.getAlbumId() : (canonical != null ? canonical.getAlbumId() : 0);
                FavoriteSong fav = new FavoriteSong(
                        song.getId(), song.getTitle(), song.getArtist(),
                        song.getAlbum(), albumId, duration,
                        System.currentTimeMillis()
                );
                dao.addFavorite(fav);
            }
        });
    }

    /** Blocking check — only for use on background thread */
    public boolean isFavoriteBlocking(long songId) {
        return dao.isFavorite(songId);
    }

    /** Blocking read for constructing playback queues */
    public List<FavoriteSong> getAllFavoritesBlocking() {
        return dao.getAllFavorites();
    }
}
