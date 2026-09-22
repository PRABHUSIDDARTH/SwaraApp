package com.psthetech.swara.domain.model;

import java.util.List;

/**
 * Domain model representing a music artist.
 * Constructed by grouping Song objects from MediaStore by artist name.
 */
public class Artist {
    private final String name;
    private final String canonicalKey;
    private final int songCount;
    private final int albumCount;
    private final List<Song> songs;
    /** Representative albumId for fetching artist artwork from first album */
    private final long representativeAlbumId;

    public Artist(String name, int songCount, int albumCount, long representativeAlbumId, List<Song> songs) {
        this(name, com.psthetech.swara.util.ArtistIdentityHelper.getCanonicalKey(name), songCount, albumCount, representativeAlbumId, songs);
    }

    public Artist(String name, String canonicalKey, int songCount, int albumCount, long representativeAlbumId, List<Song> songs) {
        this.name = name != null ? name : "Unknown Artist";
        this.canonicalKey = canonicalKey != null ? canonicalKey : com.psthetech.swara.util.ArtistIdentityHelper.getCanonicalKey(this.name);
        this.songCount = songCount;
        this.albumCount = albumCount;
        this.representativeAlbumId = representativeAlbumId;
        this.songs = songs;
    }

    public String getName() { return name; }
    public String getDisplayName() { return name; }
    public String getCanonicalKey() { return canonicalKey != null ? canonicalKey : getCanonicalName(); }
    public int getSongCount() { return songCount; }
    public int getAlbumCount() { return albumCount; }
    public long getRepresentativeAlbumId() { return representativeAlbumId; }
    public List<Song> getSongs() { return songs; }

    public static String getCanonicalKey(String rawName) {
        return com.psthetech.swara.util.ArtistIdentityHelper.getCanonicalKey(rawName);
    }

    public static String normalizeDisplayName(String rawName) {
        return com.psthetech.swara.util.ArtistIdentityHelper.normalizeDisplayName(rawName);
    }

    public String getCanonicalName() {
        return canonicalKey != null ? canonicalKey : getCanonicalKey(name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Artist)) return false;
        Artist artist = (Artist) o;
        return getCanonicalKey().equals(artist.getCanonicalKey());
    }

    @Override
    public int hashCode() {
        return getCanonicalKey().hashCode();
    }
}
