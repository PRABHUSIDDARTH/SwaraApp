package com.psthetech.swara.wrapped.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.wrapped.SongInfo;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SongMatcherTest {

    private static SongInfo s(long id, String title, String artist, String album) {
        return new SongInfo(id, title, artist, null, album, id, 0);
    }

    @Test
    public void normalize_stripsSiteTags() {
        assertEquals("kannukulla", SongMatcher.normalize("Kannukulla - MassTamilan.dev"));
        assertEquals("let me down slowly", SongMatcher.normalize("Let Me Down Slowly(PagalWorldl)"));
        assertEquals("ra ra reddy i am ready", SongMatcher.normalize("[iSongs.info] 02 - Ra Ra Reddy I Am Ready"));
        assertEquals("aura 10 10", SongMatcher.normalize("  Aura   10/10 - MassTamilan "));
    }

    @Test
    public void matchByTitleArtist_ignoringSiteTagsAndCase() {
        List<SongInfo> local = Arrays.asList(s(500, "Kannukulla", "Sai Abhyankkar, Jonita Gandhi", "Kannukulla"));
        Map<Long, Long> m = SongMatcher.match(
                Arrays.asList(s(7, "Kannukulla - MassTamilan.dev", "Sai Abhyankkar, Jonita Gandhi - MassTamilan.dev", "X")),
                local);
        assertEquals(500L, (long) m.get(7L));
    }

    @Test
    public void matchByTitleArtist_albumBreaksTie() {
        List<SongInfo> local = Arrays.asList(
                s(500, "Theme", "Anirudh", "Movie A"),
                s(501, "Theme", "Anirudh", "Movie B"));
        Map<Long, Long> m = SongMatcher.match(Arrays.asList(s(9, "Theme", "Anirudh", "Movie B")), local);
        assertEquals(501L, (long) m.get(9L));
    }

    @Test
    public void unmatchedGetsSyntheticId() {
        Map<Long, Long> m = SongMatcher.match(Arrays.asList(s(9, "Not Here", "Nobody", "None")),
                Arrays.asList(s(500, "Other", "Someone", "A")));
        long id = m.get(9L);
        assertTrue(id < -1);
        assertEquals(SongMatcher.syntheticId(SongMatcher.key("Not Here", "Nobody")), id);
    }

    @Test
    public void syntheticId_negativeAndStable() {
        long a = SongMatcher.syntheticId("x|y");
        assertTrue(a < -1);
        assertEquals(a, SongMatcher.syntheticId("x|y"));
        assertTrue(a != SongMatcher.syntheticId("x|z"));
    }
}
