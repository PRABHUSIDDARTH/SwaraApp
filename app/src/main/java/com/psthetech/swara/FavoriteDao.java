package com.psthetech.swara;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void addFavorite(FavoriteSong song);

    @Delete
    void removeFavorite(FavoriteSong song);

    @Query("SELECT * FROM favorites")
    List<FavoriteSong> getAllFavorites();

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :songId)")
    boolean isFavorite(long songId);
}