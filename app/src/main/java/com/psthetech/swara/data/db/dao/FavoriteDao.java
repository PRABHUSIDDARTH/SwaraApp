package com.psthetech.swara.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.psthetech.swara.data.db.entity.FavoriteSong;

import java.util.List;

@Dao
public interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void addFavorite(FavoriteSong song);

    @Delete
    void removeFavorite(FavoriteSong song);

    @Query("DELETE FROM favorites WHERE id = :songId")
    void removeFavoriteById(long songId);

    /** LiveData for reactive UI — updates automatically when favorites change */
    @Query("SELECT * FROM favorites ORDER BY favoritedAt DESC")
    LiveData<List<FavoriteSong>> getAllFavoritesLive();

    /** Blocking query for background thread use */
    @Query("SELECT * FROM favorites ORDER BY favoritedAt DESC")
    List<FavoriteSong> getAllFavorites();

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :songId)")
    boolean isFavorite(long songId);

    /** LiveData boolean for reactive favorite button state */
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :songId)")
    LiveData<Boolean> isFavoriteLive(long songId);

    @Query("SELECT COUNT(*) FROM favorites")
    int getFavoriteCount();

    @Query("UPDATE favorites SET title = :title, artist = :artist, album = :album WHERE id = :songId")
    void updateSongMetadata(long songId, String title, String artist, String album);
}
