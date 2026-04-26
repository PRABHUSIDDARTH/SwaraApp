package com.psthetech.swara;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "favorites")
public class FavoriteSong {

    @PrimaryKey
    public long id;
    public String title;
    public String artist;
    public String album;
    public long duration;
    public String path;

    public FavoriteSong(long id, String title, String artist,
                        String album, long duration, String path) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
        this.path = path;
    }
}