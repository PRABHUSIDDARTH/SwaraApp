package com.psthetech.swara.wrapped.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StoryNavigationTest {

    @Test
    public void autoAdvancesAfterSixSeconds() {
        StoryState s = new StoryState(3);
        s.tick(5_999);
        assertEquals(0, s.index());
        s.tick(1);
        assertEquals(1, s.index());
        assertEquals(0f, s.slideProgress(), 1e-6);
    }

    @Test
    public void pauseStopsTheClock() {
        StoryState s = new StoryState(3);
        s.pause();
        s.tick(60_000);
        assertEquals(0, s.index());
        s.resume();
        s.tick(6_000);
        assertEquals(1, s.index());
    }

    @Test
    public void nextAndPrev_stayInBounds() {
        StoryState s = new StoryState(2);
        s.prev();
        assertEquals(0, s.index());
        s.next();
        assertEquals(1, s.index());
        assertFalse(s.isFinished());
        s.next();
        assertTrue(s.isFinished());
        assertEquals(1, s.index());
    }

    @Test
    public void prevRestartsTheEntrance() {
        StoryState s = new StoryState(3);
        s.next();
        s.tick(3_000);
        s.prev();
        assertEquals(0, s.index());
        assertEquals(0f, s.entranceProgress(), 1e-6);
    }

    @Test
    public void entranceTakes900ms() {
        StoryState s = new StoryState(1);
        s.tick(450);
        assertEquals(0.5f, s.entranceProgress(), 1e-3);
        s.tick(450);
        assertEquals(1f, s.entranceProgress(), 1e-6);
        assertEquals(0.15f, s.slideProgress(), 1e-3);
    }

    @Test
    public void lastSlideFinishesInsteadOfWrapping() {
        StoryState s = new StoryState(1);
        s.tick(6_000);
        assertTrue(s.isFinished());
        assertEquals(0, s.index());
    }

    @Test
    public void zeroSlides_isFinishedImmediately() {
        assertTrue(new StoryState(0).isFinished());
    }
}
