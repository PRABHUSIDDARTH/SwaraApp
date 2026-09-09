package com.psthetech.swara.data.repository;

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
 * All writes on dbExecutor (background). LiveData reads are reactive.
 */
public class PlaylistRepository {

    private final PlaylistDao dao;

    public PlaylistRepository(AppDatabase db) {
        this.dao = db.playlistDao();
    }

    public LiveData<List<Playlist>> getAllPlaylistsLive() {
        return dao.getAllPlaylistsLive();
    }

    public LiveData<List<PlaylistSong>> getPlaylistSongsLive(long playlistId) {
        return dao.getPlaylistSongsLive(playlistId);
    }

    public void createPlaylist(String name, CreateCallback callback) {
        SwaraApplication.getInstance().getDbExecutor().execute(() -> {
            long now = System.currentTimeMillis();
            Playlist playlist = new Playlist(name, now, now);
            long id = dao.createPlaylist(playlist);
            if (callback != null) {
                android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                h.post(() -> callback.onCreated(id));
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

    public void addSongToPlaylist(long playlistId, Song song) {
        SwaraApplication.getInstance().getDbExecutor().execute(() -> {
            int nextPos = dao.getMaxPosition(playlistId) + 1;
            PlaylistSong ps = new PlaylistSong(playlistId, song.getId(), nextPos,
                    song.getTitle(), song.getArtist(), song.getAlbum(),
                    song.getAlbumId(), song.getDuration());
            dao.addSongToPlaylist(ps);
            // Update playlist modified time
            dao.renamePlaylist(playlistId,
                    dao.getPlaylistById(playlistId) != null ? dao.getPlaylistById(playlistId).name : "",
                    System.currentTimeMillis());
        });
    }

    public void removeSongFromPlaylist(long playlistId, long songId) {
        SwaraApplication.getInstance().getDbExecutor().execute(() ->
                dao.removeSongFromPlaylist(playlistId, songId));
    }

    public void reorderSong(long playlistId, long songId, int newPosition) {
        SwaraApplication.getInstance().getDbExecutor().execute(() ->
                dao.updateSongPosition(playlistId, songId, newPosition));
    }

    /** Blocking read for building playback queues */
    public List<PlaylistSong> getPlaylistSongsBlocking(long playlistId) {
        return dao.getPlaylistSongs(playlistId);
    }

    public interface CreateCallback {
        void onCreated(long playlistId);
    }
}
