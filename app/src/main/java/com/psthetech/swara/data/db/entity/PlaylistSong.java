package com.psthetech.swara.data.db.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

/**
 * Join table linking playlists to songs with explicit ordering.
 * Each row represents a song in a playlist at a given position.
 */
@Entity(
    tableName = "playlist_songs",
    primaryKeys = {"playlistId", "songId"},
    foreignKeys = @ForeignKey(
        entity = Playlist.class,
        parentColumns = "id",
        childColumns = "playlistId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("playlistId")}
)
public class PlaylistSong {
    public long playlistId;
    public long songId;

    /** Explicit ordering within the playlist (0-based) */
    public int position;

    /** Snapshot metadata (so the playlist is informative even if file is missing) */
    public String title;
    public String artist;
    public String album;
    public long albumId;
    public long duration;

    public PlaylistSong(long playlistId, long songId, int position,
                        String title, String artist, String album, long albumId, long duration) {
        this.playlistId = playlistId;
        this.songId = songId;
        this.position = position;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.albumId = albumId;
        this.duration = duration;
    }
}
