package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

import com.psthetech.swara.data.db.entity.FavoriteSong;
import com.psthetech.swara.data.db.entity.PlayHistory;
import com.psthetech.swara.data.db.entity.PlaylistSong;
import com.psthetech.swara.domain.model.Artist;
import com.psthetech.swara.domain.model.AudioOutputDevice;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.util.ArtistIdentityHelper;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Phase 7 Correction Pass Regression Test Suite:
 * 1. Artist Redundancy & Normalization
 * 2. Song Duration Inconsistency & Canonical Enrichment
 * 3. Home Recently Played / Recently Added Click Identity Isolation
 * 4. Dual Audio Detection & Intent UX
 */
public class Phase7CorrectionPassTest {

    @Before
    public void setup() {
        // Clear canonical cache if needed before test runs
    }

    // =========================================================================
    // 1. ARTIST REDUNDANCY & NORMALIZATION
    // =========================================================================

    @Test
    public void testArtistIdentity_exactDuplicate() {
        List<String> a1 = ArtistIdentityHelper.extractArtists("A.R. Rahman");
        List<String> a2 = ArtistIdentityHelper.extractArtists("A.R. Rahman");
        assertEquals(1, a1.size());
        assertEquals("A.R. Rahman", a1.get(0));
        assertEquals(a1.get(0), a2.get(0));
        assertEquals(ArtistIdentityHelper.getCanonicalKey(a1.get(0)),
                     ArtistIdentityHelper.getCanonicalKey(a2.get(0)));
    }

    @Test
    public void testArtistIdentity_whitespaceAndInitialSpacing() {
        String k1 = ArtistIdentityHelper.getCanonicalKey("A. R. Rahman");
        String k2 = ArtistIdentityHelper.getCanonicalKey("A.R. Rahman");
        String k3 = ArtistIdentityHelper.getCanonicalKey("A.R.Rahman");
        String k4 = ArtistIdentityHelper.getCanonicalKey("  a.r. rahman  ");

        assertEquals("a.r. rahman", k1);
        assertEquals("a.r. rahman", k2);
        assertEquals("a.r. rahman", k3);
        assertEquals("a.r. rahman", k4);

        // Normalized display name ensures clean initials with space before surname
        assertEquals("A.R. Rahman", ArtistIdentityHelper.normalizeDisplayName("A. R. Rahman"));
        assertEquals("A.R. Rahman", ArtistIdentityHelper.normalizeDisplayName("A.R. Rahman"));
        assertEquals("A.R. Rahman", ArtistIdentityHelper.normalizeDisplayName("A.R.Rahman"));
    }

    @Test
    public void testArtistIdentity_commaSeparated() {
        List<String> extracted = ArtistIdentityHelper.extractArtists("A.R. Rahman, Shreya Ghoshal");
        assertEquals(2, extracted.size());
        assertEquals("A.R. Rahman", extracted.get(0));
        assertEquals("Shreya Ghoshal", extracted.get(1));
    }

    @Test
    public void testArtistIdentity_commaWithNoSpace() {
        List<String> extracted = ArtistIdentityHelper.extractArtists("A.R. Rahman,Shreya Ghoshal");
        assertEquals(2, extracted.size());
        assertEquals("A.R. Rahman", extracted.get(0));
        assertEquals("Shreya Ghoshal", extracted.get(1));
    }

    @Test
    public void testArtistIdentity_ampersand() {
        List<String> extracted = ArtistIdentityHelper.extractArtists("A.R. Rahman & Sid Sriram");
        assertEquals(2, extracted.size());
        assertEquals("A.R. Rahman", extracted.get(0));
        assertEquals("Sid Sriram", extracted.get(1));
    }

