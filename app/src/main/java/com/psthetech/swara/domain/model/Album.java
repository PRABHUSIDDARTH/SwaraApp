package com.psthetech.swara.domain.model;

import java.util.List;

/**
 * Domain model representing a music album.
 * Constructed by grouping Song objects from MediaStore.
 */
public class Album {
    private final long id;
    private final String title;
    private final String artist;
    private final int songCount;
    private final int year;
    private final List<Song> songs;

    public Album(long id, String title, String artist, int songCount, int year, List<Song> songs) {
        this.id = id;
        this.title = title != null ? title : "Unknown Album";
        this.artist = artist != null ? artist : "Unknown Artist";
        this.songCount = songCount;
        this.year = year;
        this.songs = songs;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public int getSongCount() { return songCount; }
    public int getYear() { return year; }
    public List<Song> getSongs() { return songs; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Album)) return false;
        Album album = (Album) o;
        return id == album.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
}
