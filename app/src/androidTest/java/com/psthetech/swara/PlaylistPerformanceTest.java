package com.psthetech.swara;

import android.content.Context;
import android.util.Log;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.data.db.dao.PlaylistDao;
import com.psthetech.swara.data.db.entity.Playlist;
import com.psthetech.swara.data.db.entity.PlaylistSong;
import com.psthetech.swara.domain.model.Song;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class PlaylistPerformanceTest {

    private AppDatabase db;
    private PlaylistDao dao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        dao = db.playlistDao();
    }

    @After
    public void closeDb() {
        db.close();
    }

    private PlaylistSong createPlaylistSongEntity(long playlistId, Song song, int position) {
        return new PlaylistSong(
                playlistId, song.getId(), position,
                song.getTitle(), song.getArtist(), song.getAlbum(),
                song.getAlbumId(), song.getDuration());
    }

    @Test
    public void benchmarkAddSongsToPlaylist() {
        long playlistId = dao.createPlaylist(new Playlist("Test", System.currentTimeMillis(), System.currentTimeMillis()));

        List<Song> songs = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            songs.add(new Song(i, "Title " + i, "Artist " + i, "Album " + i, 0, 1000));
        }

        // We run the loop how PlaylistRepository does it currently
        long startTime = System.nanoTime();

        int nextPos = dao.getSongCount(playlistId);
        for (Song song : songs) {
            if (!dao.isSongInPlaylist(playlistId, song.getId())) {
                PlaylistSong ps = createPlaylistSongEntity(playlistId, song, nextPos++);
                dao.addSongToPlaylist(ps);
            }
        }

        long endTime = System.nanoTime();
        Log.i("PerfTest", "Current approach (N+1 queries) took: " + ((endTime - startTime) / 1_000_000) + " ms");
    }
}
