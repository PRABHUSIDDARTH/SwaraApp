package com.psthetech.swara.data.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.psthetech.swara.data.db.entity.MoodOverride;

import java.util.List;

@Dao
public interface MoodOverrideDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(MoodOverride override);

    @Query("SELECT * FROM mood_override")
    List<MoodOverride> getAll();

    @Query("SELECT * FROM mood_override WHERE songId = :songId")
    MoodOverride get(long songId);

    @Query("DELETE FROM mood_override WHERE songId = :songId")
    void deleteForSong(long songId);

    /** Callers keep {@code songIds} at most 500 long (SQLite variable limit). */
    @Query("DELETE FROM mood_override WHERE songId IN (:songIds)")
    void deleteForSongs(List<Long> songIds);
}
