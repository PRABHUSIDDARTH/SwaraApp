package com.psthetech.swara.wrapped;

/** Pure copy of a listen_hourly row. */
public final class HourlyRow {
    public final int day;
    public final int hour;
    public final long playedMs;

    public HourlyRow(int day, int hour, long playedMs) {
        this.day = day;
        this.hour = hour;
        this.playedMs = playedMs;
    }
}
