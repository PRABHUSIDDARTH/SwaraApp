package com.psthetech.swara.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.data.db.entity.PlayHistory;
import com.psthetech.swara.data.repository.MusicRepository;
import com.psthetech.swara.data.repository.PlayHistoryRepository;
import com.psthetech.swara.domain.model.Album;
import com.psthetech.swara.domain.model.Artist;
import com.psthetech.swara.domain.model.Song;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * ViewModel for the Library (songs, albums, artists) and search.
 * All MediaStore queries happen on background threads via MusicRepository.
 */
public class LibraryViewModel extends AndroidViewModel {

    private final MusicRepository musicRepository;
    private final PlayHistoryRepository historyRepository;

    // Full unfiltered lists (loaded once)
    private final MutableLiveData<List<Song>> allSongs = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Album>> allAlbums = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Artist>> allArtists = new MutableLiveData<>(new ArrayList<>());

    // Search results
    private final MutableLiveData<List<Song>> searchResults = new MutableLiveData<>(new ArrayList<>());

    // Loading states
    private final MutableLiveData<Boolean> isLoadingSongs = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isLoadingAlbums = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isLoadingArtists = new MutableLiveData<>(false);

    private final MutableLiveData<String> loadError = new MutableLiveData<>(null);

    // Recently played from Room (reactive) — raw PlayHistory rows
    private final LiveData<List<PlayHistory>> recentlyPlayed;

    // Transformed Song objects derived from play history (for Home fragment)
    private final LiveData<List<Song>> recentlyPlayedSongs;

    public LibraryViewModel(@NonNull Application application) {
        super(application);
        musicRepository = new MusicRepository(application);
        AppDatabase db = AppDatabase.getInstance(application);
        historyRepository = new PlayHistoryRepository(db);
        recentlyPlayed = historyRepository.getRecentlyPlayedLive();

        // Map PlayHistory rows → Song domain objects (max 20, most recent first)
        recentlyPlayedSongs = Transformations.map(recentlyPlayed, historyList -> {
            if (historyList == null || historyList.isEmpty()) return Collections.emptyList();
            List<Song> songs = new ArrayList<>();
            int limit = Math.min(historyList.size(), 20);
            for (int i = 0; i < limit; i++) {
                PlayHistory h = historyList.get(i);
                songs.add(new Song(h.songId, h.title, h.artist, h.album,
                        h.albumId, h.duration, 0, 0, h.playedAt));
            }
            return songs;
        });
    }

    // ===== Loaders =====

    public void loadSongs() {
        if (isLoadingSongs.getValue() == Boolean.TRUE) return;
        isLoadingSongs.setValue(true);
        musicRepository.loadAllSongs(new MusicRepository.Callback<List<Song>>() {
            @Override
            public void onResult(List<Song> result) {
                allSongs.setValue(result);
                isLoadingSongs.setValue(false);
            }
            @Override
            public void onError(String message) {
                loadError.setValue(message);
                isLoadingSongs.setValue(false);
            }
        });
    }

    public void loadAlbums() {
        if (isLoadingAlbums.getValue() == Boolean.TRUE) return;
        isLoadingAlbums.setValue(true);
        musicRepository.loadAlbums(new MusicRepository.Callback<List<Album>>() {
            @Override
            public void onResult(List<Album> result) {
                allAlbums.setValue(result);
                isLoadingAlbums.setValue(false);
            }
            @Override
            public void onError(String message) {
                loadError.setValue(message);
                isLoadingAlbums.setValue(false);
            }
        });
    }

    public void loadArtists() {
        if (isLoadingArtists.getValue() == Boolean.TRUE) return;
        isLoadingArtists.setValue(true);
        musicRepository.loadArtists(new MusicRepository.Callback<List<Artist>>() {
            @Override
            public void onResult(List<Artist> result) {
                allArtists.setValue(result);
                isLoadingArtists.setValue(false);
            }
            @Override
            public void onError(String message) {
                loadError.setValue(message);
                isLoadingArtists.setValue(false);
            }
        });
    }

    public void search(String query) {
        musicRepository.search(query, new MusicRepository.Callback<List<Song>>() {
            @Override
            public void onResult(List<Song> result) {
                searchResults.setValue(result);
            }
            @Override
            public void onError(String message) {
                searchResults.setValue(Collections.emptyList());
            }
        });
    }

    public void refreshAll() {
        loadSongs();
        loadAlbums();
        loadArtists();
    }

    // ===== Convenience Aliases and Filtering for Fragments =====

    public LiveData<List<Song>> getSongs() {
        if (allSongs.getValue() == null || allSongs.getValue().isEmpty()) {
            loadSongs();
        }
        return allSongs;
    }

    public LiveData<List<Album>> getAlbums() {
        if (allAlbums.getValue() == null || allAlbums.getValue().isEmpty()) {
            loadAlbums();
        }
        return allAlbums;
    }

    public LiveData<List<Artist>> getArtists() {
        if (allArtists.getValue() == null || allArtists.getValue().isEmpty()) {
            loadArtists();
        }
        return allArtists;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoadingSongs;
    }

    public void searchSongs(String query) {
        search(query);
    }

    public LiveData<List<Song>> getSongsForAlbum(long albumId) {
        MutableLiveData<List<Song>> albumSongs = new MutableLiveData<>();
        musicRepository.loadSongsForAlbum(albumId, new MusicRepository.Callback<List<Song>>() {
            @Override
            public void onResult(List<Song> result) {
                albumSongs.setValue(result);
            }

            @Override
            public void onError(String message) {
                albumSongs.setValue(Collections.emptyList());
            }
        });
        return albumSongs;
    }

    public LiveData<List<Song>> getSongsForArtist(String artistName) {
        MutableLiveData<List<Song>> artistSongs = new MutableLiveData<>();
        musicRepository.loadSongsForArtist(artistName, new MusicRepository.Callback<List<Song>>() {
            @Override
            public void onResult(List<Song> result) {
                artistSongs.setValue(result);
            }

            @Override
            public void onError(String message) {
                artistSongs.setValue(Collections.emptyList());
            }
        });
        return artistSongs;
    }

    /**
     * Returns a reactive list of recently played songs, derived from Room PlayHistory.
     * Each entry is mapped from a PlayHistory row back to a Song domain object.
     * Empty until the user plays at least one song past the meaningful-play threshold.
     */
    public LiveData<List<Song>> getRecentlyPlayedSongs() {
        return recentlyPlayedSongs;
    }

    // ===== LiveData getters =====

    public LiveData<List<Song>> getAllSongs() { return allSongs; }
    public LiveData<List<Album>> getAllAlbums() { return allAlbums; }
    public LiveData<List<Artist>> getAllArtists() { return allArtists; }
    public LiveData<List<Song>> getSearchResults() { return searchResults; }
    public LiveData<Boolean> getIsLoadingSongs() { return isLoadingSongs; }
    public LiveData<Boolean> getIsLoadingAlbums() { return isLoadingAlbums; }
    public LiveData<Boolean> getIsLoadingArtists() { return isLoadingArtists; }
    public LiveData<String> getLoadError() { return loadError; }
    public LiveData<List<PlayHistory>> getRecentlyPlayed() { return recentlyPlayed; }
}
