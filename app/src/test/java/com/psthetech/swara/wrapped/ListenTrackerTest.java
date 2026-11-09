package com.psthetech.swara.wrapped;

import static org.junit.Assert.assertEquals;

import com.psthetech.swara.data.db.entity.ListenEvent;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class ListenTrackerTest {
    private static final long T0 = 1792231200000L; // 2026-10-17T10:00:00Z
    private long elapsed = 0, wall = T0;

    /** Records what the tracker writes, keyed by songId. */
    private static final class FakeSink implements ListenTracker.Sink {
        final Map<Long, long[]> daily = new HashMap<>(); // playedMs, plays, finishes, skips, repeats, vibe
        final Map<Integer, Long> hourly = new HashMap<>();
        int snapshots;

        private long[] row(long id) { return daily.computeIfAbsent(id, k -> new long[6]); }

        @Override public void addDaily(int day, long songId, long playedMs, int plays, int finishes,
                                       int skips, int repeats, int vibeStarts) {
            long[] r = row(songId);
            r[0] += playedMs; r[1] += plays; r[2] += finishes; r[3] += skips; r[4] += repeats; r[5] += vibeStarts;
        }
        @Override public void addHourly(int day, int hour, long playedMs) { hourly.merge(hour, playedMs, Long::sum); }
        @Override public void snapshot(long songId, int day) { snapshots++; }
    }

    private final FakeSink sink = new FakeSink();
    private final ListenTracker tracker = new ListenTracker(
            new ListenClock(() -> elapsed, () -> wall, TimeZone.getTimeZone("UTC")),
            sink, () -> wall, TimeZone.getTimeZone("UTC"));

    private void advance(long ms) { elapsed += ms; wall += ms; }

    @Test
    public void timeGoesToTheSongThatWasPlaying() {
        tracker.onItemStarted(1L);
        tracker.onIsPlayingChanged(true);
        advance(40_000);
        tracker.onLeaveItem(1L, ListenEvent.TYPE_SKIP);
        tracker.onCurrentItemChanged(2L);
        tracker.onItemStarted(2L);
        advance(20_000);
        tracker.checkpoint();
        assertEquals(40_000, sink.daily.get(1L)[0]);
        assertEquals(1, sink.daily.get(1L)[1]); // play
        assertEquals(1, sink.daily.get(1L)[3]); // skip
        assertEquals(20_000, sink.daily.get(2L)[0]);
        assertEquals(60_000L, (long) sink.hourly.get(10));
        assertEquals(2, sink.snapshots);
    }

    @Test
    public void pausedTimeIsNotCounted() {
        tracker.onItemStarted(1L);
        tracker.onIsPlayingChanged(true);
        advance(10_000);
        tracker.onIsPlayingChanged(false);
        advance(300_000);
        tracker.checkpoint();
        assertEquals(10_000, sink.daily.get(1L)[0]);
    }

    @Test
    public void queueReplacedWithoutLeaveEvent_stillCreditsOldSong() {
        tracker.onItemStarted(1L);
        tracker.onIsPlayingChanged(true);
        advance(15_000);
        tracker.onCurrentItemChanged(9L); // e.g. user started a new playlist
        advance(5_000);
        tracker.checkpoint();
        assertEquals(15_000, sink.daily.get(1L)[0]);
        assertEquals(5_000, sink.daily.get(9L)[0]);
    }

    @Test
    public void finishAndRepeatCounted() {
        tracker.onItemStarted(1L);
        tracker.onLeaveItem(1L, ListenEvent.TYPE_FINISH);
        tracker.onLeaveItem(1L, ListenEvent.TYPE_REPEAT);
        assertEquals(1, sink.daily.get(1L)[2]);
        assertEquals(1, sink.daily.get(1L)[4]);
    }

    @Test
    public void vibeStartCounted() {
        tracker.onVibeStart(5L);
        assertEquals(1, sink.daily.get(5L)[5]);
    }

    @Test
    public void nothingWrittenForEmptyCheckpoint() {
        tracker.checkpoint();
        assertEquals(0, sink.daily.size());
        assertEquals(0, sink.hourly.size());
    }
}
