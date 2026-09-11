package com.psthetech.swara.data.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import com.psthetech.swara.SwaraApplication;
import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.data.db.dao.PlaylistDao;
import com.psthetech.swara.data.db.entity.Playlist;
import com.psthetech.swara.data.db.entity.PlaylistSong;
import com.psthetech.swara.domain.model.Song;

import java.util.List;

/**
 * Repository for playlist CRUD and song management.
 *
 * Rules:
 *  - All reads that return LiveData are reactive (Room handles the bg thread).
 *  - All writes and blocking reads go through dbExecutor.
 *  - Results are posted back to the main thread via Handler.
 *  - NO allowMainThreadQueries.
 */
public class PlaylistRepository {

    private final PlaylistDao dao;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public PlaylistRepository(AppDatabase db) {
        this.dao = db.playlistDao();
    }

    // ===== Read (LiveData — reactive) =====

    public LiveData<List<Playlist>> getAllPlaylistsLive() {
        return dao.getAllPlaylistsLive();
    }

    public LiveData<List<PlaylistSong>> getPlaylistSongsLive(long playlistId) {
        return dao.getPlaylistSongsLive(playlistId);
    }

    public LiveData<Integer> getSongCountLive(long playlistId) {
        return dao.getSongCountLive(playlistId);
    }

    // ===== Playlist CRUD =====

    public void createPlaylist(String name, CreateCallback callback) {
        SwaraApplication.getInstance().getDbExecutor().execute(() -> {
            long now = System.currentTimeMillis();
            Playlist playlist = new Playlist(name, now, now);
            long id = dao.createPlaylist(playlist);
            if (callback != null) {
                mainHandler.post(() -> callback.onCreated(id));
            }
        });
    }

    public void renamePlaylist(long playlistId, String newName) {
        SwaraApplication.getInstance().getDbExecutor().execute(() ->
                dao.renamePlaylist(playlistId, newName, System.currentTimeMillis()));
    }

    public void deletePlaylist(long playlistId) {
        SwaraApplication.getInstance().getDbExecutor().execute(() ->
                dao.deletePlaylist(playlistId));
    }

    // ===== Song management =====

    /**
     * Adds a song to a playlist. Uses getSongCount() (not getMaxPosition()+1) to determine the
     * next position, which correctly handles the case where the playlist is empty
     * (Room's SELECT MAX() on an empty set returns NULL → 0 in Java int, causing an off-by-one).
     */
    public void addSongToPlaylist(long playlistId, Song song) {
        SwaraApplication.getInstance().getDbExecutor().execute(() -> {
            int nextPos = dao.getSongCount(playlistId); // 0-based: count is next free index
            PlaylistSong ps = new PlaylistSong(
                    playlistId, song.getId(), nextPos,
                    song.getTitle(), song.getArtist(), song.getAlbum(),
                    song.getAlbumId(), song.getDuration());
            dao.addSongToPlaylist(ps);
            touchModifiedAt(playlistId);
        });
    }

    /**
     * Adds a song only if it is not already in the playlist.
     * Calls back on the main thread with the result.
     */
    public void addSongToPlaylistChecked(long playlistId, String playlistName,
                                         Song song, AddSongCallback callback) {
        SwaraApplication.getInstance().getDbExecutor().execute(() -> {
            boolean exists = dao.isSongInPlaylist(playlistId, song.getId());
            if (exists) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onDuplicate(playlistName));
                }
            } else {
                int nextPos = dao.getSongCount(playlistId);
                PlaylistSong ps = new PlaylistSong(
                        playlistId, song.getId(), nextPos,
                        song.getTitle(), song.getArtist(), song.getAlbum(),
                        song.getAlbumId(), song.getDuration());
                dao.addSongToPlaylist(ps);
                touchModifiedAt(playlistId);
                if (callback != null) {
                    mainHandler.post(() -> callback.onAdded(playlistName));
                }
            }
        });
    }

    /** Creates a new playlist and immediately adds the given song. */
    public void createPlaylistAndAddSong(String name, Song song, CreateCallback callback) {
        SwaraApplication.getInstance().getDbExecutor().execute(() -> {
            long now = System.currentTimeMillis();
            Playlist playlist = new Playlist(name, now, now);
            long id = dao.createPlaylist(playlist);
            // Add song at position 0
            PlaylistSong ps = new PlaylistSong(
                    id, song.getId(), 0,
                    song.getTitle(), song.getArtist(), song.getAlbum(),
                    song.getAlbumId(), song.getDuration());
            dao.addSongToPlaylist(ps);
            dao.renamePlaylist(id, name, System.currentTimeMillis());
            if (callback != null) {
                mainHandler.post(() -> callback.onCreated(id));
            }
        });
    }

    /** Bulk-adds all songs, skipping duplicates, preserving order. */
    public void addSongsToPlaylist(long playlistId, List<Song> songs) {
        SwaraApplication.getInstance().getDbExecutor().execute(() -> {
            int nextPos = dao.getSongCount(playlistId);
            for (Song song : songs) {
                if (!dao.isSongInPlaylist(playlistId, song.getId())) {
                    PlaylistSong ps = new PlaylistSong(
                            playlistId, song.getId(), nextPos++,
                            song.getTitle(), song.getArtist(), song.getAlbum(),
                            song.getAlbumId(), song.getDuration());
                    dao.addSongToPlaylist(ps);
                }
            }
            touchModifiedAt(playlistId);
        });
    }

    public void removeSongFromPlaylist(long playlistId, long songId) {
        SwaraApplication.getInstance().getDbExecutor().execute(() ->
                dao.removeSongFromPlaylist(playlistId, songId));
    }

    public void deleteSongFromAllPlaylists(long songId) {
        SwaraApplication.getInstance().getDbExecutor().execute(() ->
                dao.deleteSongFromAllPlaylists(songId));
    }

    public void reorderSong(long playlistId, long songId, int newPosition) {
        SwaraApplication.getInstance().getDbExecutor().execute(() ->
                dao.updateSongPosition(playlistId, songId, newPosition));
    }

    /** Blocking read for building playback queues (must be called off main thread). */
    public List<PlaylistSong> getPlaylistSongsBlocking(long playlistId) {
        return dao.getPlaylistSongs(playlistId);
    }

    /** Blocking read for all playlists (must be called off main thread). */
    public List<Playlist> getAllPlaylistsBlocking() {
        return dao.getAllPlaylists();
    }

    // ===== Internal helpers =====

    private void touchModifiedAt(long playlistId) {
        Playlist p = dao.getPlaylistById(playlistId);
        if (p != null) {
            dao.renamePlaylist(playlistId, p.name, System.currentTimeMillis());
        }
    }

    // ===== Callbacks =====

    public interface CreateCallback {
        void onCreated(long playlistId);
    }

    public interface AddSongCallback {
        void onAdded(String playlistName);
        void onDuplicate(String playlistName);
    }
}
