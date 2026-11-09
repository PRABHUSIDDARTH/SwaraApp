package com.psthetech.swara.data.db.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** One listening signal (play, skip, finish, repeat). Capped at the newest 5 000 rows. */
@Entity(tableName = "listen_events",
        indices = {@Index("songId"), @Index("timestamp")})
public class ListenEvent {

    public static final int TYPE_PLAY = 0;
    public static final int TYPE_SKIP = 1;
    public static final int TYPE_FINISH = 2;
    public static final int TYPE_REPEAT = 3;

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long songId;
    public int type;
    public long positionMs;
    public long timestamp;
    public int hourOfDay;

    public ListenEvent() {
    }
}
