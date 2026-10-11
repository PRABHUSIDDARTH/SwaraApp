package com.psthetech.swara.domain.model;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Domain model representing comprehensive, editable song metadata.
 */
public class SongMetadata {

    private final long songId;
    private final String title;
    private final String artist;
    private final String album;
    private final String albumArtist;
    private final String genre;
    private final int year;
    private final int trackNumber;
    private final int discNumber;
    private final String composer;
    private final String comment;

    private final long albumId;
    private final long duration;
    private final long dateAdded;
    private final String mimeType;
    private final String filePath;
    private final Uri contentUri;

    public SongMetadata(long songId,
                        @Nullable String title,
                        @Nullable String artist,
                        @Nullable String album,
                        @Nullable String albumArtist,
                        @Nullable String genre,
                        int year,
                        int trackNumber,
                        int discNumber,
                        @Nullable String composer,
                        @Nullable String comment,
                        long albumId,
                        long duration,
                        long dateAdded,
                        @Nullable String mimeType,
                        @Nullable String filePath,
                        @Nullable Uri contentUri) {
        this.songId = songId;
        this.title = title != null ? title : "";
        this.artist = artist != null ? artist : "";
        this.album = album != null ? album : "";
        this.albumArtist = albumArtist != null ? albumArtist : "";
        this.genre = genre != null ? genre : "";
        this.year = year;
        this.trackNumber = trackNumber;
        this.discNumber = discNumber;
        this.composer = composer != null ? composer : "";
        this.comment = comment != null ? comment : "";
        this.albumId = albumId;
        this.duration = duration;
        this.dateAdded = dateAdded;
        this.mimeType = mimeType != null ? mimeType : "";
        this.filePath = filePath != null ? filePath : "";
        this.contentUri = contentUri;
    }

    public long getSongId() { return songId; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getAlbum() { return album; }
    public String getAlbumArtist() { return albumArtist; }
    public String getGenre() { return genre; }
    public int getYear() { return year; }
    public int getTrackNumber() { return trackNumber; }
    public int getDiscNumber() { return discNumber; }
    public String getComposer() { return composer; }
    public String getComment() { return comment; }
    public long getAlbumId() { return albumId; }
    public long getDuration() { return duration; }
    public long getDateAdded() { return dateAdded; }
    public String getMimeType() { return mimeType; }
    public String getFilePath() { return filePath; }
    public Uri getContentUri() { return contentUri; }

    /**
     * Converts to standard Song model.
     */
    public Song toSong() {
        return new Song(songId, title, artist, album, albumId, duration, trackNumber, year, dateAdded);
    }

    /**
     * Checks whether editable metadata has changed compared to another instance.
     */
    public boolean hasChanges(@NonNull SongMetadata other) {
        return !Objects.equals(title, other.title)
                || !Objects.equals(artist, other.artist)
                || !Objects.equals(album, other.album)
                || !Objects.equals(albumArtist, other.albumArtist)
                || !Objects.equals(genre, other.genre)
                || year != other.year
                || trackNumber != other.trackNumber
                || discNumber != other.discNumber
                || !Objects.equals(composer, other.composer)
                || !Objects.equals(comment, other.comment);
    }

    public static class Builder {
        private long songId;
        private String title = "";
        private String artist = "";
        private String album = "";
        private String albumArtist = "";
        private String genre = "";
        private int year = 0;
        private int trackNumber = 0;
        private int discNumber = 0;
        private String composer = "";
        private String comment = "";
        private long albumId = 0;
        private long duration = 0;
        private long dateAdded = 0;
        private String mimeType = "";
        private String filePath = "";
        private Uri contentUri;

        public Builder() {}

        public Builder(SongMetadata src) {
            this.songId = src.songId;
            this.title = src.title;
            this.artist = src.artist;
            this.album = src.album;
            this.albumArtist = src.albumArtist;
            this.genre = src.genre;
            this.year = src.year;
            this.trackNumber = src.trackNumber;
            this.discNumber = src.discNumber;
            this.composer = src.composer;
            this.comment = src.comment;
            this.albumId = src.albumId;
            this.duration = src.duration;
            this.dateAdded = src.dateAdded;
            this.mimeType = src.mimeType;
            this.filePath = src.filePath;
            this.contentUri = src.contentUri;
        }

        public Builder setSongId(long songId) { this.songId = songId; return this; }
        public Builder setTitle(String title) { this.title = title; return this; }
        public Builder setArtist(String artist) { this.artist = artist; return this; }
        public Builder setAlbum(String album) { this.album = album; return this; }
        public Builder setAlbumArtist(String albumArtist) { this.albumArtist = albumArtist; return this; }
        public Builder setGenre(String genre) { this.genre = genre; return this; }
        public Builder setYear(int year) { this.year = year; return this; }
        public Builder setTrackNumber(int trackNumber) { this.trackNumber = trackNumber; return this; }
        public Builder setDiscNumber(int discNumber) { this.discNumber = discNumber; return this; }
        public Builder setComposer(String composer) { this.composer = composer; return this; }
        public Builder setComment(String comment) { this.comment = comment; return this; }
        public Builder setAlbumId(long albumId) { this.albumId = albumId; return this; }
        public Builder setDuration(long duration) { this.duration = duration; return this; }
        public Builder setDateAdded(long dateAdded) { this.dateAdded = dateAdded; return this; }
        public Builder setMimeType(String mimeType) { this.mimeType = mimeType; return this; }
        public Builder setFilePath(String filePath) { this.filePath = filePath; return this; }
        public Builder setContentUri(Uri contentUri) { this.contentUri = contentUri; return this; }

        public SongMetadata build() {
            return new SongMetadata(songId, title, artist, album, albumArtist, genre,
                    year, trackNumber, discNumber, composer, comment, albumId,
                    duration, dateAdded, mimeType, filePath, contentUri);
        }
    }
}
