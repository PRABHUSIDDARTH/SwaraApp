package com.psthetech.swara.data.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.psthetech.swara.data.db.dao.FavoriteDao;
import com.psthetech.swara.data.db.dao.ListenEventDao;
import com.psthetech.swara.data.db.dao.MixDao;
import com.psthetech.swara.data.db.dao.MoodDao;
import com.psthetech.swara.data.db.dao.PlayHistoryDao;
import com.psthetech.swara.data.db.dao.PlaylistDao;
import com.psthetech.swara.data.db.entity.FavoriteSong;
import com.psthetech.swara.data.db.entity.ListenEvent;
import com.psthetech.swara.data.db.entity.Mix;
import com.psthetech.swara.data.db.entity.PlayHistory;
import com.psthetech.swara.data.db.entity.Playlist;
import com.psthetech.swara.data.db.entity.PlaylistSong;
import com.psthetech.swara.data.db.entity.SongMood;

/**
 * Swara V2 Room database.
 *
 * Version history:
 *  1  — Initial schema (placeholder)
 *  2  — Added albumId to FavoriteSong, Playlist, PlaylistSong, PlayHistory entities.
 *  3  — Added artworkPath column to playlists table (nullable, default NULL).
 *  4  — Added song_mood, listen_events and mixes tables (Vibe Mode recommender).
 *
 * NO allowMainThreadQueries — all access must be on a background thread via ExecutorService.
 *
 * Singleton pattern with double-checked locking.
 */
@Database(
    entities = {FavoriteSong.class, Playlist.class, PlaylistSong.class, PlayHistory.class,
            SongMood.class, ListenEvent.class, Mix.class},
    version = 4,
    exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract FavoriteDao favoriteDao();
    public abstract PlaylistDao playlistDao();
    public abstract PlayHistoryDao playHistoryDao();
    public abstract MoodDao moodDao();
    public abstract ListenEventDao listenEventDao();
    public abstract MixDao mixDao();

    // ===== Migrations =====

    /**
     * V2 → V3: Add nullable artworkPath column to playlists.
     * Safe ALTER TABLE — no data loss.
     */
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            boolean hasColumn = false;
            try (android.database.Cursor cursor = database.query("PRAGMA table_info(playlists)")) {
                int nameIndex = cursor.getColumnIndex("name");
                while (cursor.moveToNext()) {
                    if ("artworkPath".equals(cursor.getString(nameIndex))) {
                        hasColumn = true;
                        break;
                    }
                }
            }
            if (!hasColumn) {
                database.execSQL("ALTER TABLE playlists ADD COLUMN artworkPath TEXT");
            }
        }
    };

    /** V3 -> V4: add song_mood, listen_events and mixes tables. */
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `song_mood` (`songId` INTEGER NOT NULL, `valence` REAL NOT NULL, `energy` REAL NOT NULL, `tempoBpm` REAL NOT NULL, `rawFeatures` BLOB, `scorerVersion` INTEGER NOT NULL, `status` INTEGER NOT NULL, `analyzedAt` INTEGER NOT NULL, PRIMARY KEY(`songId`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS `listen_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `songId` INTEGER NOT NULL, `type` INTEGER NOT NULL, `positionMs` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `hourOfDay` INTEGER NOT NULL)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_listen_events_songId` ON `listen_events` (`songId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_listen_events_timestamp` ON `listen_events` (`timestamp`)");
            database.execSQL("CREATE TABLE IF NOT EXISTS `mixes` (`mixKey` TEXT NOT NULL, `title` TEXT, `songIds` TEXT, `builtAt` INTEGER NOT NULL, PRIMARY KEY(`mixKey`))");
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
                            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
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
