package com.psthetech.swara.data.db.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Room entity for user-created playlists. */
@Entity(tableName = "playlists")
public class Playlist {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String name;

    /** Timestamp when the playlist was created (ms epoch) */
    public long createdAt;

    /** Timestamp when the playlist was last modified (ms epoch) */
    public long modifiedAt;

    public Playlist(String name, long createdAt, long modifiedAt) {
        this.name = name;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
    }
}
