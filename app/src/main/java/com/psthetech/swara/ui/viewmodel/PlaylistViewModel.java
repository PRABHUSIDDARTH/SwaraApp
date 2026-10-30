package com.psthetech.swara.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.psthetech.swara.SwaraApplication;
import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.data.db.entity.Playlist;
import com.psthetech.swara.data.db.entity.PlaylistSong;
import com.psthetech.swara.data.repository.PlaylistRepository;
import com.psthetech.swara.domain.model.Song;

import java.util.ArrayList;
import java.util.List;

public class PlaylistViewModel extends AndroidViewModel {

    private final PlaylistRepository repository;
    private final LiveData<List<Playlist>> playlists;

    public PlaylistViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);
        repository = new PlaylistRepository(db);
        playlists = repository.getAllPlaylistsLive();
    }

    // ===== Playlist list =====

    public LiveData<List<Playlist>> getPlaylists() { return playlists; }

    public LiveData<List<Playlist>> getAllPlaylists() { return playlists; }

    // ===== Song list for a specific playlist =====

    /**
     * Returns LiveData<List<Song>> for a playlist, converting PlaylistSong rows to Song objects.
     * The list is ordered by position (ascending).
     */
    public LiveData<List<Song>> getSongsForPlaylist(long playlistId) {
        LiveData<List<PlaylistSong>> liveEntries = repository.getPlaylistSongsLive(playlistId);
        return Transformations.map(liveEntries, list -> {
            List<Song> songs = new ArrayList<>();
            if (list != null) {
                for (PlaylistSong item : list) {
                    songs.add(new Song(
                            item.songId,
                            item.title,
                            item.artist,
                            item.album,
                            item.albumId,
                            item.duration,
                            item.position, 0, 0
                    ));
                }
            }
            return songs;
        });
    }

    /** Reactive song count for a single playlist (used in detail screen header). */
    public LiveData<Integer> getSongCountLive(long playlistId) {
        return repository.getSongCountLive(playlistId);
    }

    // ===== CRUD =====

    public void createPlaylist(String name) {
        repository.createPlaylist(name, null);
    }

    public void createPlaylist(String name, PlaylistRepository.CreateCallback callback) {
        repository.createPlaylist(name, callback);
    }

    public void renamePlaylist(long playlistId, String name) {
        repository.renamePlaylist(playlistId, name);
    }

    public void deletePlaylist(Playlist playlist) {
        if (playlist != null) {
            repository.deletePlaylist(playlist.id);
        }
    }

    public void deletePlaylist(long playlistId) {
        repository.deletePlaylist(playlistId);
    }

    // ===== Song management =====

    /** Adds a song to a playlist (no duplicate check — use addSongToPlaylistChecked for UI). */
    public void addSongToPlaylist(long playlistId, Song song) {
        repository.addSongToPlaylist(playlistId, song);
    }

    /**
     * Adds a song only if it is not already in the playlist.
     * Delivers feedback on the main thread via AddSongCallback.
     */
    public void addSongToPlaylistChecked(long playlistId, String playlistName,
                                         Song song, PlaylistRepository.AddSongCallback callback) {
        repository.addSongToPlaylistChecked(playlistId, playlistName, song, callback);
    }

    /** Creates a new playlist and immediately adds the given song. */
    public void createPlaylistAndAddSong(String name, Song song,
                                         PlaylistRepository.CreateCallback callback) {
        repository.createPlaylistAndAddSong(name, song, callback);
    }

    /** Bulk-adds songs to a playlist, skipping duplicates. */
    public void addSongsToPlaylist(long playlistId, List<Song> songs) {
        repository.addSongsToPlaylist(playlistId, songs);
    }

    public void removeSongFromPlaylist(long playlistId, long songId) {
        repository.removeSongFromPlaylist(playlistId, songId);
    }

    public void reorderSong(long playlistId, long songId, int newPosition) {
        repository.reorderSong(playlistId, songId, newPosition);
    }

    /**
     * Loads all playlists for use in the "Add to playlist" dialog.
     * Uses a background thread and calls back on main thread.
     */
    public void loadAllPlaylistsBackground(PlaylistsCallback callback) {
        SwaraApplication.getInstance().getDbExecutor().execute(() -> {
            List<Playlist> playlists = repository.getAllPlaylistsBlocking();
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(() -> callback.onLoaded(playlists));
        });
    }

    public interface PlaylistsCallback {
        void onLoaded(List<Playlist> playlists);
    }
}
