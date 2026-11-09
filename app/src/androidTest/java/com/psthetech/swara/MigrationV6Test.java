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

public class MigrationV6Test {

    private static final String DB = "migration-v6-test";

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
    public void migrate5To6_keepsDataAndCreatesWrappedTables() throws IOException {
        SupportSQLiteDatabase v5 = helper.createDatabase(DB, 5);
        ContentValues pl = new ContentValues();
        pl.put("name", "Road trip");
        pl.put("createdAt", 1L);
        pl.put("modifiedAt", 2L);
        v5.insert("playlists", SQLiteDatabase.CONFLICT_REPLACE, pl);
        v5.execSQL("INSERT INTO song_mood (songId, valence, energy, tempoBpm, scorerVersion, status, analyzedAt) "
                + "VALUES (7, 0.4, 0.6, 120.0, 3, 0, 5)");
        v5.execSQL("INSERT INTO mood_override (songId, label, setAt) VALUES (7, 'HAPPY', 9)");
        v5.close();

        SupportSQLiteDatabase v6 = helper.runMigrationsAndValidate(
                DB, 6, true, AppDatabase.MIGRATION_5_6);

        assertEquals(1, count(v6, "playlists"));
        assertEquals(1, count(v6, "song_mood"));
        assertEquals(1, count(v6, "mood_override"));

        v6.execSQL("INSERT INTO listen_daily (day, songId, playedMs, plays, finishes, skips, repeats) "
                + "VALUES (20261017, 7, 60000, 1, 1, 0, 0)");
        try (Cursor c = v6.query("SELECT vibeStarts FROM listen_daily WHERE songId = 7")) {
            c.moveToFirst();
            assertEquals(0, c.getInt(0)); // DEFAULT 0
        }
        v6.execSQL("INSERT INTO listen_hourly (day, hour, playedMs) VALUES (20261017, 23, 60000)");
        v6.execSQL("INSERT INTO song_snapshot (songId, title, artist, artistKey, album, albumId, firstHeardDay) "
                + "VALUES (7, 'T', 'A', 'a', 'Al', 3, 20261017)");
        v6.execSQL("INSERT INTO recap (recapKey, periodStart, periodEnd, statsJson, createdAt, seen, bannerDismissed) "
                + "VALUES ('month-2026-10', 20261001, 20261031, '{}', 1, 0, 0)");
        assertEquals(1, count(v6, "listen_daily"));
        assertEquals(1, count(v6, "listen_hourly"));
        assertEquals(1, count(v6, "song_snapshot"));
        assertEquals(1, count(v6, "recap"));
    }
}
