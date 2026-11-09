package com.psthetech.swara.ui.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.psthetech.swara.domain.model.Song;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SearchQueueBuilderTest {

    private static Song song(long id) {
        return new Song(id, "T" + id, "A", "Al", 0L, 1000, 0, 0, 0L);
    }

    @Test
    public void tappedSongStartsQueueAtItsLibraryPosition() {
        List<Song> library = Arrays.asList(song(1), song(2), song(3), song(4));
        SearchQueueBuilder.Queue q = SearchQueueBuilder.build(library, song(3));
        assertEquals(4, q.songs.size());
        assertEquals(2, q.startIndex);
        assertEquals(4L, q.songs.get(q.startIndex + 1).getId());
    }

    @Test
    public void missingSongIsPrependedThenLibraryFollows() {
        List<Song> library = Arrays.asList(song(1), song(2));
        Song tapped = song(9);
        SearchQueueBuilder.Queue q = SearchQueueBuilder.build(library, tapped);
        assertEquals(3, q.songs.size());
        assertEquals(0, q.startIndex);
        assertSame(tapped, q.songs.get(0));
        assertEquals(1L, q.songs.get(1).getId());
    }

    @Test
    public void emptyOrNullLibraryFallsBackToSingleSong() {
        Song tapped = song(5);
        assertEquals(1, SearchQueueBuilder.build(null, tapped).songs.size());
        assertEquals(1, SearchQueueBuilder.build(Collections.emptyList(), tapped).songs.size());
    }
}
