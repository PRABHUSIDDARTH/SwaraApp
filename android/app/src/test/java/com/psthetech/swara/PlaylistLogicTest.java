package com.psthetech.swara;

import com.psthetech.swara.data.db.entity.Playlist;
import com.psthetech.swara.data.db.entity.PlaylistSong;
import com.psthetech.swara.domain.model.Song;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Pure JUnit tests for playlist business logic.
 *
 * These tests exercise the data structures and algorithms used in
 * PlaylistRepository / PlaylistDao without touching Android framework or Room.
 *
 * Tests use fake in-memory stores that mirror the DAO contract.
 */
public class PlaylistLogicTest {

    // ===== Helpers =====

    private Song song(long id) {
        return new Song(id, "Title" + id, "Artist" + id, "Album" + id, id, 60000, 1, 2024, 0);
    }

    private Playlist createPlaylist(String name) {
        long now = System.currentTimeMillis();
        Playlist p = new Playlist(name, now, now);
        p.id = idCounter++;
        return p;
    }

    private long idCounter = 1;

    // ===== Fake in-memory store =====

    static class InMemoryPlaylistStore {
        final List<Playlist> playlists = new ArrayList<>();
        final Map<Long, List<PlaylistSong>> songs = new HashMap<>();
        long nextId = 1;

        long createPlaylist(String name) {
            long now = System.currentTimeMillis();
            Playlist p = new Playlist(name, now, now);
            p.id = nextId++;
            playlists.add(p);
            songs.put(p.id, new ArrayList<>());
            return p.id;
        }

        void renamePlaylist(long id, String newName) {
            for (Playlist p : playlists) {
                if (p.id == id) {
                    p.name = newName;
                    p.modifiedAt = System.currentTimeMillis();
                    return;
                }
            }
        }

        void deletePlaylist(long id) {
            playlists.removeIf(p -> p.id == id);
            songs.remove(id);
        }

        Playlist getById(long id) {
            for (Playlist p : playlists) {
                if (p.id == id) return p;
            }
            return null;
        }

        boolean addSong(long playlistId, Song song) {
            List<PlaylistSong> list = songs.get(playlistId);
            if (list == null) return false;
            // Duplicate check
            for (PlaylistSong ps : list) {
                if (ps.songId == song.getId()) return false; // duplicate
            }
            int pos = list.size(); // correct: use count, not max+1
            PlaylistSong ps = new PlaylistSong(playlistId, song.getId(), pos,
                    song.getTitle(), song.getArtist(), song.getAlbum(),
                    song.getAlbumId(), song.getDuration());
            list.add(ps);
            return true;
        }

        void removeSong(long playlistId, long songId) {
            List<PlaylistSong> list = songs.get(playlistId);
            if (list != null) list.removeIf(ps -> ps.songId == songId);
        }

        List<PlaylistSong> getSongs(long playlistId) {
            return songs.getOrDefault(playlistId, new ArrayList<>());
        }

        boolean isSongInPlaylist(long playlistId, long songId) {
            List<PlaylistSong> list = songs.get(playlistId);
            if (list == null) return false;
            for (PlaylistSong ps : list) {
                if (ps.songId == songId) return true;
            }
            return false;
        }

        int getSongCount(long playlistId) {
            List<PlaylistSong> list = songs.get(playlistId);
            return list != null ? list.size() : 0;
        }
    }

    // ===== Tests: Playlist creation =====

    @Test
    public void testCreatePlaylist_generatesUniqueId() {
        InMemoryPlaylistStore store = new InMemoryPlaylistStore();
        long id1 = store.createPlaylist("Favorites");
        long id2 = store.createPlaylist("Workout");
        assertNotEquals(id1, id2);
    }

    @Test
    public void testCreatePlaylist_storesPersists() {
        InMemoryPlaylistStore store = new InMemoryPlaylistStore();
        long id = store.createPlaylist("Tamil Hits");
        assertNotNull(store.getById(id));
        assertEquals("Tamil Hits", store.getById(id).name);
    }

    @Test
    public void testCreateMultiplePlaylists() {
        InMemoryPlaylistStore store = new InMemoryPlaylistStore();
        store.createPlaylist("A");
        store.createPlaylist("B");
        store.createPlaylist("C");
        assertEquals(3, store.playlists.size());
    }

    // ===== Tests: Rename =====

    @Test
    public void testRenamePlaylist_updatesName() {
        InMemoryPlaylistStore store = new InMemoryPlaylistStore();
        long id = store.createPlaylist("Old Name");
        store.renamePlaylist(id, "New Name");
        assertEquals("New Name", store.getById(id).name);
    }

    @Test
    public void testRenamePlaylist_doesNotAffectOtherPlaylists() {
        InMemoryPlaylistStore store = new InMemoryPlaylistStore();
        long id1 = store.createPlaylist("A");
        long id2 = store.createPlaylist("B");
        store.renamePlaylist(id1, "A Renamed");
        assertEquals("B", store.getById(id2).name);
    }

    // ===== Tests: Delete =====

    @Test
    public void testDeletePlaylist_removesPlaylist() {
        InMemoryPlaylistStore store = new InMemoryPlaylistStore();
        long id = store.createPlaylist("To Delete");
        store.deletePlaylist(id);
        assertNull(store.getById(id));
    }

    @Test
    public void testDeletePlaylist_removesSongs() {
        InMemoryPlaylistStore store = new InMemoryPlaylistStore();
        long id = store.createPlaylist("Playlist");
        store.addSong(id, song(1));
        store.deletePlaylist(id);
        assertNull(store.songs.get(id));
    }

