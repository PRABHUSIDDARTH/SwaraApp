package com.psthetech.swara.wrapped;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.function.LongSupplier;

/**
 * Counts time actually spent playing and splits it by local day and hour.
 * Duration comes from a monotonic clock; the wall clock only places it on the calendar, so
 * clock changes never produce negative or inflated time. Not thread-safe (main thread only).
 */
public final class ListenClock {

    /** Played time within one local hour. */
    public static final class Slice {
        public final int day;
        public final int hour;
        public final long ms;

        Slice(int day, int hour, long ms) {
            this.day = day;
            this.hour = hour;
            this.ms = ms;
        }
    }

    private final LongSupplier elapsedMs;
    private final LongSupplier wallMs;
    private final TimeZone tz;

    private boolean playing;
    private long segStartElapsed;
    private long segStartWall;
    /** key = day * 100 + hour, kept in time order. */
    private final Map<Long, Long> pending = new TreeMap<>();

    public ListenClock(LongSupplier elapsedMs, LongSupplier wallMs, TimeZone tz) {
        this.elapsedMs = elapsedMs;
        this.wallMs = wallMs;
        this.tz = tz;
    }

    public void setPlaying(boolean nowPlaying) {
        if (nowPlaying == playing) return;
        if (nowPlaying) {
            segStartElapsed = elapsedMs.getAsLong();
            segStartWall = wallMs.getAsLong();
        } else {
            closeSegment();
        }
        playing = nowPlaying;
    }

    public boolean isPlaying() {
        return playing;
    }

    /** Returns the time accumulated since the last drain, split per local hour, and clears it. */
    public List<Slice> drain() {
        if (playing) {
            closeSegment();
            segStartElapsed = elapsedMs.getAsLong();
            segStartWall = wallMs.getAsLong();
        }
        List<Slice> out = new ArrayList<>(pending.size());
        for (Map.Entry<Long, Long> e : pending.entrySet()) {
            long key = e.getKey();
            out.add(new Slice((int) (key / 100), (int) (key % 100), e.getValue()));
        }
        pending.clear();
        return out;
    }

    private void closeSegment() {
        long dur = elapsedMs.getAsLong() - segStartElapsed;
        if (dur <= 0) return;
        Calendar c = Calendar.getInstance(tz);
        long t = segStartWall;
        long remaining = dur;
        while (remaining > 0) {
            c.setTimeInMillis(t);
            int day = Day.of(c);
            int hour = c.get(Calendar.HOUR_OF_DAY);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            c.add(Calendar.HOUR_OF_DAY, 1);
            long toBoundary = c.getTimeInMillis() - t;
            long part = Math.min(remaining, Math.max(1, toBoundary));
            pending.merge((long) day * 100 + hour, part, Long::sum);
            remaining -= part;
            t += part;
        }
    }
}
