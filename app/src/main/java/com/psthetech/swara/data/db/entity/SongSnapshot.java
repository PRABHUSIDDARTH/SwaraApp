package com.psthetech.swara.data.db.entity;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Last known tags of a song, so Wrapped can show songs that were deleted since. */
@Entity(tableName = "song_snapshot")
public class SongSnapshot {
    @PrimaryKey
    public long songId;
    @Nullable public String title;
    @Nullable public String artist;
    @Nullable public String artistKey;
    @Nullable public String album;
    public long albumId;
    /** yyyymmdd of the first recorded listen. */
    public int firstHeardDay;
}
