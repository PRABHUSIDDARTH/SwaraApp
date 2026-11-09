package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.data.repository.MusicRepository;
import com.psthetech.swara.domain.model.Artist;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.util.ArtistIdentityHelper;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Regression tests for Artist Detail song list resolution,
 * canonical membership logic, and count/list parity.
 */
public class ArtistDetailResolutionTest {

    @Test
    public void testCanonicalArtistNormalization() {
        String k1 = ArtistIdentityHelper.getCanonicalKey("A.R. Rahman");
        String k2 = ArtistIdentityHelper.getCanonicalKey("A. R. Rahman");
        String k3 = ArtistIdentityHelper.getCanonicalKey("A.R.Rahman");

        assertEquals("a.r. rahman", k1);
        assertEquals("a.r. rahman", k2);
        assertEquals("a.r. rahman", k3);
        assertEquals(k1, k2);
        assertEquals(k2, k3);
    }

    @Test
    public void testCompoundArtistBothAppearances() {
        Song song = new Song(10L, "Barso Re", "A.R. Rahman, Shreya Ghoshal", "Guru", 1L, 200000L, 1, 2007, 100L);
        List<Song> songs = Arrays.asList(song);

        // Resolving for A.R. Rahman
        List<Song> rahmanSongs = ArtistIdentityHelper.getSongsForArtist("A.R. Rahman", songs);
        assertEquals(1, rahmanSongs.size());
        assertEquals(10L, rahmanSongs.get(0).getId());

        // Resolving for Shreya Ghoshal
        List<Song> shreyaSongs = ArtistIdentityHelper.getSongsForArtist("Shreya Ghoshal", songs);
        assertEquals(1, shreyaSongs.size());
        assertEquals(10L, shreyaSongs.get(0).getId());

        // Resolving by canonical key
        List<Song> rahmanCanonicalSongs = ArtistIdentityHelper.getSongsForArtist("a.r. rahman", songs);
        assertEquals(1, rahmanCanonicalSongs.size());
        assertEquals(10L, rahmanCanonicalSongs.get(0).getId());
    }

    @Test
    public void testCompoundArtistMurtuzaKhan() {
        Song song = new Song(20L, "In Lamhon Ke Daaman Mein", "A.R. Rahman, Murtuza Khan", "Jodhaa Akbar", 2L, 250000L, 2, 2008, 200L);
        List<Song> songs = Arrays.asList(song);

        List<Song> rahmanSongs = ArtistIdentityHelper.getSongsForArtist("A.R. Rahman", songs);
        assertEquals(1, rahmanSongs.size());
        assertEquals(20L, rahmanSongs.get(0).getId());

        List<Song> murtuzaSongs = ArtistIdentityHelper.getSongsForArtist("Murtuza Khan", songs);
        assertEquals(1, murtuzaSongs.size());
        assertEquals(20L, murtuzaSongs.get(0).getId());
    }

    /**
     * Exact test case specified in bug fix requirements:
     * Song1: "A.R. Rahman, Shreya Ghoshal"
     * Song2: "A.R. Rahman, Murtuza Khan"
     * Song3: "A. R. Rahman"
     * Song4: "Shreya Ghoshal"
     *
     * Expected:
     * A.R. Rahman → Song1, Song2, Song3
     * Shreya Ghoshal → Song1, Song4
     * Murtuza Khan → Song2
     * No duplicates.
     */
    @Test
    public void testExactUserSpecifiedFourSongIndex() {
        Song song1 = new Song(1L, "Song1", "A.R. Rahman, Shreya Ghoshal", "Album A", 1L, 180000L, 1, 2020, 1L);
        Song song2 = new Song(2L, "Song2", "A.R. Rahman, Murtuza Khan", "Album B", 2L, 190000L, 2, 2020, 2L);
        Song song3 = new Song(3L, "Song3", "A. R. Rahman", "Album C", 3L, 200000L, 3, 2020, 3L);
        Song song4 = new Song(4L, "Song4", "Shreya Ghoshal", "Album D", 4L, 210000L, 4, 2020, 4L);

        List<Song> songs = Arrays.asList(song1, song2, song3, song4);

        // 1. Check A.R. Rahman (tested with all 3 spelling variants)
        for (String query : Arrays.asList("A.R. Rahman", "A. R. Rahman", "A.R.Rahman", "a.r. rahman")) {
            List<Song> rahmanSongs = ArtistIdentityHelper.getSongsForArtist(query, songs);
            assertEquals("Expected 3 songs for " + query, 3, rahmanSongs.size());
            assertEquals(1L, rahmanSongs.get(0).getId());
            assertEquals(2L, rahmanSongs.get(1).getId());
            assertEquals(3L, rahmanSongs.get(2).getId());
        }

        // 2. Check Shreya Ghoshal
        List<Song> shreyaSongs = ArtistIdentityHelper.getSongsForArtist("Shreya Ghoshal", songs);
        assertEquals("Expected 2 songs for Shreya Ghoshal", 2, shreyaSongs.size());
        assertEquals(1L, shreyaSongs.get(0).getId());
        assertEquals(4L, shreyaSongs.get(1).getId());

        // 3. Check Murtuza Khan
        List<Song> murtuzaSongs = ArtistIdentityHelper.getSongsForArtist("Murtuza Khan", songs);
        assertEquals("Expected 1 song for Murtuza Khan", 1, murtuzaSongs.size());
        assertEquals(2L, murtuzaSongs.get(0).getId());

        // 4. Verify canonical index structure
        Map<String, List<Song>> index = ArtistIdentityHelper.buildCanonicalArtistIndex(songs);
        assertTrue(index.containsKey("a.r. rahman"));
        assertTrue(index.containsKey("shreya ghoshal"));
        assertTrue(index.containsKey("murtuza khan"));

        assertEquals(3, index.get("a.r. rahman").size());
        assertEquals(2, index.get("shreya ghoshal").size());
        assertEquals(1, index.get("murtuza khan").size());

        // 5. Verify buildArtists generates exactly matching counts and lists
        List<Artist> artists = ArtistIdentityHelper.buildArtists(songs);
        assertEquals(3, artists.size());

        for (Artist artist : artists) {
            String key = artist.getCanonicalKey();
            List<Song> detailList = ArtistIdentityHelper.getSongsForArtist(key, songs);
            assertEquals("Card count must equal detail list size for " + artist.getName(),
                    artist.getSongCount(), detailList.size());
            assertEquals("Artist domain songs must equal detail list size",
                    artist.getSongs().size(), detailList.size());
        }
    }

