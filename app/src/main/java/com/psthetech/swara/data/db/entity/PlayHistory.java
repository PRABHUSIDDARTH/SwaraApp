package com.psthetech.swara.data.db.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity recording meaningful playback history.
 *
 * A song is recorded only after crossing the playback threshold:
 *   min(30_000ms, duration * 0.4)
 * and with a 30-second debounce to prevent duplicate rapid entries.
 *
 * Table is capped at 200 entries; oldest are deleted on overflow.
 */
@Entity(tableName = "play_history")
public class PlayHistory {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long songId;
    public String title;
    public String artist;
    public String album;
    public long albumId;
    public long duration;

    /** When the meaningful playback threshold was crossed (ms epoch) */
    public long playedAt;

    public PlayHistory(long songId, String title, String artist, String album,
                       long albumId, long duration, long playedAt) {
        this.songId = songId;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.albumId = albumId;
        this.duration = duration;
        this.playedAt = playedAt;
    }
}