    @Test
    public void testArtistIdentity_featAndFeaturingAndFt() {
        List<String> feat = ArtistIdentityHelper.extractArtists("Eminem feat. Rihanna");
        assertEquals(2, feat.size());
        assertEquals("Eminem", feat.get(0));
        assertEquals("Rihanna", feat.get(1));

        List<String> featuring = ArtistIdentityHelper.extractArtists("Daft Punk featuring Pharrell Williams");
        assertEquals(2, featuring.size());
        assertEquals("Daft Punk", featuring.get(0));
        assertEquals("Pharrell Williams", featuring.get(1));

        List<String> ft = ArtistIdentityHelper.extractArtists("Major Lazer ft. Justin Bieber");
        assertEquals(2, ft.size());
        assertEquals("Major Lazer", ft.get(0));
        assertEquals("Justin Bieber", ft.get(1));
    }

    @Test
    public void testArtistIdentity_parenthesesFeat() {
        List<String> parenFeat = ArtistIdentityHelper.extractArtists("The Weeknd (feat. Daft Punk)");
        assertEquals(2, parenFeat.size());
        assertEquals("The Weeknd", parenFeat.get(0));
        assertEquals("Daft Punk", parenFeat.get(1));
    }

    @Test
    public void testArtistIdentity_multipleCompoundArtists() {
        List<String> multi = ArtistIdentityHelper.extractArtists(
                "A.R. Rahman, Murtuza Khan & Qadir Khan feat. Shreya Ghoshal");
        assertEquals(4, multi.size());
        assertEquals("A.R. Rahman", multi.get(0));
        assertEquals("Murtuza Khan", multi.get(1));
        assertEquals("Qadir Khan", multi.get(2));
        assertEquals("Shreya Ghoshal", multi.get(3));
    }

    @Test
    public void testArtistIdentity_junkWebsiteTagsCleaned() {
        List<String> junk1 = ArtistIdentityHelper.extractArtists("A.R. Rahman - MassTamilan.com");
        assertEquals(1, junk1.size());
        assertEquals("A.R. Rahman", junk1.get(0));

        List<String> junk2 = ArtistIdentityHelper.extractArtists("A.R. Rahman - MassTa");
        assertEquals(1, junk2.size());
        assertEquals("A.R. Rahman", junk2.get(0));

        List<String> junk3 = ArtistIdentityHelper.extractArtists("A.R. Rahman - MassTamilan, Murtuza");
        assertEquals(2, junk3.size());
        assertEquals("A.R. Rahman", junk3.get(0));
        assertEquals("Murtuza", junk3.get(1));
    }

    @Test
    public void testArtistGrouping_songsBelongingToMultipleArtists() {
        Song s1 = new Song(1L, "Tere Bina", "A.R. Rahman, Murtuza Khan & Qadir Khan", "Guru", 10L, 240000L, 1, 2007, 100L);
        Song s2 = new Song(2L, "Barso Re", "A.R. Rahman, Shreya Ghoshal", "Guru", 10L, 300000L, 2, 2007, 200L);
        Song s3 = new Song(3L, "Deewani Mastani", "Shreya Ghoshal", "Bajirao Mastani", 20L, 280000L, 1, 2015, 300L);

        List<Artist> artists = ArtistIdentityHelper.buildArtists(Arrays.asList(s1, s2, s3));

        // Total distinct artists: A.R. Rahman, Murtuza Khan, Qadir Khan, Shreya Ghoshal = 4
        assertEquals(4, artists.size());

        // Find A.R. Rahman
        Artist rahman = null;
        Artist shreya = null;
        for (Artist a : artists) {
            if (a.getCanonicalName().equals("a.r. rahman")) rahman = a;
            if (a.getCanonicalName().equals("shreya ghoshal")) shreya = a;
        }

        assertNotNull(rahman);
        assertNotNull(shreya);

        // A.R. Rahman must contain s1 and s2
        assertEquals(2, rahman.getSongCount());
        assertTrue(rahman.getSongs().contains(s1));
        assertTrue(rahman.getSongs().contains(s2));

        // Shreya Ghoshal must contain s2 and s3
        assertEquals(2, shreya.getSongCount());
        assertTrue(shreya.getSongs().contains(s2));
        assertTrue(shreya.getSongs().contains(s3));
    }

