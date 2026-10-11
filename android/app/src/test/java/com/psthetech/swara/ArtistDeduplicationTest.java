package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.data.repository.MusicRepository;
import com.psthetech.swara.domain.model.Artist;
import com.psthetech.swara.domain.model.Song;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArtistDeduplicationTest {

    @Test
    public void testCanonicalKey() {
        assertEquals("unknown artist", Artist.getCanonicalKey(null));
        assertEquals("unknown artist", Artist.getCanonicalKey(""));
        assertEquals("unknown artist", Artist.getCanonicalKey("   "));
        assertEquals("unknown artist", Artist.getCanonicalKey("<unknown>"));
        assertEquals("unknown artist", Artist.getCanonicalKey("<UNKNOWN>"));

        assertEquals("anirudh ravichander", Artist.getCanonicalKey("Anirudh Ravichander"));
        assertEquals("anirudh ravichander", Artist.getCanonicalKey("  anirudh ravichander  "));
        assertEquals("anirudh ravichander", Artist.getCanonicalKey("ANIRUDH RAVICHANDER"));
        assertEquals("a.r. rahman", Artist.getCanonicalKey("  A.R. Rahman "));
    }

    @Test
    public void testNormalizeDisplayName() {
        assertEquals("Unknown Artist", Artist.normalizeDisplayName(null));
        assertEquals("Unknown Artist", Artist.normalizeDisplayName(""));
        assertEquals("Unknown Artist", Artist.normalizeDisplayName("   "));
        assertEquals("Unknown Artist", Artist.normalizeDisplayName("<unknown>"));

        assertEquals("A.R. Rahman", Artist.normalizeDisplayName("  A.R. Rahman  "));
        assertEquals("Harris Jayaraj", Artist.normalizeDisplayName("Harris Jayaraj"));
    }

    @Test
    public void testArtistEqualityAndHashCode() {
        Artist a1 = new Artist("Anirudh", 2, 1, 100L, new ArrayList<>());
        Artist a2 = new Artist("  anirudh  ", 5, 2, 100L, new ArrayList<>());
        Artist a3 = new Artist("Harris Jayaraj", 1, 1, 200L, new ArrayList<>());

        assertEquals(a1, a2);
        assertEquals(a1.hashCode(), a2.hashCode());
        assertFalse(a1.equals(a3));
    }

    @Test
    public void testBuildArtistsDeduplication() {
        Song s1 = new Song(1L, "Naa Ready", "Anirudh", "Leo", 10L, 180000L, 1, 2023, 1000L);
        Song s2 = new Song(2L, "Badass", "anirudh", "Leo", 10L, 200000L, 2, 2023, 1000L);
        Song s3 = new Song(3L, "Hukum", " Anirudh  ", "Jailer", 20L, 210000L, 1, 2023, 1000L);
        Song s4 = new Song(4L, "Vaseegara", "Harris Jayaraj", "Minnale", 30L, 300000L, 1, 2001, 1000L);
        Song s5 = new Song(5L, "Track 5", "<unknown>", "Unknown Album", 40L, 120000L, 1, 2020, 1000L);
        Song s6 = new Song(6L, "Track 6", null, "Unknown Album", 40L, 130000L, 2, 2020, 1000L);

        List<Song> songs = Arrays.asList(s1, s2, s3, s4, s5, s6);
        List<Artist> artists = MusicRepository.buildArtists(songs);

        assertNotNull(artists);
        // There should be exactly 3 unique artists: "Anirudh", "Harris Jayaraj", and "Unknown Artist"
        assertEquals(3, artists.size());

        // Find Anirudh
        Artist anirudh = null;
        Artist unknown = null;
        for (Artist a : artists) {
            if (a.getCanonicalName().equals("anirudh")) {
                anirudh = a;
            } else if (a.getCanonicalName().equals("unknown artist")) {
                unknown = a;
            }
        }

        assertNotNull(anirudh);
        assertEquals("Anirudh", anirudh.getName());
        assertEquals(3, anirudh.getSongCount());
        assertEquals(2, anirudh.getAlbumCount()); // Leo and Jailer
        assertEquals(3, anirudh.getSongs().size());

        assertNotNull(unknown);
        assertEquals(2, unknown.getSongCount());
        assertEquals(2, unknown.getSongs().size());
    }

    @Test
    public void testEmptyOrNullSongList() {
        assertTrue(MusicRepository.buildArtists(null).isEmpty());
        assertTrue(MusicRepository.buildArtists(new ArrayList<>()).isEmpty());
    }
}
