package com.psthetech.swara.domain.model;

import java.util.List;

/**
 * Domain model representing a music artist.
 * Constructed by grouping Song objects from MediaStore by artist name.
 */
public class Artist {
    private final String name;
    private final int songCount;
    private final int albumCount;
    private final List<Song> songs;
    /** Representative albumId for fetching artist artwork from first album */
    private final long representativeAlbumId;

    public Artist(String name, int songCount, int albumCount, long representativeAlbumId, List<Song> songs) {
        this.name = name != null ? name : "Unknown Artist";
        this.songCount = songCount;
        this.albumCount = albumCount;
        this.representativeAlbumId = representativeAlbumId;
        this.songs = songs;
    }

    public String getName() { return name; }
    public int getSongCount() { return songCount; }
    public int getAlbumCount() { return albumCount; }
    public long getRepresentativeAlbumId() { return representativeAlbumId; }
    public List<Song> getSongs() { return songs; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Artist)) return false;
        Artist artist = (Artist) o;
        return name.equals(artist.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