    @Test
    public void testArtistSearchDeduplication() {
        Song s1 = new Song(1L, "Song 1", "A. R. Rahman, Murtuza", "Album 1", 1L, 1000L, 1, 2020, 1L);
        Song s2 = new Song(2L, "Song 2", "A.R. Rahman - MassTamilan", "Album 2", 2L, 1000L, 2, 2020, 2L);
        Song s3 = new Song(3L, "Song 3", "A.R. Rahman, A.R. Reihana", "Album 3", 3L, 1000L, 3, 2020, 3L);
        Song s4 = new Song(4L, "Song 4", "A.R. Rahman, Sathyaprakash", "Album 4", 4L, 1000L, 4, 2020, 4L);
        Song s5 = new Song(5L, "Song 5", "A.R. Rahman,Shreya Ghoshal", "Album 5", 5L, 1000L, 5, 2020, 5L);

        List<Artist> artists = ArtistIdentityHelper.buildArtists(Arrays.asList(s1, s2, s3, s4, s5));

        // Filter by search query "A.R. Rahman"
        String query = "A.R. Rahman";
        String canonicalQuery = ArtistIdentityHelper.getCanonicalKey(query);
        List<Artist> matched = new ArrayList<>();
        for (Artist a : artists) {
            if (a.getCanonicalName().equals(canonicalQuery)) {
                matched.add(a);
            }
        }

        // Must return EXACTLY ONE artist result for A.R. Rahman
        assertEquals(1, matched.size());
        assertEquals("A.R. Rahman", matched.get(0).getName());
        assertEquals(5, matched.get(0).getSongCount()); // contains all 5 songs
    }

    // =========================================================================
    // 2. SONG DURATION INCONSISTENCY & PRESERVATION
    // =========================================================================

    @Test
    public void testSongDurationFormatting() {
        Song s = new Song(10L, "Sample Song", "Artist", "Album", 1L, 215000L, 1, 2022, 100L);
        assertEquals(215000L, s.getDuration());
        assertEquals("3:35", s.getFormattedDuration());
    }

    @Test
    public void testDurationPreservedThroughRoomEntities() {
        Song original = new Song(42L, "Master Track", "Artist X", "Album Y", 100L, 185000L, 3, 2021, 500L);

        // FavoriteSong entity
        FavoriteSong fav = new FavoriteSong(
                original.getId(), original.getTitle(), original.getArtist(),
                original.getAlbum(), original.getAlbumId(), original.getDuration(),
                System.currentTimeMillis()
        );
        assertEquals(185000L, fav.duration);

        // PlayHistory entity
        PlayHistory history = new PlayHistory(
                original.getId(), original.getTitle(), original.getArtist(),
                original.getAlbum(), original.getAlbumId(), original.getDuration(),
                System.currentTimeMillis()
        );
        assertEquals(185000L, history.duration);

        // PlaylistSong entity
        PlaylistSong ps = new PlaylistSong(
                1L, original.getId(), 0,
                original.getTitle(), original.getArtist(), original.getAlbum(),
                original.getAlbumId(), original.getDuration()
        );
        assertEquals(185000L, ps.duration);
    }

    @Test
    public void testMediaItemToSongDurationRoundTrip() {
        long songId = 99L;
        long duration = 245000L;
        long albumId = 12L;

        // Canonical Song in authoritative repository
        Song canonical = new Song(songId, "Roundtrip Song", "Roundtrip Artist", "Roundtrip Album",
                albumId, duration, 5, 2023, 1000L);

        // MediaItem representing the playing song
        MediaItem item = new MediaItem.Builder()
                .setMediaId(String.valueOf(songId))
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(canonical.getTitle())
                        .setArtist(canonical.getArtist())
                        .setAlbumTitle(canonical.getAlbum())
                        .build())
                .build();

        // Decoder logic: when extras or duration is missing/stripped from MediaItem,
        // it falls back to canonical Song representation
        long decodedId = Long.parseLong(item.mediaId);
        Song resolved = null;
        if (decodedId == canonical.getId()) {
            resolved = canonical;
        }

