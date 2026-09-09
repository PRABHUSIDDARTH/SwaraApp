package com.psthetech.swara.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

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
    private final MutableLiveData<Long> selectedPlaylistId = new MutableLiveData<>(-1L);
    private LiveData<List<PlaylistSong>> playlistSongs;

    public PlaylistViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);
        repository = new PlaylistRepository(db);
        playlists = repository.getAllPlaylistsLive();
    }

    public LiveData<List<Playlist>> getPlaylists() { return playlists; }

    public LiveData<List<Playlist>> getAllPlaylists() { return playlists; }

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

    public void selectPlaylist(long playlistId) {
        selectedPlaylistId.setValue(playlistId);
        playlistSongs = repository.getPlaylistSongsLive(playlistId);
    }

    public LiveData<List<PlaylistSong>> getSelectedPlaylistSongs() {
        return playlistSongs;
    }

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

    public void addSongToPlaylist(long playlistId, Song song) {
        repository.addSongToPlaylist(playlistId, song);
    }

    public void removeSongFromPlaylist(long playlistId, long songId) {
        repository.removeSongFromPlaylist(playlistId, songId);
    }

    public void reorderSong(long playlistId, long songId, int newPosition) {
        repository.reorderSong(playlistId, songId, newPosition);
    }
}
