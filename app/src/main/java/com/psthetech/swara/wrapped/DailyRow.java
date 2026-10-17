package com.psthetech.swara.wrapped;

/** Pure copy of a listen_daily row (engine and backfill input). */
public final class DailyRow {
    public final int day;
    public final long songId;
    public long playedMs;
    public int plays;
    public int finishes;
    public int skips;
    public int repeats;
    public int vibeStarts;

    public DailyRow(int day, long songId, long playedMs, int plays, int finishes, int skips,
                    int repeats, int vibeStarts) {
        this.day = day;
        this.songId = songId;
        this.playedMs = playedMs;
        this.plays = plays;
        this.finishes = finishes;
        this.skips = skips;
        this.repeats = repeats;
        this.vibeStarts = vibeStarts;
    }
}
