package com.psthetech.swara.domain.model;

import com.psthetech.swara.data.db.entity.Playlist;

import java.util.Collections;
import java.util.List;

/**
 * Aggregated search results across all categories.
 * Produced by SearchViewModel and consumed by SearchResultsAdapter.
 *
 * All lists are non-null (may be empty).
 */
public class SearchResults {

    public final List<Song> songs;
    public final List<Album> albums;
    public final List<Artist> artists;
    public final List<Playlist> playlists;

    public SearchResults(List<Song> songs,
                         List<Album> albums,
                         List<Artist> artists,
                         List<Playlist> playlists) {
        this.songs     = songs     != null ? songs     : Collections.emptyList();
        this.albums    = albums    != null ? albums    : Collections.emptyList();
        this.artists   = artists   != null ? artists   : Collections.emptyList();
        this.playlists = playlists != null ? playlists : Collections.emptyList();
    }

    public static SearchResults empty() {
        return new SearchResults(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    public boolean isEmpty() {
        return songs.isEmpty() && albums.isEmpty()
                && artists.isEmpty() && playlists.isEmpty();
    }

    public int totalCount() {
        return songs.size() + albums.size() + artists.size() + playlists.size();
    }
}