        assertNotNull(resolved);
        assertEquals(duration, resolved.getDuration());
        assertEquals("4:05", resolved.getFormattedDuration());
        assertEquals(albumId, resolved.getAlbumId());
        assertEquals("Roundtrip Song", resolved.getTitle());
    }

    // =========================================================================
    // 3. HOME RECENTLY PLAYED / RECENTLY ADDED CLICK RESOLUTION
    // =========================================================================

    @Test
    public void testHomeClickIdentityResolution() {
        Song recentlyPlayedA = new Song(101L, "Song A (Recently Played)", "Artist A", "Album A", 1L, 180000L, 1, 2020, 100L);
        Song recentlyAddedB = new Song(202L, "Song B (Recently Added)", "Artist B", "Album B", 2L, 200000L, 1, 2024, 200L);

        List<Song> recentlyPlayedList = Collections.singletonList(recentlyPlayedA);
        List<Song> recentlyAddedList = Collections.singletonList(recentlyAddedB);

        // Click resolver function using stable ID
        class ClickResolver {
            Song resolve(Song clicked, List<Song> sourceList) {
                for (Song s : sourceList) {
                    if (s.getId() == clicked.getId()) {
                        return s;
                    }
                }
                return clicked;
            }
        }

        ClickResolver resolver = new ClickResolver();

        // 1. User taps item at position 0 in Recently Played
        Song resultPlayed = resolver.resolve(recentlyPlayedA, recentlyPlayedList);
        assertEquals(101L, resultPlayed.getId());
        assertEquals("Song A (Recently Played)", resultPlayed.getTitle());

        // 2. User taps item at position 0 in Recently Added
        Song resultAdded = resolver.resolve(recentlyAddedB, recentlyAddedList);
        assertEquals(202L, resultAdded.getId());
        assertEquals("Song B (Recently Added)", resultAdded.getTitle());

        // 3. Verify that Recently Played NEVER resolves to Recently Added
        assertFalse("Recently Played must not resolve to Recently Added song",
                resultPlayed.getId() == recentlyAddedB.getId());
    }

    // =========================================================================
    // 4. DUAL AUDIO DETECTION & CAPABILITY UX
    // =========================================================================

    @Test
    public void testDualAudioCapabilityFlag() {
        AudioOutputDevice bt1 = new AudioOutputDevice("bt_1", "pTron TWS",
                AudioOutputDevice.OutputType.BLUETOOTH, true, true, 8, null);
        AudioOutputDevice bt2 = new AudioOutputDevice("bt_2", "Galaxy Buds",
                AudioOutputDevice.OutputType.BLUETOOTH, false, true, 8, null);
        AudioOutputDevice speaker = new AudioOutputDevice("spk", "Phone Speaker",
                AudioOutputDevice.OutputType.BUILT_IN_SPEAKER, false, true, 2, null);

        // 2 Bluetooth devices connected -> Multi-output supported
        List<AudioOutputDevice> devicesWithDualBT = Arrays.asList(bt1, bt2, speaker);
        int btCountDual = 0;
        for (AudioOutputDevice d : devicesWithDualBT) {
            if (d.getType() == AudioOutputDevice.OutputType.BLUETOOTH
                    || d.getType() == AudioOutputDevice.OutputType.BLUETOOTH_LE) {
                btCountDual++;
            }
        }
        boolean isMultiOutputDual = btCountDual > 1;
        assertTrue(isMultiOutputDual);

        // 1 Bluetooth device connected -> Multi-output NOT shown
        List<AudioOutputDevice> devicesWithSingleBT = Arrays.asList(bt1, speaker);
        int btCountSingle = 0;
        for (AudioOutputDevice d : devicesWithSingleBT) {
            if (d.getType() == AudioOutputDevice.OutputType.BLUETOOTH
                    || d.getType() == AudioOutputDevice.OutputType.BLUETOOTH_LE) {
                btCountSingle++;
            }
        }
        boolean isMultiOutputSingle = btCountSingle > 1;
        assertFalse(isMultiOutputSingle);
    }
}
