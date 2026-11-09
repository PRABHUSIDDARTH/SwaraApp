package com.psthetech.swara.wrapped;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.TimeZone;

public class ListenClockTest {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    // 2026-10-17T10:00:00Z
    private static final long T0 = 1792231200000L;

    private long elapsed = 1_000_000L;
    private long wall = T0;
    private final ListenClock clock = new ListenClock(() -> elapsed, () -> wall, UTC);

    private void advance(long ms) { elapsed += ms; wall += ms; }

    private static long total(List<ListenClock.Slice> s) {
        long t = 0;
        for (ListenClock.Slice x : s) t += x.ms;
        return t;
    }

    @Test
    public void pausedTimeNotCounted() {
        advance(10_000);
        assertEquals(0, total(clock.drain()));
        clock.setPlaying(true);
        advance(5_000);
        clock.setPlaying(false);
        advance(60_000);
        assertEquals(5_000, total(clock.drain()));
    }

    @Test
    public void resumeAccumulates() {
        clock.setPlaying(true);
        advance(4_000);
        clock.setPlaying(false);
        advance(1_000);
        clock.setPlaying(true);
        advance(6_000);
        List<ListenClock.Slice> s = clock.drain();
        assertEquals(10_000, total(s));
        assertEquals(1, s.size());
        assertEquals(20261017, s.get(0).day);
        assertEquals(10, s.get(0).hour);
    }

    @Test
    public void drainWhilePlaying_keepsCounting() {
        clock.setPlaying(true);
        advance(30_000);
        assertEquals(30_000, total(clock.drain()));
        advance(20_000);
        assertEquals(20_000, total(clock.drain()));
    }

    @Test
    public void drainClears() {
        clock.setPlaying(true);
        advance(3_000);
        clock.setPlaying(false);
        clock.drain();
        assertEquals(0, total(clock.drain()));
    }

    @Test
    public void crossesHour_splits() {
        wall = T0 + 59 * 60_000L; // 10:59:00
        clock.setPlaying(true);
        advance(120_000);           // to 11:01:00
        List<ListenClock.Slice> s = clock.drain();
        assertEquals(2, s.size());
        assertEquals(10, s.get(0).hour);
        assertEquals(60_000, s.get(0).ms);
        assertEquals(11, s.get(1).hour);
        assertEquals(60_000, s.get(1).ms);
    }

    @Test
    public void crossesMidnight_splitsIntoTwoDays() {
        wall = 1792281570000L;      // 2026-10-17T23:59:30Z
        clock.setPlaying(true);
        advance(60_000);            // to 2026-10-18T00:00:30Z
        List<ListenClock.Slice> s = clock.drain();
        assertEquals(2, s.size());
        assertEquals(20261017, s.get(0).day);
        assertEquals(23, s.get(0).hour);
        assertEquals(30_000, s.get(0).ms);
        assertEquals(20261018, s.get(1).day);
        assertEquals(0, s.get(1).hour);
        assertEquals(30_000, s.get(1).ms);
    }

    @Test
    public void wallClockJumpBackwards_neverNegative() {
        clock.setPlaying(true);
        elapsed += 5_000;
        wall -= 3_600_000; // user set the clock back an hour mid-song
        List<ListenClock.Slice> s = clock.drain();
        assertEquals(5_000, total(s));
        for (ListenClock.Slice x : s) assertTrue(x.ms >= 0);
    }
}
