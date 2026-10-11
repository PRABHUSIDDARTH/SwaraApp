package com.psthetech.swara.domain.model;

/**
 * Core domain model representing a single audio track.
 * V2 adds albumId (for artwork URI), genreId, trackNumber, year, dateAdded.
 * Does NOT use the deprecated MediaStore.Audio.Media.DATA — uses content URI via id.
 */
public class Song {
    private final long id;
    private final String title;
    private final String artist;
    private final String album;
    private final long albumId;
    private final long duration;
    private final int trackNumber;
    private final int year;
    private final long dateAdded; // seconds since epoch (MediaStore format)

    public Song(long id, String title, String artist, String album,
                long albumId, long duration, int trackNumber, int year, long dateAdded) {
        this.id = id;
        this.title = title != null ? title : "Unknown Song";
        this.artist = artist != null ? artist : "Unknown Artist";
        this.album = album != null ? album : "Unknown Album";
        this.albumId = albumId;
        this.duration = duration;
        this.trackNumber = trackNumber;
        this.year = year;
        this.dateAdded = dateAdded;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getAlbum() { return album; }
    public long getAlbumId() { return albumId; }
    public long getDuration() { return duration; }
    public int getTrackNumber() { return trackNumber; }
    public int getYear() { return year; }
    public long getDateAdded() { return dateAdded; }

    /** Formatted duration string: M:SS */
    public String getFormattedDuration() {
        long minutes = (duration / 1000) / 60;
        long seconds = (duration / 1000) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Song)) return false;
        Song song = (Song) o;
        return id == song.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "Song{id=" + id + ", title='" + title + "', artist='" + artist + "'}";
    }
}
