package com.psthetech.swara.ui.deck;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StandbyPolicyTest {

    private static final long PLAYING = -1;

    @Test
    public void closesAfterFifteenMinutesWithoutATouch() {
        assertEquals(15 * 60_000L, StandbyPolicy.IDLE_LIMIT_MS);
        assertFalse(StandbyPolicy.shouldClose(1000 + StandbyPolicy.IDLE_LIMIT_MS - 1, 1000, PLAYING));
        assertTrue(StandbyPolicy.shouldClose(1000 + StandbyPolicy.IDLE_LIMIT_MS, 1000, PLAYING));
    }

    @Test
    public void closesAfterOneMinutePaused() {
        assertEquals(60_000L, StandbyPolicy.PAUSE_LIMIT_MS);
        assertFalse(StandbyPolicy.shouldClose(65_000 - 1, 1000, 5000));
        assertTrue(StandbyPolicy.shouldClose(65_000, 1000, 5000));
    }

    @Test
    public void aTouchDuringAPauseRestartsThePauseCountdown() {
        assertFalse(StandbyPolicy.shouldClose(70_000, 50_000, 5000));
        assertTrue(StandbyPolicy.shouldClose(110_000, 50_000, 5000));
    }

    @Test
    public void aLongGapSuchAsTheScreenBeingOffExpiresAtOnce() {
        assertTrue(StandbyPolicy.shouldClose(10_000_000L, 0, PLAYING));
    }

    @Test
    public void opensCentredAndStillReachesBothSides() {
        assertEquals(0, StandbyPolicy.shiftX(0, 12));
        assertEquals(0, StandbyPolicy.shiftY(0, 12));
        int low = 0;
        int high = 0;
        for (long minute = 0; minute < 60; minute++) {
            low = Math.min(low, StandbyPolicy.shiftX(minute, 12));
            high = Math.max(high, StandbyPolicy.shiftX(minute, 12));
        }
        assertEquals(-12, low);
        assertEquals(12, high);
    }

    @Test
    public void burnInShiftStaysSmallAndMovesEveryMinute() {
        int max = 12;
        for (long minute = 0; minute < 500; minute++) {
            int x = StandbyPolicy.shiftX(minute, max);
            int y = StandbyPolicy.shiftY(minute, max);
            assertTrue(Math.abs(x) <= max && Math.abs(y) <= max);
            assertTrue("stuck at minute " + minute,
                    x != StandbyPolicy.shiftX(minute + 1, max)
                            || y != StandbyPolicy.shiftY(minute + 1, max));
        }
        assertEquals(0, StandbyPolicy.shiftX(7, 0));
        assertEquals(0, StandbyPolicy.shiftY(7, 0));
    }
}
