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

public class MigrationV5Test {

    private static final String DB = "migration-v5-test";

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
    public void migrate4To5_keepsMoodsAndCreatesOverrideTable() throws IOException {
        SupportSQLiteDatabase v4 = helper.createDatabase(DB, 4);
        v4.execSQL("INSERT INTO song_mood (songId, valence, energy, tempoBpm, scorerVersion, status, analyzedAt) "
                + "VALUES (7, 0.4, 0.6, 120.0, 3, 0, 5)");
        ContentValues pl = new ContentValues();
        pl.put("name", "Road trip");
        pl.put("createdAt", 1L);
        pl.put("modifiedAt", 2L);
        v4.insert("playlists", SQLiteDatabase.CONFLICT_REPLACE, pl);
        v4.close();

        SupportSQLiteDatabase v5 = helper.runMigrationsAndValidate(
                DB, 5, true, AppDatabase.MIGRATION_4_5);

        assertEquals(1, count(v5, "song_mood"));
        assertEquals(1, count(v5, "playlists"));
        assertEquals(0, count(v5, "mood_override"));

        v5.execSQL("INSERT INTO mood_override (songId, label, setAt) VALUES (7, 'HAPPY', 9)");
        v5.execSQL("INSERT OR REPLACE INTO mood_override (songId, label, setAt) VALUES (7, 'SAD', 10)");
        assertEquals(1, count(v5, "mood_override"));
        try (Cursor c = v5.query("SELECT label FROM mood_override WHERE songId = 7")) {
            c.moveToFirst();
            assertEquals("SAD", c.getString(0));
        }
        // The analysis row is a separate layer and stays as it was.
        try (Cursor c = v5.query("SELECT valence FROM song_mood WHERE songId = 7")) {
            c.moveToFirst();
            assertEquals(0.4, c.getDouble(0), 1e-9);
        }
    }
}
