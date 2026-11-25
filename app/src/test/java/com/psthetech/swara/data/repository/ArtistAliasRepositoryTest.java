package com.psthetech.swara.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.lifecycle.LiveData;

import com.psthetech.swara.data.db.dao.ArtistAliasDao;
import com.psthetech.swara.data.db.entity.ArtistAlias;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.util.ArtistAliasResolver;
import com.psthetech.swara.util.ArtistIdentityHelper;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ArtistAliasRepositoryTest {

    private static class FakeDao implements ArtistAliasDao {
        final Map<String, ArtistAlias> rows = new LinkedHashMap<>();
        public List<ArtistAlias> getAll() { return new ArrayList<>(rows.values()); }
        public LiveData<List<ArtistAlias>> observeAll() { return null; }
        public void upsert(ArtistAlias a) { rows.put(a.fromKey, a); }
        public void delete(String k) { rows.remove(k); }
    }

    private static String key(String n) { return ArtistIdentityHelper.getCanonicalKey(n); }

    private static Song song(long id, String artist, long albumId) {
        return new Song(id, "T" + id, artist, "Album" + albumId, albumId, 1000L, 1, 2020, 0L);
    }

    private final FakeDao dao = new FakeDao();
    private final List<Song> library = new ArrayList<>();
    private final AtomicInteger libVersion = new AtomicInteger(1);
    private final AtomicLong published = new AtomicLong();

    private ArtistAliasRepository repo() {
        return new ArtistAliasRepository(dao, () -> new ArrayList<>(library), libVersion::get,
                Runnable::run, published::set, null);
    }

    @Test
    public void unmergeOverrideBeatsAutoRule() {
        library.add(song(1, "Anirudh", 10));
        library.add(song(2, "Anirudh Ravichander", 10));
        library.add(song(3, "Anirudh Ravichander", 11));
        ArtistAliasRepository r = repo();
        assertEquals(ArtistAliasResolver.identity(), r.resolver()); // first call: identity, build scheduled
        assertEquals(key("Anirudh Ravichander"), r.resolver().resolve(key("Anirudh")));
        long before = published.get();
        r.unmerge(key("Anirudh"));
        assertEquals(key("Anirudh"), r.resolver().resolve(key("Anirudh")));
        assertTrue(published.get() > before);
    }

    @Test
    public void resolverRebuildsOnLibraryVersionChange() {
        ArtistAliasRepository r = repo();
        r.resolver();
        assertEquals(key("Anirudh"), r.resolver().resolve(key("Anirudh")));
        library.add(song(1, "Anirudh", 10));
        library.add(song(2, "Anirudh Ravichander", 10));
        library.add(song(3, "Anirudh Ravichander", 11));
        r.resolver(); // same lib version: no rebuild
        assertEquals(key("Anirudh"), r.resolver().resolve(key("Anirudh")));
        long before = published.get();
        libVersion.incrementAndGet();
        r.resolver(); // schedules rebuild
        assertEquals(key("Anirudh Ravichander"), r.resolver().resolve(key("Anirudh")));
        assertTrue(published.get() > before);
    }

    @Test
    public void selfAndCycleMergesIgnored() {
        ArtistAliasRepository r = repo();
        r.resolver();
        r.merge("a", "a");
        assertTrue(dao.rows.isEmpty());
        r.merge("a", "b");
        assertEquals("b", r.resolver().resolve("a"));
        r.merge("b", "a"); // would cycle
        assertEquals(1, dao.rows.size());
        r.clearOverride("a");
        assertTrue(dao.rows.isEmpty());
    }
}
