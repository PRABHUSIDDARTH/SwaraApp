package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.data.repository.MusicRepository;
import com.psthetech.swara.util.SleepTimerManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AuditFixesTest {

    private final SleepTimerManager timer = SleepTimerManager.getInstance();
    private final boolean[] pauseAtEnd = new boolean[1];
    private final int[] pauseNowCalls = new int[1];

    @Before
    public void setUp() {
        timer.cancelTimer();
        timer.setPlaybackHooks(new SleepTimerManager.PlaybackHooks() {
            @Override public void pauseNow() { pauseNowCalls[0]++; }
            @Override public void setPauseAtEndOfCurrentItem(boolean pause) { pauseAtEnd[0] = pause; }
        });
    }

    @After
    public void tearDown() {
        timer.cancelTimer();
        timer.setPlaybackHooks(null);
    }

    // ----- LIKE escaping (search) -----

    @Test
    public void likeWildcardsAreEscaped() {
        assertEquals("100\\%", MusicRepository.escapeLike("100%"));
        assertEquals("a\\_b", MusicRepository.escapeLike("a_b"));
        assertEquals("c\\\\d", MusicRepository.escapeLike("c\\d"));
        assertEquals("plain", MusicRepository.escapeLike("plain"));
    }

    // ----- Sleep timer drives the player through service hooks -----

    @Test
    public void endOfSongArmsPauseAtEndOfItem() {
        timer.sleepAfterCurrentSong(null);
        assertTrue(pauseAtEnd[0]);
    }

    @Test
    public void cancellingEndOfSongDisarmsPauseAtEnd() {
        timer.sleepAfterCurrentSong(null);
        timer.cancelTimer();
        assertFalse(pauseAtEnd[0]);
        assertFalse(timer.isActive());
    }

    @Test
    public void currentItemEndedClearsEndOfSongTimer() {
        boolean[] notified = new boolean[1];
        timer.sleepAfterCurrentSong(() -> notified[0] = true);
        timer.onCurrentItemEnded();
        assertTrue(notified[0]);
        assertFalse(timer.isActive());
        assertFalse(pauseAtEnd[0]);
    }

    @Test
    public void currentItemEndedIsIgnoredWithoutEndOfSongTimer() {
        boolean[] notified = new boolean[1];
        timer.onCurrentItemEnded();
        assertFalse(notified[0]);
        assertEquals(0, pauseNowCalls[0]);
    }

    @Test
    public void hooksRegisteredLateStillArmPauseAtEnd() {
        timer.setPlaybackHooks(null);
        timer.sleepAfterCurrentSong(null);
        pauseAtEnd[0] = false;
        timer.setPlaybackHooks(new SleepTimerManager.PlaybackHooks() {
            @Override public void pauseNow() { }
            @Override public void setPauseAtEndOfCurrentItem(boolean pause) { pauseAtEnd[0] = pause; }
        });
        assertTrue(pauseAtEnd[0]);
    }
}
