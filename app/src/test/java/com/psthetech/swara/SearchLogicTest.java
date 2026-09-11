package com.psthetech.swara;

import com.psthetech.swara.data.db.entity.Playlist;
import com.psthetech.swara.domain.model.Song;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.*;

/**
 * Pure JUnit tests for search business logic.
 *
 * These tests exercise the matching/filtering logic used in SearchViewModel /
 * MusicRepository.searchAllCategories() without touching Android framework or MediaStore.
 *
 * The "fake search" implementation here mirrors the filtering logic in the production code.
 */
public class SearchLogicTest {

    private List<Song> songLibrary;
    private List<Playlist> playlistLibrary;

    // ===== Fake search engine (mirrors MusicRepository.searchAllCategories logic) =====

    private static List<Song> searchSongs(List<Song> library, String query) {
        if (query == null || query.trim().isEmpty()) return new ArrayList<>();
        String lower = query.trim().toLowerCase(Locale.getDefault());
        List<Song> results = new ArrayList<>();
        for (Song s : library) {
            if (s.getTitle().toLowerCase(Locale.getDefault()).contains(lower)) {
                results.add(s);
            }
        }
        return results;
    }

    private static List<String> searchArtists(List<Song> library, String query) {
        if (query == null || query.trim().isEmpty()) return new ArrayList<>();
        String lower = query.trim().toLowerCase(Locale.getDefault());
        List<String> seen = new ArrayList<>();
        for (Song s : library) {
            String artist = s.getArtist().toLowerCase(Locale.getDefault());
            if (artist.contains(lower) && !seen.contains(s.getArtist())) {
                seen.add(s.getArtist());
            }
        }
        return seen;
    }

    private static List<String> searchAlbums(List<Song> library, String query) {
        if (query == null || query.trim().isEmpty()) return new ArrayList<>();
        String lower = query.trim().toLowerCase(Locale.getDefault());
        List<String> seen = new ArrayList<>();
        for (Song s : library) {
            String album = s.getAlbum().toLowerCase(Locale.getDefault());
            if (album.contains(lower) && !seen.contains(s.getAlbum())) {
                seen.add(s.getAlbum());
            }
        }
        return seen;
    }

    private static List<Playlist> searchPlaylists(List<Playlist> playlists, String query) {
        if (query == null || query.trim().isEmpty()) return new ArrayList<>();
        String lower = query.trim().toLowerCase(Locale.getDefault());
        List<Playlist> results = new ArrayList<>();
        for (Playlist p : playlists) {
            if (p.name != null && p.name.toLowerCase(Locale.getDefault()).contains(lower)) {
                results.add(p);
            }
        }
        return results;
    }

    private static Song makeSong(long id, String title, String artist, String album) {
        return new Song(id, title, artist, album, id, 60000, 1, 2024, 0);
    }

    private static Playlist makePlaylist(long id, String name) {
        Playlist p = new Playlist(name, 0, 0);
        p.id = id;
        return p;
    }

    @Before
    public void setUp() {
        songLibrary = new ArrayList<>();
        songLibrary.add(makeSong(1, "Vennilave",       "A.R. Rahman",     "Minsara Kanavu"));
        songLibrary.add(makeSong(2, "Enna Solla",      "A.R. Rahman",     "Kadal"));
        songLibrary.add(makeSong(3, "Nee Partha Vizhigal","Sid Sriram",   "Enai Noki Paayum Thota"));
        songLibrary.add(makeSong(4, "Rock On",         "Farhan Akhtar",   "Rock On"));
        songLibrary.add(makeSong(5, "Rockstar",        "Mohit Chauhan",   "Rockstar"));
        songLibrary.add(makeSong(6, "மழை வருகிறது",    "Haricharan",      "Tamil Chill"));
        songLibrary.add(makeSong(7, "Love Me Tender",  "Elvis Presley",   "Elvis"));
        songLibrary.add(makeSong(8, "Hey Jude",        "The Beatles",     "Past Masters"));
        songLibrary.add(makeSong(9, "Let It Be",       "The Beatles",     "Let It Be"));
        songLibrary.add(makeSong(10, "Yesterday",      "The Beatles",     "Help!"));

        playlistLibrary = new ArrayList<>();
        playlistLibrary.add(makePlaylist(1, "Favorites"));
        playlistLibrary.add(makePlaylist(2, "Workout Mix"));
        playlistLibrary.add(makePlaylist(3, "Tamil Classical"));
        playlistLibrary.add(makePlaylist(4, "Road Trip"));
    }

    // ===== Song search =====

    @Test
    public void testSearch_songTitleMatch() {
        List<Song> results = searchSongs(songLibrary, "vennilave");
        assertEquals(1, results.size());
        assertEquals("Vennilave", results.get(0).getTitle());
    }

    @Test
    public void testSearch_partialTitleMatch() {
        List<Song> results = searchSongs(songLibrary, "ven");
        assertEquals(1, results.size());
    }

