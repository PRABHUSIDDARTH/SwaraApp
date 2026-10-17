package com.psthetech.swara.data.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.psthetech.swara.data.db.entity.ListenEvent;

import java.util.List;

@Dao
public interface ListenEventDao {

    @Insert
    void insert(ListenEvent event);

    @Query("SELECT * FROM listen_events WHERE timestamp >= :ts ORDER BY timestamp ASC")
    List<ListenEvent> since(long ts);

    @Query("SELECT * FROM listen_events ORDER BY timestamp ASC")
    List<ListenEvent> all();

    /** Keep only the newest {@code keep} rows. */
    @Query("DELETE FROM listen_events WHERE id NOT IN "
            + "(SELECT id FROM listen_events ORDER BY timestamp DESC, id DESC LIMIT :keep)")
    void trimTo(int keep);

    @Query("DELETE FROM listen_events WHERE songId = :songId")
    void deleteForSong(long songId);

    @Query("SELECT DISTINCT songId FROM listen_events")
    List<Long> distinctSongIds();

    /** Callers keep {@code songIds} at most 500 long (SQLite variable limit). */
    @Query("DELETE FROM listen_events WHERE songId IN (:songIds)")
    void deleteForSongs(List<Long> songIds);

    /** Timestamp of the most recent play event for the song, or null if none. */
    @Query("SELECT MAX(timestamp) FROM listen_events WHERE songId = :songId")
    Long lastPlayed(long songId);
}
