package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.viewmodel.CustomSongOrder;
import com.psthetech.swara.ui.viewmodel.SortOrder;
import com.psthetech.swara.ui.widget.DiscSpinPhysics;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class DiscSpinAndCustomOrderTest {

    private static Song song(long id, long dateAdded) {
        return new Song(id, "T" + id, "A", "Al", 1L, 1000L, 0, 0, dateAdded);
    }

    // ── Disc spin ──

    @Test
    public void wrapKeepsAnglesInRange() {
        assertEquals(10f, DiscSpinPhysics.wrap(370f), 0.001f);
        assertEquals(350f, DiscSpinPhysics.wrap(-10f), 0.001f);
        assertEquals(0f, DiscSpinPhysics.wrap(720f), 0.001f);
    }

    @Test
    public void approachConvergesWithoutOvershoot() {
        float v = 1.0f;
        for (int i = 0; i < 200; i++) v = DiscSpinPhysics.approach(v, 0.036f, 16f, 420f);
        assertEquals(0.036f, v, 0.001f);
        float one = DiscSpinPhysics.approach(1f, 0f, 16f, 420f);
        assertTrue(one < 1f && one > 0f);
    }

    @Test
    public void approachIsFrameRateIndependent() {
        float a = 1f;
        for (int i = 0; i < 4; i++) a = DiscSpinPhysics.approach(a, 0f, 8f, 300f);
        float b = DiscSpinPhysics.approach(1f, 0f, 32f, 300f);
        assertEquals(b, a, 0.0001f);
    }

    @Test
    public void velocityFollowsDragDirection() {
        float cw = 0f;
        for (int i = 0; i < 10; i++) cw = DiscSpinPhysics.trackVelocity(cw, 12f, 16L);
        assertTrue("clockwise drag spins clockwise", cw > 0.5f);
        float ccw = cw;
        for (int i = 0; i < 10; i++) ccw = DiscSpinPhysics.trackVelocity(ccw, -12f, 16L);
        assertTrue("reversing the drag reverses the spin", ccw < -0.5f);
        assertEquals(ccw, DiscSpinPhysics.trackVelocity(ccw, 5f, 0L), 0f);
    }

    // ── Custom (drag-arranged) order ──

    @Test
    public void customOrderRoundTripsAndOrdersSongs() {
        List<Song> songs = Arrays.asList(song(1, 10), song(2, 20), song(3, 30));
        List<Long> saved = CustomSongOrder.decode(CustomSongOrder.encode(Arrays.asList(songs.get(2), songs.get(0), songs.get(1))));
        List<Song> ordered = CustomSongOrder.apply(songs, saved);
        assertEquals(3L, ordered.get(0).getId());
        assertEquals(1L, ordered.get(1).getId());
        assertEquals(2L, ordered.get(2).getId());
    }

    @Test
    public void newSongsAppendAfterArrangedOnesNewestFirst() {
        List<Song> songs = Arrays.asList(song(1, 10), song(2, 20), song(4, 40), song(5, 50));
        List<Song> ordered = CustomSongOrder.apply(songs, CustomSongOrder.decode("2,1,99"));
        assertEquals(2L, ordered.get(0).getId());
        assertEquals(1L, ordered.get(1).getId());
        assertEquals(5L, ordered.get(2).getId());
        assertEquals(4L, ordered.get(3).getId());
    }

    @Test
    public void corruptEntriesAreSkipped() {
        assertEquals(Arrays.asList(1L, 3L), CustomSongOrder.decode("1,x,3"));
        assertTrue(CustomSongOrder.decode(null).isEmpty());
    }

    @Test
    public void customSortOrderPersistsByName() {
        assertEquals(SortOrder.CUSTOM, SortOrder.fromKey("CUSTOM"));
    }
}
