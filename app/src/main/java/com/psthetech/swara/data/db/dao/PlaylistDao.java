package com.psthetech.swara.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.psthetech.swara.data.db.entity.Playlist;
import com.psthetech.swara.data.db.entity.PlaylistSong;

import java.util.List;

@Dao
public interface PlaylistDao {

    // ===== Playlist operations =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long createPlaylist(Playlist playlist);

    @Update
    void updatePlaylist(Playlist playlist);

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    void deletePlaylist(long playlistId);

    @Query("UPDATE playlists SET name = :name, modifiedAt = :modifiedAt WHERE id = :playlistId")
    void renamePlaylist(long playlistId, String name, long modifiedAt);

    /** LiveData for reactive playlist list */
    @Query("SELECT * FROM playlists ORDER BY modifiedAt DESC")
    LiveData<List<Playlist>> getAllPlaylistsLive();

    @Query("SELECT * FROM playlists ORDER BY modifiedAt DESC")
    List<Playlist> getAllPlaylists();

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    Playlist getPlaylistById(long playlistId);

    // ===== PlaylistSong operations =====

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void addSongToPlaylist(PlaylistSong song);

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    void removeSongFromPlaylist(long playlistId, long songId);

    /** All songs in playlist ordered by position */
    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    LiveData<List<PlaylistSong>> getPlaylistSongsLive(long playlistId);

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    List<PlaylistSong> getPlaylistSongs(long playlistId);

    @Query("UPDATE playlist_songs SET position = :position WHERE playlistId = :playlistId AND songId = :songId")
    void updateSongPosition(long playlistId, long songId, int position);

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    int getSongCount(long playlistId);

    @Query("SELECT MAX(position) FROM playlist_songs WHERE playlistId = :playlistId")
    int getMaxPosition(long playlistId);

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId)")
    boolean isSongInPlaylist(long playlistId, long songId);
}
