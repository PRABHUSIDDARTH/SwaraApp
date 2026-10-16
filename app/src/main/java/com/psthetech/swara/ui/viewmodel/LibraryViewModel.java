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
    private final MutableLiveData<List<Song>>   allSongs   = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Album>>  allAlbums  = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Artist>> allArtists = new MutableLiveData<>(new ArrayList<>());

    static final String PREFS_NAME = "swara_library_prefs";
    static final String KEY_SORT_ORDER = "songs_sort_order";
    static final String KEY_CUSTOM_ORDER = "songs_custom_order";

    // Hand-arranged order used by SortOrder.CUSTOM
    private List<Long> customOrderIds = new ArrayList<>();

    // Active sort order, restored from preferences (default: A–Z by title)
    private final android.content.SharedPreferences libraryPrefs;
    private final MutableLiveData<SortOrder> activeSortOrder;

    // Derived sorted view of allSongs, updated whenever allSongs or activeSortOrder changes
    private final LiveData<List<Song>> sortedSongs;

    // Search results
    private final MutableLiveData<List<Song>> searchResults = new MutableLiveData<>(new ArrayList<>());

    // Loading states
    private final MutableLiveData<Boolean> isLoadingSongs   = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isLoadingAlbums  = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isLoadingArtists = new MutableLiveData<>(false);

    private final MutableLiveData<String> loadError = new MutableLiveData<>(null);

    // Recently played from Room (reactive) — raw PlayHistory rows
    private final LiveData<List<PlayHistory>> recentlyPlayed;

    // Transformed Song objects derived from play history (for Home fragment)
    private final LiveData<List<Song>> recentlyPlayedSongs;

    public LibraryViewModel(@NonNull Application application) {
        super(application);
        libraryPrefs = application.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
        activeSortOrder = new MutableLiveData<>(SortOrder.fromKey(libraryPrefs.getString(KEY_SORT_ORDER, null)));
        customOrderIds = CustomSongOrder.decode(libraryPrefs.getString(KEY_CUSTOM_ORDER, null));
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
                com.psthetech.swara.domain.model.Song canonical =
                        com.psthetech.swara.data.repository.MusicRepository.getCanonicalSong(h.songId);
                long dur = h.duration > 0 ? h.duration : (canonical != null ? canonical.getDuration() : 0);
                long albumId = h.albumId != 0 ? h.albumId : (canonical != null ? canonical.getAlbumId() : 0);
                songs.add(new Song(h.songId, h.title, h.artist, h.album,
                        albumId, dur,
                        canonical != null ? canonical.getTrackNumber() : 0,
                        canonical != null ? canonical.getYear() : 0,
                        h.playedAt));
            }
            return songs;
        });

        // Pick up songs added, removed, or re-tagged outside the app.
        application.getContentResolver().registerContentObserver(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver);

        // Build a sorted view: re-sort whenever allSongs or activeSortOrder changes
        sortedSongs = Transformations.switchMap(activeSortOrder, order ->
                Transformations.map(allSongs, songs -> applySortOrder(songs, order)));
    }

    /** Apply sort order to a list (always returns a fresh mutable copy). */
    private List<Song> applySortOrder(List<Song> songs, SortOrder order) {
        if (songs == null) return Collections.emptyList();
        if (order == SortOrder.CUSTOM) return CustomSongOrder.apply(songs, customOrderIds);
        List<Song> sorted = new ArrayList<>(songs);
        switch (order) {
            case TITLE_DESC:
                sorted.sort((a, b) -> b.getTitle().compareToIgnoreCase(a.getTitle()));
                break;
            case ARTIST_ASC:
                sorted.sort((a, b) -> a.getArtist().compareToIgnoreCase(b.getArtist()));
                break;
            case DATE_ADDED:
                sorted.sort((a, b) -> Long.compare(b.getDateAdded(), a.getDateAdded()));
                break;
            case DURATION_ASC:
                sorted.sort((a, b) -> Long.compare(a.getDuration(), b.getDuration()));
                break;
            case TITLE_ASC:
            default:
                sorted.sort((a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()));
                break;
        }
        return sorted;
    }

    /** Change the active sort order; sortedSongs will automatically update. */
    public void setSortOrder(SortOrder order) {
        if (order == null) order = SortOrder.TITLE_ASC;
        libraryPrefs.edit().putString(KEY_SORT_ORDER, order.name()).apply();
        activeSortOrder.setValue(order);
    }

    /** Saves a drag-arranged order and switches the Songs list to it (persisted across launches). */
    public void setCustomOrder(List<Song> orderedSongs) {
        String encoded = CustomSongOrder.encode(orderedSongs);
        customOrderIds = CustomSongOrder.decode(encoded);
        libraryPrefs.edit()
                .putString(KEY_CUSTOM_ORDER, encoded)
                .putString(KEY_SORT_ORDER, SortOrder.CUSTOM.name())
                .apply();
        activeSortOrder.setValue(SortOrder.CUSTOM);
    }

    public SortOrder getActiveSortOrder() {
        SortOrder o = activeSortOrder.getValue();
        return o != null ? o : SortOrder.TITLE_ASC;
    }

    // ===== MediaStore change observer =====

    private static final long MEDIA_CHANGE_DEBOUNCE_MS = 1500L;
    private final android.os.Handler mediaChangeHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable mediaChangeReload = this::refreshAll;
    private final android.database.ContentObserver mediaObserver =
            new android.database.ContentObserver(mediaChangeHandler) {
                @Override
                public void onChange(boolean selfChange) {
                    // MediaStore fires bursts of changes during a scan; coalesce them.
                    mediaChangeHandler.removeCallbacks(mediaChangeReload);
                    mediaChangeHandler.postDelayed(mediaChangeReload, MEDIA_CHANGE_DEBOUNCE_MS);
                }
            };

    @Override
    protected void onCleared() {
        mediaChangeHandler.removeCallbacks(mediaChangeReload);
        getApplication().getContentResolver().unregisterContentObserver(mediaObserver);
        super.onCleared();
    }

    // ===== Loaders =====

    private boolean reloadSongsPending, reloadAlbumsPending, reloadArtistsPending;

    public void loadSongs() {
        if (isLoadingSongs.getValue() == Boolean.TRUE) {
            // A load started before (e.g.) the permission grant may return stale results.
            reloadSongsPending = true;
            return;
        }
        isLoadingSongs.setValue(true);
        musicRepository.loadAllSongs(new MusicRepository.Callback<List<Song>>() {
            @Override
            public void onResult(List<Song> result) {
                allSongs.setValue(result);
                isLoadingSongs.setValue(false);
                if (reloadSongsPending) { reloadSongsPending = false; loadSongs(); }
            }
            @Override
            public void onError(String message) {
                loadError.setValue(message);
                isLoadingSongs.setValue(false);
                if (reloadSongsPending) { reloadSongsPending = false; loadSongs(); }
            }
        });
    }

    public void loadAlbums() {
        if (isLoadingAlbums.getValue() == Boolean.TRUE) {
            // A load started before (e.g.) the permission grant may return stale results.
            reloadAlbumsPending = true;
            return;
        }
        isLoadingAlbums.setValue(true);
        musicRepository.loadAlbums(new MusicRepository.Callback<List<Album>>() {
            @Override
            public void onResult(List<Album> result) {
                allAlbums.setValue(result);
                isLoadingAlbums.setValue(false);
                if (reloadAlbumsPending) { reloadAlbumsPending = false; loadAlbums(); }
            }
            @Override
            public void onError(String message) {
                loadError.setValue(message);
                isLoadingAlbums.setValue(false);
                if (reloadAlbumsPending) { reloadAlbumsPending = false; loadAlbums(); }
            }
        });
    }

    public void loadArtists() {
        if (isLoadingArtists.getValue() == Boolean.TRUE) {
            // A load started before (e.g.) the permission grant may return stale results.
            reloadArtistsPending = true;
            return;
        }
        isLoadingArtists.setValue(true);
        musicRepository.loadArtists(new MusicRepository.Callback<List<Artist>>() {
            @Override
            public void onResult(List<Artist> result) {
                allArtists.setValue(result);
                isLoadingArtists.setValue(false);
                if (reloadArtistsPending) { reloadArtistsPending = false; loadArtists(); }
            }
            @Override
            public void onError(String message) {
                loadError.setValue(message);
                isLoadingArtists.setValue(false);
                if (reloadArtistsPending) { reloadArtistsPending = false; loadArtists(); }
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

    /**
     * Returns the sorted song list (re-emits on sort-order or data change).
     * Prefer this over getAllSongs() when displaying in the Songs tab.
     */
    public LiveData<List<Song>> getSongs() {
        if (allSongs.getValue() == null || allSongs.getValue().isEmpty()) {
            loadSongs();
        }
        return sortedSongs;
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
        if (allSongs.getValue() == null || allSongs.getValue().isEmpty()) {
            loadSongs();
        }
        return Transformations.map(allSongs, songs -> {
            if (songs == null || songs.isEmpty()) return Collections.emptyList();
            return com.psthetech.swara.util.ArtistIdentityHelper.getSongsForArtist(artistName, songs);
        });
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

    /** Clears all play history (called from the Home screen's clear button). */
    public void clearPlayHistory() {
        historyRepository.clearHistory();
    }

    /**
     * Updates an existing song in the loaded song list and re-emits,
     * triggering sortedSongs recalculation, plus refreshing albums/artists.
     */
    public void onSongMetadataUpdated(@NonNull Song updatedSong) {
        List<Song> current = allSongs.getValue();
        if (current == null) return;

        List<Song> updatedList = new ArrayList<>(current.size());
        for (Song s : current) {
            if (s.getId() == updatedSong.getId()) {
                updatedList.add(updatedSong);
            } else {
                updatedList.add(s);
            }
        }
        allSongs.setValue(updatedList);
        loadAlbums();
        loadArtists();
    }
}