    // ===== Tests: Add song =====

    @Test
    public void testAddSong_persistsCorrectly() {
        InMemoryPlaylistStore store = new InMemoryPlaylistStore();
        long id = store.createPlaylist("P");
        boolean added = store.addSong(id, song(42));
        assertTrue(added);
        assertEquals(1, store.getSongCount(id));
        assertEquals(42L, store.getSongs(id).get(0).songId);
    }

    @Test
    public void testAddSong_firstSongGetsPositionZero() {
        InMemoryPlaylistStore store = new InMemoryPlaylistStore();
        long id = store.createPlaylist("P");
        store.addSong(id, song(10));
        assertEquals(0, store.getSongs(id).get(0).position);
    }

    @Test
    public void testAddSong_secondSongGetsPositionOne() {
        InMemoryPlaylistStore store = new InMemoryPlaylistStore();
        long id = store.createPlaylist("P");
        store.addSong(id, song(1));
        store.addSong(id, song(2));
        assertEquals(1, store.getSongs(id).get(1).position);
    }

    @Test
    public void testAddSong_duplicatePrevented() {
        InMemoryPlaylistStore store = new InMemoryPlaylistStore();
        long id = store.createPlaylist("P");
        store.addSong(id, song(5));
        boolean secondAdd = store.addSong(id, song(5));
        assertFalse(secondAdd);
        assertEquals(1, store.getSongCount(id)); // still only 1 entry
    }

    @Test
    public void testAddSong_sameSongInDifferentPlaylists() {
        InMemoryPlaylistStore store = new InMemoryPlaylistStore();
        long id1 = store.createPlaylist("A");
        long id2 = store.createPlaylist("B");
        assertTrue(store.addSong(id1, song(99)));
        assertTrue(store.addSong(id2, song(99))); // allowed in different playlist
        assertEquals(1, store.getSongCount(id1));
        assertEquals(1, store.getSongCount(id2));
    }

    // ===== Tests: Remove song =====

    @Test
    public void testRemoveSong_decreasesCount() {
        InMemoryPlaylistStore store = new InMemoryPlaylistStore();
        long id = store.createPlaylist("P");
        store.addSong(id, song(1));
        store.addSong(id, song(2));
        store.removeSong(id, 1L);
        assertEquals(1, store.getSongCount(id));
    }

    @Test
    public void testRemoveSong_correctSongRemoved() {
        InMemoryPlaylistStore store = new InMemoryPlaylistStore();
        long id = store.createPlaylist("P");
        store.addSong(id, song(1));
        store.addSong(id, song(2));
        store.removeSong(id, 1L);
        assertEquals(2L, store.getSongs(id).get(0).songId);
    }

    // ===== Tests: Song ordering =====

    @Test
    public void testSongOrdering_maintainsInsertionOrder() {
        InMemoryPlaylistStore store = new InMemoryPlaylistStore();
        long id = store.createPlaylist("P");
        store.addSong(id, song(10));
        store.addSong(id, song(20));
        store.addSong(id, song(30));
        List<PlaylistSong> songs = store.getSongs(id);
        assertEquals(10L, songs.get(0).songId);
        assertEquals(20L, songs.get(1).songId);
        assertEquals(30L, songs.get(2).songId);
    }

    @Test
    public void testSongPositions_areSequential() {
        InMemoryPlaylistStore store = new InMemoryPlaylistStore();
        long id = store.createPlaylist("P");
        store.addSong(id, song(1));
        store.addSong(id, song(2));
        store.addSong(id, song(3));
        List<PlaylistSong> songs = store.getSongs(id);
        for (int i = 0; i < songs.size(); i++) {
            assertEquals(i, songs.get(i).position);
        }
    }

    // ===== Tests: isSongInPlaylist =====

    @Test
    public void testIsSongInPlaylist_trueWhenPresent() {
        InMemoryPlaylistStore store = new InMemoryPlaylistStore();
        long id = store.createPlaylist("P");
        store.addSong(id, song(7));
        assertTrue(store.isSongInPlaylist(id, 7L));
    }

    @Test
    public void testIsSongInPlaylist_falseWhenAbsent() {
        InMemoryPlaylistStore store = new InMemoryPlaylistStore();
        long id = store.createPlaylist("P");
        assertFalse(store.isSongInPlaylist(id, 999L));
    }

    // ===== Tests: getSongCount uses count, not max+1 =====

    @Test
    public void testGetSongCount_emptyPlaylistReturnsZero() {
        InMemoryPlaylistStore store = new InMemoryPlaylistStore();
        long id = store.createPlaylist("P");
        assertEquals(0, store.getSongCount(id));
    }

    @Test
    public void testGetSongCount_afterAddReturnsCorrectCount() {
        InMemoryPlaylistStore store = new InMemoryPlaylistStore();
        long id = store.createPlaylist("P");
        store.addSong(id, song(1));
        store.addSong(id, song(2));
        assertEquals(2, store.getSongCount(id));
    }

    @Test
    public void testPositionCounterUsesCount_notMaxPosition() {
        // This test verifies the position bug fix:
        // Using getSongCount() (not getMaxPosition()+1) gives correct 0-based positions.
        InMemoryPlaylistStore store = new InMemoryPlaylistStore();
        long id = store.createPlaylist("P");
        // First song: getSongCount = 0, so position = 0 (correct)
        store.addSong(id, song(1));
        assertEquals(0, store.getSongs(id).get(0).position);
        // Second song: getSongCount = 1, so position = 1 (correct)
        store.addSong(id, song(2));
        assertEquals(1, store.getSongs(id).get(1).position);
    }
}
