package com.psthetech.swara.ui.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.psthetech.swara.SwaraApplication;
import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.data.db.entity.Playlist;
import com.psthetech.swara.data.repository.MusicRepository;
import com.psthetech.swara.data.repository.PlaylistRepository;
import com.psthetech.swara.domain.model.Album;
import com.psthetech.swara.domain.model.Artist;
import com.psthetech.swara.domain.model.SearchResults;
import com.psthetech.swara.domain.model.Song;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ViewModel dedicated to search.
 *
 * Architecture:
 *  - Single query triggers concurrent search across MediaStore (Songs + Albums + Artists)
 *    and Room (Playlists).
 *  - 300ms debounce via Handler — prevents MediaStore overload on rapid typing.
 *  - Results are aggregated into SearchResults and posted to main thread via LiveData.
 *  - All I/O on IoExecutor (MediaStore) or DbExecutor (Room).
 *  - No Kotlin, no coroutines.
 */
public class SearchViewModel extends AndroidViewModel {

    private static final int DEBOUNCE_MS = 300;

    private final MusicRepository musicRepository;
    private final PlaylistRepository playlistRepository;

    private final MutableLiveData<SearchResults> searchResults =
            new MutableLiveData<>(SearchResults.empty());
    private final MutableLiveData<Boolean> isSearching = new MutableLiveData<>(false);

    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    // Current query — used to guard against stale results
    private volatile String lastQuery = "";

    public SearchViewModel(@NonNull Application application) {
        super(application);
        musicRepository = new MusicRepository(application);
        playlistRepository = new PlaylistRepository(AppDatabase.getInstance(application));
    }

    // ===== Public API =====

    /** Call this when the user types. Triggers debounced search. */
    public void search(String rawQuery) {
        final String query = rawQuery == null ? "" : rawQuery.trim();

        // Cancel any pending debounced search
        if (pendingSearch != null) {
            debounceHandler.removeCallbacks(pendingSearch);
        }

        if (query.isEmpty()) {
            lastQuery = "";
            searchResults.setValue(SearchResults.empty());
            isSearching.setValue(false);
            return;
        }

        isSearching.setValue(true);

        pendingSearch = () -> {
            lastQuery = query;
            executeSearch(query);
        };
        debounceHandler.postDelayed(pendingSearch, DEBOUNCE_MS);
    }

    public LiveData<SearchResults> getSearchResults() { return searchResults; }
    public LiveData<Boolean> getIsSearching() { return isSearching; }

    // ===== Internal =====

    private void executeSearch(String query) {
        // Use an array to track partial completion safely
        final String lower = query.toLowerCase(Locale.getDefault());
        final Object[] lock = new Object[1];
        lock[0] = new PendingSearchState();

        // Search MediaStore (songs → albums + artists derived client-side)
        musicRepository.searchAllCategories(lower,
                new MusicRepository.SearchCallback() {
                    @Override
                    public void onResult(List<Song> songs, List<Album> albums, List<Artist> artists) {
                        synchronized (lock[0]) {
                            PendingSearchState state = (PendingSearchState) lock[0];
                            state.songs = songs;
                            state.albums = albums;
                            state.artists = artists;
                            state.mediaStoreReady = true;
                            maybePublish(query, state);
                        }
                    }

                    @Override
                    public void onError() {
                        synchronized (lock[0]) {
                            PendingSearchState state = (PendingSearchState) lock[0];
                            state.mediaStoreReady = true;
                            maybePublish(query, state);
                        }
                    }
                });

        // Search Room playlists (background thread)
        SwaraApplication.getInstance().getDbExecutor().execute(() -> {
            List<Playlist> allPlaylists = playlistRepository.getAllPlaylistsBlocking();
            List<Playlist> matched = new ArrayList<>();
            for (Playlist p : allPlaylists) {
                if (p.name != null && p.name.toLowerCase(Locale.getDefault()).contains(lower)) {
                    matched.add(p);
                }
            }
            synchronized (lock[0]) {
                PendingSearchState state = (PendingSearchState) lock[0];
                state.playlists = matched;
                state.roomReady = true;
                maybePublish(query, state);
            }
        });
    }

    /**
     * Called from both async callbacks (MediaStore + Room). Publishes results only when
     * both sources are ready AND the query hasn't been superseded.
     * Must be called inside synchronized(lock).
     */
    private void maybePublish(String query, PendingSearchState state) {
        if (!state.mediaStoreReady || !state.roomReady) return;

        // Check the query is still current (user may have typed more)
        if (!query.equals(lastQuery)) return;

        SearchResults results = new SearchResults(
                state.songs, state.albums, state.artists, state.playlists);

        debounceHandler.post(() -> {
            isSearching.setValue(false);
            searchResults.setValue(results);
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (pendingSearch != null) {
            debounceHandler.removeCallbacks(pendingSearch);
        }
    }

    // ===== Inner state holder =====

    private static class PendingSearchState {
        List<Song>    songs    = new ArrayList<>();
        List<Album>   albums   = new ArrayList<>();
        List<Artist>  artists  = new ArrayList<>();
        List<Playlist> playlists = new ArrayList<>();
        boolean mediaStoreReady = false;
        boolean roomReady       = false;
    }
}
