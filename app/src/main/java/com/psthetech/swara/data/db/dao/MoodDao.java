package com.psthetech.swara.data.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.psthetech.swara.data.db.entity.SongMood;

import java.util.List;

@Dao
public interface MoodDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SongMood mood);

    @Query("SELECT * FROM song_mood")
    List<SongMood> getAll();

    @Query("SELECT * FROM song_mood WHERE songId = :songId")
    SongMood get(long songId);

    @Query("DELETE FROM song_mood WHERE songId = :songId")
    void deleteForSong(long songId);

    @Query("UPDATE song_mood SET valence = :valence, energy = :energy WHERE songId = :songId")
    void updateNormalized(long songId, double valence, double energy);

    /** Writes all normalized values in one transaction. */
    @Transaction
    default void updateNormalizedBatch(long[] songIds, double[] valence, double[] energy) {
        for (int i = 0; i < songIds.length; i++) {
            updateNormalized(songIds[i], valence[i], energy[i]);
        }
    }
}
