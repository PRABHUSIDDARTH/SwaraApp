package com.psthetech.swara.ui.viewmodel;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.psthetech.swara.SwaraApplication;
import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.data.db.entity.Playlist;
import com.psthetech.swara.data.db.entity.PlaylistSong;
import com.psthetech.swara.data.repository.PlaylistArtworkStore;
import com.psthetech.swara.data.repository.PlaylistRepository;
import com.psthetech.swara.domain.model.Song;

import java.util.ArrayList;
import java.util.List;

public class PlaylistViewModel extends AndroidViewModel {

    private final PlaylistRepository repository;
    private final PlaylistArtworkStore artworkStore;
    private final LiveData<List<Playlist>> playlists;

    public PlaylistViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);
        repository = new PlaylistRepository(db);
        artworkStore = new PlaylistArtworkStore(application);
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
                    Song canonical = com.psthetech.swara.data.repository.MusicRepository.getCanonicalSong(item.songId);
                    long dur = item.duration > 0 ? item.duration : (canonical != null ? canonical.getDuration() : 0);
                    long albumId = item.albumId != 0 ? item.albumId : (canonical != null ? canonical.getAlbumId() : 0);
                    songs.add(new Song(
                            item.songId,
                            item.title,
                            item.artist,
                            item.album,
                            albumId,
                            dur,
                            item.position,
                            canonical != null ? canonical.getYear() : 0,
                            0
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

    /** Reactive map of playlistId -> song count for all playlists. */
    public LiveData<java.util.Map<Long, Integer>> getSongCountsMapLive() {
        return repository.getSongCountsMapLive();
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
            repository.deletePlaylistWithArtwork(playlist.id, artworkStore);
        }
    }

    public void deletePlaylist(long playlistId) {
        repository.deletePlaylistWithArtwork(playlistId, artworkStore);
    }

    // ===== Playlist Artwork =====

    /** Returns the shared PlaylistArtworkStore (used by adapters for loading artwork). */
    public PlaylistArtworkStore getPlaylistArtworkStore() {
        return artworkStore;
    }

    /**
     * Saves a user-selected image URI as custom artwork for a playlist.
     * Runs on a background thread; calls onComplete on the main thread.
     */
    public void setPlaylistArtwork(long playlistId, Uri imageUri, Runnable onComplete) {
        SwaraApplication.getInstance().getDbExecutor().execute(() -> {
            boolean saved = artworkStore.saveCustomArtwork(playlistId, imageUri);
            if (saved) {
                String path = artworkStore.getCustomArtworkFile(playlistId).getAbsolutePath();
                repository.setArtworkPath(playlistId, path);
            }
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                com.psthetech.swara.util.ArtworkHelper.notifyArtworkChanged(getApplication());
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        });
    }

    /**
     * Removes the custom artwork for a playlist, reverting to collage/default.
     * Runs on a background thread; calls onComplete on the main thread.
     */
    public void removePlaylistArtwork(long playlistId, Runnable onComplete) {
        SwaraApplication.getInstance().getDbExecutor().execute(() -> {
            artworkStore.removeCustomArtwork(playlistId);
            repository.clearArtworkPath(playlistId);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                com.psthetech.swara.util.ArtworkHelper.notifyArtworkChanged(getApplication());
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        });
    }

    // ===== Song management =====

    /** Adds a song to a playlist (no duplicate check — use addSongToPlaylistChecked for UI). */
    public void addSongToPlaylist(long playlistId, Song song) {
        artworkStore.invalidateCollage(playlistId);
        repository.addSongToPlaylist(playlistId, song);
        com.psthetech.swara.util.ArtworkHelper.notifyArtworkChanged(getApplication());
    }

    /**
     * Adds a song only if it is not already in the playlist.
     * Delivers feedback on the main thread via AddSongCallback.
     */
    public void addSongToPlaylistChecked(long playlistId, String playlistName,
                                         Song song, PlaylistRepository.AddSongCallback callback) {
        repository.addSongToPlaylistChecked(playlistId, playlistName, song, new PlaylistRepository.AddSongCallback() {
            @Override
            public void onAdded(String pName) {
                artworkStore.invalidateCollage(playlistId);
                com.psthetech.swara.util.ArtworkHelper.notifyArtworkChanged(getApplication());
                if (callback != null) callback.onAdded(pName);
            }

            @Override
            public void onDuplicate(String pName) {
                if (callback != null) callback.onDuplicate(pName);
            }
        });
    }

    /** Creates a new playlist and immediately adds the given song. */
    public void createPlaylistAndAddSong(String name, Song song,
                                         PlaylistRepository.CreateCallback callback) {
        repository.createPlaylistAndAddSong(name, song, playlistId -> {
            artworkStore.invalidateCollage(playlistId);
            com.psthetech.swara.util.ArtworkHelper.notifyArtworkChanged(getApplication());
            if (callback != null) callback.onCreated(playlistId);
        });
    }

    /** Bulk-adds songs to a playlist, skipping duplicates. */
    public void addSongsToPlaylist(long playlistId, List<Song> songs) {
        artworkStore.invalidateCollage(playlistId);
        repository.addSongsToPlaylist(playlistId, songs);
        com.psthetech.swara.util.ArtworkHelper.notifyArtworkChanged(getApplication());
    }

    public void removeSongFromPlaylist(long playlistId, long songId) {
        artworkStore.invalidateCollage(playlistId);
        repository.removeSongFromPlaylist(playlistId, songId);
        com.psthetech.swara.util.ArtworkHelper.notifyArtworkChanged(getApplication());
    }

    public void reorderSong(long playlistId, long songId, int newPosition) {
        artworkStore.invalidateCollage(playlistId);
        repository.reorderSong(playlistId, songId, newPosition);
        com.psthetech.swara.util.ArtworkHelper.notifyArtworkChanged(getApplication());
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
