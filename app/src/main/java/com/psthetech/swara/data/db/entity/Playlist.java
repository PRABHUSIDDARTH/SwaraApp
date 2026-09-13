package com.psthetech.swara.data.db.entity;

import androidx.room.ColumnInfo;
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

    /**
     * Path to the custom artwork file in internal storage, or null if using collage/default.
     * Added in schema version 3.
     */
    public String artworkPath;

    public Playlist(String name, long createdAt, long modifiedAt) {
        this.name = name;
        this.createdAt = createdAt;
        this.modifiedAt = modifiedAt;
    }
}
