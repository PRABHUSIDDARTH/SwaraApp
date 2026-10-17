package com.psthetech.swara.vibe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.data.db.entity.ListenEvent;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TasteTest {

    private static ListenEvent ev(long song, int type, int hour) {
        ListenEvent e = new ListenEvent();
        e.songId = song;
        e.type = type;
        e.hourOfDay = hour;
        return e;
    }

    private static TasteProfile profile(List<ListenEvent> events, Set<Long> favs) {
        return new TasteProfile(events, favs, new HashMap<Long, String>(), new HashMap<Long, Long>());
    }

    @Test
    public void classify_earlySkip() {
        assertEquals(ListenEvent.TYPE_SKIP, EventClassifier.classifyLeave(10_000, 200_000, false, false));
    }

    @Test
    public void classify_lateLeave_isFinish() {
        assertEquals(ListenEvent.TYPE_FINISH, EventClassifier.classifyLeave(150_000, 200_000, false, false));
    }

    @Test
    public void classify_shortSongUnder40pct_isSkip() {
        assertEquals(ListenEvent.TYPE_SKIP, EventClassifier.classifyLeave(35_000, 100_000, false, false));
    }

    @Test
    public void classify_repeat() {
        assertEquals(ListenEvent.TYPE_REPEAT, EventClassifier.classifyLeave(5_000, 200_000, true, true));
        assertEquals(ListenEvent.TYPE_FINISH, EventClassifier.classifyLeave(200_000, 200_000, true, false));
    }

    @Test
    public void favoriteWithNoPlays_scoresAboveNeutral() {
        TasteProfile p = profile(new ArrayList<ListenEvent>(), new HashSet<>(Collections.singleton(1L)));
        assertTrue(p.song(1L) > 0.5);
    }

    @Test
    public void manySkips_scoresBelowNeutral() {
        List<ListenEvent> es = new ArrayList<>();
        for (int i = 0; i < 5; i++) es.add(ev(2, ListenEvent.TYPE_SKIP, 10));
        assertTrue(profile(es, new HashSet<Long>()).song(2L) < 0.5);
    }

    @Test
    public void unknownSong_isNeutralHalf() {
        TasteProfile p = profile(new ArrayList<ListenEvent>(), new HashSet<Long>());
        assertEquals(0.5, p.song(99L), 0.0);
        assertEquals(0.5, p.artist("x"), 0.0);
        assertEquals(0.5, p.album(7L), 0.0);
        assertEquals(0.0, p.hour(99L, 5), 0.0);
    }

    @Test
    public void hourAffinity_prefersMatchingHour() {
        List<ListenEvent> es = new ArrayList<>();
        for (int i = 0; i < 3; i++) es.add(ev(1, ListenEvent.TYPE_PLAY, 23));
        es.add(ev(1, ListenEvent.TYPE_PLAY, 12));
        TasteProfile p = profile(es, new HashSet<Long>());
        assertEquals(0.75, p.hour(1L, 0), 1e-9);   // 23 is adjacent to 0 (circular)
        assertEquals(0.25, p.hour(1L, 12), 1e-9);
        assertTrue(p.hour(1L, 23) > p.hour(1L, 6));
    }

    @Test
    public void combined_blendsArtistAndAlbum() {
        List<ListenEvent> es = new ArrayList<>();
        es.add(ev(1, ListenEvent.TYPE_FINISH, 1));
        es.add(ev(2, ListenEvent.TYPE_SKIP, 1));
        Map<Long, String> artists = new HashMap<>();
        artists.put(1L, "a");
        artists.put(2L, "a");
        Map<Long, Long> albums = new HashMap<>();
        albums.put(1L, 5L);
        TasteProfile p = new TasteProfile(es, new HashSet<Long>(), artists, albums);
        double s1 = p.song(1L);
        assertEquals((s1 + p.song(2L)) / 2, p.artist("a"), 1e-9);
        assertEquals(s1, p.album(5L), 1e-9);
        assertEquals(0.6 * s1 + 0.25 * p.artist("a") + 0.15 * s1, p.combined(1L), 1e-9);
        // song 3 unknown everywhere
        assertEquals(0.5, p.combined(3L), 1e-9);
    }
}
