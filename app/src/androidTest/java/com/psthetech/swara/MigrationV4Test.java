package com.psthetech.swara;

import static org.junit.Assert.assertEquals;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.platform.app.InstrumentationRegistry;

import com.psthetech.swara.data.db.AppDatabase;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

public class MigrationV4Test {

    private static final String DB = "migration-test";

    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase.class,
            java.util.Collections.emptyList(),
            new FrameworkSQLiteOpenHelperFactory());

    private static int count(SupportSQLiteDatabase db, String table) {
        try (Cursor c = db.query("SELECT COUNT(*) FROM " + table)) {
            c.moveToFirst();
            return c.getInt(0);
        }
    }

    @Test
    public void migrate3To4_keepsPlaylistsAndCreatesTables() throws IOException {
        SupportSQLiteDatabase v3 = helper.createDatabase(DB, 3);
        ContentValues pl = new ContentValues();
        pl.put("name", "Road trip");
        pl.put("createdAt", 1L);
        pl.put("modifiedAt", 2L);
        v3.insert("playlists", SQLiteDatabase.CONFLICT_REPLACE, pl);
        ContentValues fav = new ContentValues();
        fav.put("id", 42L);
        fav.put("title", "T");
        fav.put("artist", "A");
        fav.put("album", "B");
        fav.put("albumId", 1L);
        fav.put("duration", 1000L);
        fav.put("favoritedAt", 3L);
        v3.insert("favorites", SQLiteDatabase.CONFLICT_REPLACE, fav);
        v3.close();

        SupportSQLiteDatabase v4 = helper.runMigrationsAndValidate(
                DB, 4, true, AppDatabase.MIGRATION_3_4);

        assertEquals(1, count(v4, "playlists"));
        assertEquals(1, count(v4, "favorites"));

        v4.execSQL("INSERT INTO song_mood (songId, valence, energy, tempoBpm, scorerVersion, status, analyzedAt) "
                + "VALUES (1, 0.5, 0.5, 100.0, 1, 0, 5)");
        v4.execSQL("INSERT INTO listen_events (songId, type, positionMs, timestamp, hourOfDay) "
                + "VALUES (1, 0, 0, 5, 12)");
        v4.execSQL("INSERT INTO mixes (mixKey, title, songIds, builtAt) VALUES ('k', 't', '1,2', 5)");
        assertEquals(1, count(v4, "song_mood"));
        assertEquals(1, count(v4, "listen_events"));
        assertEquals(1, count(v4, "mixes"));
    }
}
