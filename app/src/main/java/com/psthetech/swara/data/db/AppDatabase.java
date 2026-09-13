package com.psthetech.swara.data.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

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
 * Version history:
 *  1  — Initial schema (placeholder)
 *  2  — Added albumId to FavoriteSong, Playlist, PlaylistSong, PlayHistory entities.
 *  3  — Added artworkPath column to playlists table (nullable, default NULL).
 *
 * NO allowMainThreadQueries — all access must be on a background thread via ExecutorService.
 *
 * Singleton pattern with double-checked locking.
 */
@Database(
    entities = {FavoriteSong.class, Playlist.class, PlaylistSong.class, PlayHistory.class},
    version = 3,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract FavoriteDao favoriteDao();
    public abstract PlaylistDao playlistDao();
    public abstract PlayHistoryDao playHistoryDao();

    // ===== Migrations =====

    /**
     * V2 → V3: Add nullable artworkPath column to playlists.
     * Safe ALTER TABLE — no data loss.
     */
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            android.database.Cursor cursor = database.query("PRAGMA table_info(playlists)");
            boolean hasColumn = false;
            try {
                int nameIndex = cursor.getColumnIndex("name");
                while (cursor.moveToNext()) {
                    if ("artworkPath".equals(cursor.getString(nameIndex))) {
                        hasColumn = true;
                        break;
                    }
                }
            } finally {
                cursor.close();
            }
            if (!hasColumn) {
                database.execSQL("ALTER TABLE playlists ADD COLUMN artworkPath TEXT");
            }
        }
    };

    // ===== Singleton =====

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
                            .addMigrations(MIGRATION_2_3)
                            // Destructive fallback only for schema versions before v2
                            .fallbackToDestructiveMigrationFrom(1)
                            .fallbackToDestructiveMigrationOnDowngrade()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