    @Test
    public void testSearch_caseInsensitive_lowercase() {
        List<Song> results = searchSongs(songLibrary, "rock on");
        assertFalse(results.isEmpty());
        assertEquals("Rock On", results.get(0).getTitle());
    }

    @Test
    public void testSearch_caseInsensitive_uppercase() {
        List<Song> results = searchSongs(songLibrary, "YESTERDAY");
        assertEquals(1, results.size());
        assertEquals("Yesterday", results.get(0).getTitle());
    }

    @Test
    public void testSearch_mixedCase() {
        List<Song> results = searchSongs(songLibrary, "RoCk");
        // Matches "Rock On" and "Rockstar"
        assertTrue(results.size() >= 1);
        for (Song s : results) {
            assertTrue(s.getTitle().toLowerCase(Locale.getDefault()).contains("rock"));
        }
    }

    @Test
    public void testSearch_emptyQuery_returnsEmpty() {
        List<Song> results = searchSongs(songLibrary, "");
        assertTrue(results.isEmpty());
    }

    @Test
    public void testSearch_nullQuery_returnsEmpty() {
        List<Song> results = searchSongs(songLibrary, null);
        assertTrue(results.isEmpty());
    }

    @Test
    public void testSearch_noMatch_returnsEmpty() {
        List<Song> results = searchSongs(songLibrary, "xyznotexist");
        assertTrue(results.isEmpty());
    }

    @Test
    public void testSearch_trimWhitespace() {
        List<Song> results = searchSongs(songLibrary, "  hey jude  ");
        assertEquals(1, results.size());
    }

    @Test
    public void testSearch_queryWithSpaceInMiddle() {
        List<Song> results = searchSongs(songLibrary, "hey jude");
        assertEquals(1, results.size());
        assertEquals("Hey Jude", results.get(0).getTitle());
    }

    // ===== Artist search =====

    @Test
    public void testSearch_artistMatch() {
        List<String> results = searchArtists(songLibrary, "beatles");
        assertEquals(1, results.size());
        assertEquals("The Beatles", results.get(0));
    }

    @Test
    public void testSearch_artist_caseInsensitive() {
        List<String> results = searchArtists(songLibrary, "A.R. RAHMAN");
        assertEquals(1, results.size());
    }

    @Test
    public void testSearch_artist_deduplicates() {
        // "The Beatles" should appear only once even though 3 songs exist
        List<String> artists = searchArtists(songLibrary, "beatles");
        assertEquals(1, artists.size());
    }

    @Test
    public void testSearch_artist_noMatch() {
        List<String> artists = searchArtists(songLibrary, "Radiohead");
        assertTrue(artists.isEmpty());
    }

    // ===== Album search =====

    @Test
    public void testSearch_albumMatch() {
        List<String> results = searchAlbums(songLibrary, "kadal");
        assertEquals(1, results.size());
        assertEquals("Kadal", results.get(0));
    }

    @Test
    public void testSearch_album_partialMatch() {
        List<String> results = searchAlbums(songLibrary, "minsara");
        assertEquals(1, results.size());
    }

    @Test
    public void testSearch_album_caseInsensitive() {
        List<String> results = searchAlbums(songLibrary, "KADAL");
        assertEquals(1, results.size());
    }

    // ===== Playlist search =====

    @Test
    public void testSearch_playlistNameMatch() {
        List<Playlist> results = searchPlaylists(playlistLibrary, "favorites");
        assertEquals(1, results.size());
        assertEquals("Favorites", results.get(0).name);
    }

    @Test
    public void testSearch_playlist_caseInsensitive() {
        List<Playlist> results = searchPlaylists(playlistLibrary, "WORKOUT");
        assertEquals(1, results.size());
    }

    @Test
    public void testSearch_playlist_partialMatch() {
        List<Playlist> results = searchPlaylists(playlistLibrary, "tamil");
        assertEquals(1, results.size());
        assertEquals("Tamil Classical", results.get(0).name);
    }

    @Test
    public void testSearch_playlist_noMatch() {
        List<Playlist> results = searchPlaylists(playlistLibrary, "jazz");
        assertTrue(results.isEmpty());
    }

    @Test
    public void testSearch_playlist_emptyQuery_returnsEmpty() {
        List<Playlist> results = searchPlaylists(playlistLibrary, "");
        assertTrue(results.isEmpty());
    }

    // ===== Edge cases =====

    @Test
    public void testSearch_singleCharacter() {
        // "a" should match many songs/artists
        List<Song> results = searchSongs(songLibrary, "a");
        assertFalse(results.isEmpty());
    }

    @Test
    public void testSearch_specialCharacterInTitle() {
        // Tamil title "மழை வருகிறது" — search for "மழை"
        List<Song> results = searchSongs(songLibrary, "மழை");
        assertEquals(1, results.size());
    }
}
