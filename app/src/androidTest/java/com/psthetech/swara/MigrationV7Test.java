package com.psthetech.swara;

import static org.junit.Assert.assertEquals;

import android.database.Cursor;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.platform.app.InstrumentationRegistry;

import com.psthetech.swara.data.db.AppDatabase;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

public class MigrationV7Test {

    private static final String DB = "migration-v7-test";

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
    public void migrate6To7_createsEmptyArtistAliasAndKeepsData() throws IOException {
        SupportSQLiteDatabase v6 = helper.createDatabase(DB, 6);
        v6.execSQL("INSERT INTO playlists (name, createdAt, modifiedAt) VALUES ('Road trip', 1, 2)");
        v6.execSQL("INSERT INTO mood_override (songId, label, setAt) VALUES (7, 'HAPPY', 9)");
        v6.execSQL("INSERT INTO song_snapshot (songId, title, artist, artistKey, album, albumId, firstHeardDay) "
                + "VALUES (7, 'T', 'A', 'a', 'Al', 3, 20261017)");
        v6.close();

        SupportSQLiteDatabase v7 = helper.runMigrationsAndValidate(
                DB, 7, true, AppDatabase.MIGRATION_6_7);

        assertEquals(0, count(v7, "artist_alias"));
        assertEquals(1, count(v7, "playlists"));
        assertEquals(1, count(v7, "mood_override"));
        assertEquals(1, count(v7, "song_snapshot"));

        v7.execSQL("INSERT INTO artist_alias (from_key, to_key, created_at) VALUES ('anirudh', NULL, 1)");
        assertEquals(1, count(v7, "artist_alias"));
    }
}
