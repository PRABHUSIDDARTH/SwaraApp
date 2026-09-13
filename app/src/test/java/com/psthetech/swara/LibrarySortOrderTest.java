package com.psthetech.swara;

import static org.junit.Assert.assertEquals;

import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.viewmodel.SortOrder;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LibrarySortOrderTest {

    private List<Song> sampleSongs;

    @Before
    public void setUp() {
        sampleSongs = new ArrayList<>();
        sampleSongs.add(new Song(1L, "Zebra Song", "Artist B", "Album 1", 1L, 300000L, 2024, 0, 1000L));
        sampleSongs.add(new Song(2L, "Alpha Song", "Artist A", "Album 1", 1L, 180000L, 2024, 0, 2000L));
        sampleSongs.add(new Song(3L, "Beta Song", "Artist C", "Album 1", 1L, 240000L, 2024, 0, 1500L));
    }

    @Test
    public void testSortTitleAscending() {
        sortSongs(sampleSongs, SortOrder.TITLE_ASC);
        assertEquals("Alpha Song", sampleSongs.get(0).getTitle());
        assertEquals("Beta Song", sampleSongs.get(1).getTitle());
        assertEquals("Zebra Song", sampleSongs.get(2).getTitle());
    }

    @Test
    public void testSortTitleDescending() {
        sortSongs(sampleSongs, SortOrder.TITLE_DESC);
        assertEquals("Zebra Song", sampleSongs.get(0).getTitle());
        assertEquals("Beta Song", sampleSongs.get(1).getTitle());
        assertEquals("Alpha Song", sampleSongs.get(2).getTitle());
    }

    @Test
    public void testSortDurationAscending() {
        sortSongs(sampleSongs, SortOrder.DURATION_ASC);
        assertEquals(180000L, sampleSongs.get(0).getDuration());
        assertEquals(240000L, sampleSongs.get(1).getDuration());
        assertEquals(300000L, sampleSongs.get(2).getDuration());
    }

    @Test
    public void testSortArtistAscending() {
        sortSongs(sampleSongs, SortOrder.ARTIST_ASC);
        assertEquals("Artist A", sampleSongs.get(0).getArtist());
        assertEquals("Artist B", sampleSongs.get(1).getArtist());
        assertEquals("Artist C", sampleSongs.get(2).getArtist());
    }

    private void sortSongs(List<Song> songs, SortOrder sortOrder) {
        Comparator<Song> comp;
        switch (sortOrder) {
            case TITLE_DESC:
                comp = (a, b) -> b.getTitle().compareToIgnoreCase(a.getTitle());
                break;
            case ARTIST_ASC:
                comp = (a, b) -> a.getArtist().compareToIgnoreCase(b.getArtist());
                break;
            case DURATION_ASC:
                comp = Comparator.comparingLong(Song::getDuration);
                break;
            case TITLE_ASC:
            default:
                comp = (a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle());
                break;
        }
        Collections.sort(songs, comp);
    }
}
