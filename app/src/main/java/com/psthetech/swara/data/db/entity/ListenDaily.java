package com.psthetech.swara.data.db.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

/** Never-trimmed listening totals for one song on one local day (Swara Wrapped). */
@Entity(tableName = "listen_daily", primaryKeys = {"day", "songId"}, indices = {@Index("songId")})
public class ListenDaily {
    /** yyyymmdd in device local time when listened. */
    public int day;
    public long songId;
    public long playedMs;
    public int plays;
    public int finishes;
    public int skips;
    public int repeats;
    @ColumnInfo(defaultValue = "0")
    public int vibeStarts;
}
