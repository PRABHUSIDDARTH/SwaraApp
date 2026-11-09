package com.psthetech.swara.wrapped;

import com.psthetech.swara.data.db.entity.ListenEvent;

import java.util.List;
import java.util.TimeZone;
import java.util.function.LongSupplier;

/**
 * Records real listening into the Wrapped aggregates: playing time per song, day and hour,
 * plus plays / finishes / skips / repeats / Vibe starts. Time is attributed to the item that
 * was current while it accumulated. Main thread only; the sink does the (async) writes.
 */
public final class ListenTracker {

    /** Where aggregates are written. The Android implementation writes Room off the main thread. */
    public interface Sink {
        void addDaily(int day, long songId, long playedMs, int plays, int finishes, int skips,
                      int repeats, int vibeStarts);
        void addHourly(int day, int hour, long playedMs);
        /** Inserts or refreshes the song's snapshot; firstHeardDay is kept if already known. */
        void snapshot(long songId, int day);
    }

    private final ListenClock clock;
    private final Sink sink;
    private final LongSupplier wallMs;
    private final TimeZone tz;
    private long currentSongId = -1;

    public ListenTracker(ListenClock clock, Sink sink, LongSupplier wallMs, TimeZone tz) {
        this.clock = clock;
        this.sink = sink;
        this.wallMs = wallMs;
        this.tz = tz;
    }

    private int today() {
        return Day.of(wallMs.getAsLong(), tz);
    }

    /** The item now playing changed (any reason). Time so far belongs to the previous item. */
    public void onCurrentItemChanged(long songId) {
        flushTo(currentSongId);
        currentSongId = songId;
    }

    /** The item actually started playing: one play, and its snapshot. */
    public void onItemStarted(long songId) {
        if (songId <= 0) return;
        if (songId != currentSongId) onCurrentItemChanged(songId);
        int day = today();
        sink.snapshot(songId, day);
        sink.addDaily(day, songId, 0, 1, 0, 0, 0, 0);
    }

    public void onIsPlayingChanged(boolean playing) {
        clock.setPlaying(playing);
        if (!playing) flushTo(currentSongId);
    }

    /** The user left an item; {@code eventType} is an {@link ListenEvent} TYPE_ constant. */
    public void onLeaveItem(long songId, int eventType) {
        if (songId <= 0) return;
        flushTo(songId);
        int finishes = eventType == ListenEvent.TYPE_FINISH ? 1 : 0;
        int skips = eventType == ListenEvent.TYPE_SKIP ? 1 : 0;
        int repeats = eventType == ListenEvent.TYPE_REPEAT ? 1 : 0;
        if (finishes + skips + repeats > 0) {
            sink.addDaily(today(), songId, 0, 0, finishes, skips, repeats, 0);
        }
    }

    /** Periodic save while playing, and on shutdown. */
    public void checkpoint() {
        flushTo(currentSongId);
    }

    public void onVibeStart(long songId) {
        if (songId <= 0) return;
        sink.addDaily(today(), songId, 0, 0, 0, 0, 0, 1);
    }

    private void flushTo(long songId) {
        List<ListenClock.Slice> slices = clock.drain();
        if (songId <= 0) return; // time with nothing identifiable playing is dropped
        for (ListenClock.Slice s : slices) {
            if (s.ms <= 0) continue;
            sink.addDaily(s.day, songId, s.ms, 0, 0, 0, 0, 0);
            sink.addHourly(s.day, s.hour, s.ms);
        }
    }
}
