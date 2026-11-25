package com.psthetech.swara.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.lifecycle.MutableLiveData;

import com.psthetech.swara.data.db.dao.ArtistAliasDao;
import com.psthetech.swara.data.db.entity.ArtistAlias;
import com.psthetech.swara.domain.model.Artist;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.util.ArtistAliasResolver;
import com.psthetech.swara.util.ArtistIdentityHelper;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class ArtistIndexAndSnapshotTest {

    private static List<Song> library() {
        List<Song> songs = new ArrayList<>();
        songs.add(new Song(1, "Solo", "Anirudh", "Album10", 10L, 1000L, 1, 2020, 0L));
        for (int i = 0; i < 29; i++) {
            songs.add(new Song(100 + i, "Song" + i, "Anirudh Ravichander", "Album10", 10L, 1000L, 1, 2020, 0L));
        }
        return songs;
    }

    @Test
    public void indexIsStaleAfterAliasChange() {
        List<Song> songs = library();
        ArtistAliasResolver merged = ArtistAliasResolver.build(songs, null);
        String root = ArtistIdentityHelper.getCanonicalKey("Anirudh Ravichander");
        String alias = ArtistIdentityHelper.getCanonicalKey("Anirudh");

        MusicRepository.buildArtists(songs, merged);
        assertTrue(MusicRepository.isArtistIndexCurrent(merged));
        assertEquals(30, MusicRepository.artistSongsFromIndex(root, merged).size());

        // Manual unmerge builds a new resolver: the old index must not be served for it.
        Map<String, String> overrides = new HashMap<>();
        overrides.put(alias, null);
        ArtistAliasResolver split = ArtistAliasResolver.build(songs, overrides);
        assertFalse(MusicRepository.isArtistIndexCurrent(split));
        assertNull(MusicRepository.artistSongsFromIndex(root, split));

        MusicRepository.buildArtists(songs, split);
        assertTrue(MusicRepository.isArtistIndexCurrent(split));
        assertEquals(29, MusicRepository.artistSongsFromIndex(root, split).size());
        assertEquals(1, MusicRepository.artistSongsFromIndex(alias, split).size());
        List<Artist> artists = ArtistIdentityHelper.buildArtists(songs, split);
        assertEquals(2, artists.size());
    }

    @Test
    public void snapshotVersionMatchesResolver() {
        ArtistAliasDao dao = new ArtistAliasDao() {
            public List<ArtistAlias> getAll() { return Collections.emptyList(); }
            public androidx.lifecycle.LiveData<List<ArtistAlias>> observeAll() { return null; }
            public void upsert(ArtistAlias a) {}
            public void delete(String k) {}
        };
        AtomicLong published = new AtomicLong();
        MutableLiveData<Long> live = new MutableLiveData<>(0L);
        List<Song> songs = library();
        ArtistAliasRepository repo = new ArtistAliasRepository(dao, () -> songs, () -> 1,
                Runnable::run, published::set, live);
        // Direct executor: the first snapshot() schedules and runs the rebuild synchronously,
        // but returns what was published before it.
        ArtistAliasRepository.Snapshot first = repo.snapshot();
        assertEquals(0L, first.version);
        assertSame(ArtistAliasResolver.identity(), first.resolver);
        ArtistAliasRepository.Snapshot second = repo.snapshot();
        assertEquals(1L, second.version);
        assertEquals(1L, published.get());
        assertEquals("anirudh ravichander",
                second.resolver.resolve(ArtistIdentityHelper.getCanonicalKey("Anirudh")));
        ArtistAliasRepository.Snapshot third = repo.snapshot();
        assertEquals(1L, third.version);
        assertSame(second.resolver, third.resolver);
    }
}
