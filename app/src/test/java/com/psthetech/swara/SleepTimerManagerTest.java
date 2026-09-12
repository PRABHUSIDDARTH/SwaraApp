package com.psthetech.swara;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.util.SleepTimerManager;

import org.junit.Before;
import org.junit.Test;

public class SleepTimerManagerTest {

    private SleepTimerManager sleepTimerManager;

    @Before
    public void setUp() {
        sleepTimerManager = SleepTimerManager.getInstance();
        sleepTimerManager.cancelTimer();
    }

    @Test
    public void testSleepAfterSongActivation() {
        assertFalse(sleepTimerManager.isSleepAfterSong());
        assertFalse(sleepTimerManager.isActive());

        sleepTimerManager.sleepAfterCurrentSong(null);

        assertTrue(sleepTimerManager.isSleepAfterSong());
        assertTrue(sleepTimerManager.isActive());
    }

    @Test
    public void testCancelTimerResetsState() {
        sleepTimerManager.sleepAfterCurrentSong(null);
        assertTrue(sleepTimerManager.isActive());

        sleepTimerManager.cancelTimer();

        assertFalse(sleepTimerManager.isSleepAfterSong());
        assertFalse(sleepTimerManager.isActive());
    }
}
