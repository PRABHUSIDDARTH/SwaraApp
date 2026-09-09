package com.psthetech.swara.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.psthetech.swara.data.db.dao.FavoriteDao;
import com.psthetech.swara.data.db.dao.PlayHistoryDao;
import com.psthetech.swara.data.db.dao.PlaylistDao;
import com.psthetech.swara.data.db.entity.FavoriteSong;
import com.psthetech.swara.data.db.entity.PlayHistory;
import com.psthetech.swara.data.db.entity.Playlist;
import com.psthetech.swara.data.db.entity.PlaylistSong;

/**
 * Swara V2 Room database.
 *
 * Version 2 adds: albumId to FavoriteSong, Playlist, PlaylistSong, PlayHistory entities.
 * NO allowMainThreadQueries — all access must be on a background thread via ExecutorService.
 *
 * Singleton pattern with double-checked locking.
 */
@Database(
    entities = {FavoriteSong.class, Playlist.class, PlaylistSong.class, PlayHistory.class},
    version = 2,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract FavoriteDao favoriteDao();
    public abstract PlaylistDao playlistDao();
    public abstract PlayHistoryDao playHistoryDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "swara_v2.db"
                            )
                            // Destructive migration: V1 was a placeholder; start fresh for V2
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
