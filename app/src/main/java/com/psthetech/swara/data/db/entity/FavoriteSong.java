package com.psthetech.swara.data.db.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity for favorited songs. Stores a snapshot of song metadata
 * so the favorites list is usable even if the file is temporarily unavailable.
 */
@Entity(tableName = "favorites")
public class FavoriteSong {

    @PrimaryKey
    public long id;
    public String title;
    public String artist;
    public String album;
    public long albumId;
    public long duration;
    /** Timestamp when the song was favorited (ms epoch) */
    public long favoritedAt;

    public FavoriteSong(long id, String title, String artist,
                        String album, long albumId, long duration, long favoritedAt) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.albumId = albumId;
        this.duration = duration;
        this.favoritedAt = favoritedAt;
    }
}
