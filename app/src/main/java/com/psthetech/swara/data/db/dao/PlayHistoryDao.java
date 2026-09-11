package com.psthetech.swara.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.psthetech.swara.data.db.entity.PlayHistory;

import java.util.List;

@Dao
public interface PlayHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void recordPlay(PlayHistory history);

    /** Most recent plays, capped at 50 for UI */
    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT 50")
    LiveData<List<PlayHistory>> getRecentlyPlayedLive();

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT 50")
    List<PlayHistory> getRecentlyPlayed();

    /**
     * Check if this song was recorded within the last 30 seconds (debounce).
     * Returns count > 0 if a recent entry exists.
     */
    @Query("SELECT COUNT(*) FROM play_history WHERE songId = :songId AND playedAt > :cutoffMs")
    int countRecentEntries(long songId, long cutoffMs);

    /**
     * Trim history to the most recent 200 entries.
     * Called after each insert to prevent unbounded growth.
     */
    @Query("DELETE FROM play_history WHERE id NOT IN " +
           "(SELECT id FROM play_history ORDER BY playedAt DESC LIMIT 200)")
    void trimHistory();

    @Query("DELETE FROM play_history WHERE songId = :songId")
    void deleteHistoryForSong(long songId);

    @Query("DELETE FROM play_history")
    void clearHistory();

    @Query("SELECT COUNT(*) FROM play_history")
    int getHistoryCount();
}
