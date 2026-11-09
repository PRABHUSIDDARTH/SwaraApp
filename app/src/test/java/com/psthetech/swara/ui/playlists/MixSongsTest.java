package com.psthetech.swara.ui.playlists;

import com.psthetech.swara.domain.model.Song;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MixSongsTest {

    private static Song song(long id) {
        return new Song(id, "Title" + id, "Artist" + id, "Album" + id, id, 60000, 1, 2024, 0);
    }

    private static Map<Long, Song> library(long... ids) {
        Map<Long, Song> m = new HashMap<>();
        for (long id : ids) m.put(id, song(id));
        return m;
    }

    private static long[] ids(List<Song> songs) {
        long[] out = new long[songs.size()];
        for (int i = 0; i < songs.size(); i++) out[i] = songs.get(i).getId();
        return out;
    }

    @Test
    public void keepsMixOrder() {
        Map<Long, Song> lib = library(1, 2, 3);
        List<Song> out = MixSongs.resolveInOrder(Arrays.asList(3L, 1L, 2L), lib::get);
        assertEquals(Arrays.toString(new long[]{3, 1, 2}), Arrays.toString(ids(out)));
    }

    @Test
    public void skipsSongsMissingFromLibrary() {
        Map<Long, Song> lib = library(1, 3);
        List<Song> out = MixSongs.resolveInOrder(Arrays.asList(1L, 2L, 3L), lib::get);
        assertEquals(Arrays.toString(new long[]{1, 3}), Arrays.toString(ids(out)));
    }

    @Test
    public void dropsDuplicateIdsKeepingFirst() {
        Map<Long, Song> lib = library(1, 2);
        List<Song> out = MixSongs.resolveInOrder(Arrays.asList(2L, 1L, 2L, 1L), lib::get);
        assertEquals(Arrays.toString(new long[]{2, 1}), Arrays.toString(ids(out)));
    }

    @Test
    public void emptyOrNullIdsGiveEmptyList() {
        Map<Long, Song> lib = library(1);
        assertTrue(MixSongs.resolveInOrder(Collections.emptyList(), lib::get).isEmpty());
        assertTrue(MixSongs.resolveInOrder(null, lib::get).isEmpty());
    }

    @Test
    public void lookupExceptionSkipsOnlyThatSong() {
        Map<Long, Song> lib = library(1, 3);
        List<Song> out = MixSongs.resolveInOrder(Arrays.asList(1L, 2L, 3L), id -> {
            if (id == 2L) throw new RuntimeException("boom");
            return lib.get(id);
        });
        assertEquals(Arrays.toString(new long[]{1, 3}), Arrays.toString(ids(out)));
    }
}
