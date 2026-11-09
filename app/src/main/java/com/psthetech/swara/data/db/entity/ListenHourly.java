package com.psthetech.swara.data.db.entity;

import androidx.room.Entity;

/** Never-trimmed playing time per local day and hour (0–23). */
@Entity(tableName = "listen_hourly", primaryKeys = {"day", "hour"})
public class ListenHourly {
    public int day;
    public int hour;
    public long playedMs;
}
