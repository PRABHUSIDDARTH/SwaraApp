package com.psthetech.swara.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DeckWidgetModelTest {

    @Test
    public void spinsOnlyWithASongPlayingOnAndroid12OrNewer() {
        assertTrue(DeckWidgetModel.spins(true, true, 31));
        assertFalse(DeckWidgetModel.spins(false, true, 35));
        assertFalse(DeckWidgetModel.spins(true, false, 35));
        assertFalse(DeckWidgetModel.spins(true, true, 30));
    }

    @Test
    public void progressIsClampedAndEmptyWithoutADuration() {
        assertEquals(0, DeckWidgetModel.progress(5000, 0));
        assertEquals(0, DeckWidgetModel.progress(5000, -1));
        assertEquals(0, DeckWidgetModel.progress(-5, 1000));
        assertEquals(500, DeckWidgetModel.progress(500, 1000));
        assertEquals(DeckWidgetModel.PROGRESS_MAX, DeckWidgetModel.progress(5000, 1000));
        assertEquals(500, DeckWidgetModel.progress(Long.MAX_VALUE / 4, Long.MAX_VALUE / 2));
    }

    private static String signature(long song, boolean playing, boolean favorite, boolean korokae,
                                    long duration, int art, int palette) {
        return DeckWidgetModel.signature(song, playing, favorite, korokae, duration, art, palette);
    }

    @Test
    public void onlyTheProgressMovingNeedsNoFullRedraw() {
        // Position is deliberately not part of the signature.
        assertEquals(signature(1, true, false, false, 1000, 2, 3),
                signature(1, true, false, false, 1000, 2, 3));
    }

    @Test
    public void anythingElseChangingNeedsAFullRedraw() {
        String base = signature(1, true, false, false, 1000, 2, 3);
        assertNotEquals(base, signature(9, true, false, false, 1000, 2, 3));
        assertNotEquals(base, signature(1, false, false, false, 1000, 2, 3));
        assertNotEquals(base, signature(1, true, true, false, 1000, 2, 3));
        assertNotEquals(base, signature(1, true, false, true, 1000, 2, 3));
        assertNotEquals(base, signature(1, true, false, false, 2000, 2, 3));
        assertNotEquals(base, signature(1, true, false, false, 1000, 8, 3));
        assertNotEquals(base, signature(1, true, false, false, 1000, 2, 8));
    }
}
