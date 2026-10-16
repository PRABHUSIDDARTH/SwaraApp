package com.psthetech.swara.data.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

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
}