    @Test
    public void testContainsArtistHelper() {
        String credit = "A.R. Rahman, Murtuza Khan & Qadir Khan feat. Shreya Ghoshal";

        assertTrue(ArtistIdentityHelper.containsArtist(credit, "A.R. Rahman"));
        assertTrue(ArtistIdentityHelper.containsArtist(credit, "A. R. Rahman"));
        assertTrue(ArtistIdentityHelper.containsArtist(credit, "A.R.Rahman"));
        assertTrue(ArtistIdentityHelper.containsArtist(credit, "a.r. rahman"));
        assertTrue(ArtistIdentityHelper.containsArtist(credit, "Murtuza Khan"));
        assertTrue(ArtistIdentityHelper.containsArtist(credit, "Qadir Khan"));
        assertTrue(ArtistIdentityHelper.containsArtist(credit, "Shreya Ghoshal"));

        assertFalse(ArtistIdentityHelper.containsArtist(credit, "Anirudh"));
        assertFalse(ArtistIdentityHelper.containsArtist(credit, "Harris Jayaraj"));
        assertFalse(ArtistIdentityHelper.containsArtist(null, "A.R. Rahman"));
        assertFalse(ArtistIdentityHelper.containsArtist(credit, null));
    }

    @Test
    public void testMusicRepositoryArtistCacheIntegration() {
        Song s1 = new Song(1L, "Alpha", "A.R. Rahman, Shreya Ghoshal", "Alb", 1L, 1000L, 1, 2020, 1L);
        Song s2 = new Song(2L, "Beta", "A.R. Rahman", "Alb", 1L, 1000L, 2, 2020, 2L);
        List<Song> songs = Arrays.asList(s1, s2);

        List<Artist> artists = MusicRepository.buildArtists(songs);
        assertEquals(2, artists.size());

        List<Song> fromCache = MusicRepository.getSongsForArtistFromCache("A.R. Rahman");
        assertEquals(2, fromCache.size());
        assertEquals(1L, fromCache.get(0).getId());
        assertEquals(2L, fromCache.get(1).getId());
    }

    @Test
    public void testDeduplicationOfSameSongUnderSingleArtist() {
        // Even if a song somehow matched multiple tokens mapping to the same canonical key
        Song duplicateCredit = new Song(1L, "Double", "A.R. Rahman, A. R. Rahman", "Alb", 1L, 1000L, 1, 2020, 1L);
        List<Song> songs = Arrays.asList(duplicateCredit);

        List<Song> result = ArtistIdentityHelper.getSongsForArtist("A.R. Rahman", songs);
        assertEquals("Song must not be duplicated under the same artist", 1, result.size());

        List<Artist> artists = ArtistIdentityHelper.buildArtists(songs);
        assertEquals(1, artists.size());
        assertEquals(1, artists.get(0).getSongCount());
        assertEquals(1, artists.get(0).getSongs().size());
    }
}
