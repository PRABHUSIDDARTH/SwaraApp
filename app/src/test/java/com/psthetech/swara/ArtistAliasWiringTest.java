package com.psthetech.swara;

import static org.junit.Assert.assertEquals;

import com.psthetech.swara.domain.model.Artist;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.util.ArtistAliasResolver;
import com.psthetech.swara.util.ArtistIdentityHelper;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ArtistAliasWiringTest {

    static List<Song> anirudhLibrary() {
        List<Song> songs = new ArrayList<>();
        songs.add(new Song(1, "Solo", "Anirudh", "Album10", 10L, 1000L, 1, 2020, 0L));
        for (int i = 0; i < 29; i++) {
            songs.add(new Song(100 + i, "Song" + i, "Anirudh Ravichander", "Album10", 10L, 1000L, 1, 2020, 0L));
        }
        return songs;
    }

    @Test
    public void buildArtistsMergesAnirudh() {
        List<Song> songs = anirudhLibrary();
        ArtistAliasResolver r = ArtistAliasResolver.build(songs, null);
        List<Artist> artists = ArtistIdentityHelper.buildArtists(songs, r);
        assertEquals(1, artists.size());
        assertEquals("Anirudh Ravichander", artists.get(0).getName());
        assertEquals(30, artists.get(0).getSongs().size());
        assertEquals(30, artists.get(0).getSongCount());
    }

    @Test
    public void getSongsForAliasedKeyReturnsMergedSongs() {
        List<Song> songs = anirudhLibrary();
        ArtistAliasResolver r = ArtistAliasResolver.build(songs, null);
        String key = ArtistIdentityHelper.getCanonicalKey("Anirudh");
        assertEquals(30, ArtistIdentityHelper.getSongsForArtist(key, songs, r).size());
        assertEquals(1, ArtistIdentityHelper.getSongsForArtist(key, songs).size());
    }
}
