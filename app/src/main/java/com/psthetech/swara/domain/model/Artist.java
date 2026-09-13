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

    public static String getCanonicalKey(String rawName) {
        if (rawName == null) return "unknown artist";
        String trimmed = rawName.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("<unknown>")) {
            return "unknown artist";
        }
        return trimmed.toLowerCase(java.util.Locale.ROOT);
    }

    public static String normalizeDisplayName(String rawName) {
        if (rawName == null) return "Unknown Artist";
        String trimmed = rawName.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("<unknown>")) {
            return "Unknown Artist";
        }
        return trimmed;
    }

    public String getCanonicalName() {
        return getCanonicalKey(name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Artist)) return false;
        Artist artist = (Artist) o;
        return getCanonicalKey(name).equals(getCanonicalKey(artist.name));
    }

    @Override
    public int hashCode() {
        return getCanonicalKey(name).hashCode();
    }
}
