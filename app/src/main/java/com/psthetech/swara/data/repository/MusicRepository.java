package com.psthetech.swara.data.repository;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import com.psthetech.swara.SwaraApplication;
import com.psthetech.swara.domain.model.Album;
import com.psthetech.swara.domain.model.Artist;
import com.psthetech.swara.domain.model.Song;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Repository responsible for discovering music from the Android MediaStore.
 *
 * All queries run on the IoExecutor (background thread). Results are delivered
 * to the main thread via Handler + callback — no main-thread I/O.
 *
 * V2 improvements over V1:
 * - Runs off main thread
 * - Null-safe cursor handling
 * - Filters: IS_MUSIC = 1, DURATION >= 30_000ms
 * - Includes albumId for artwork
 * - Does NOT use the deprecated DATA column
 * - Provides Song, Album, and Artist views
 */
public class MusicRepository {

    private static final String TAG = "MusicRepository";

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback<T> {
        void onResult(T result);
        void onError(String message);
    }

    private static final Map<Long, Song> canonicalSongMap = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, List<Song>> canonicalArtistIndex = new java.util.concurrent.ConcurrentHashMap<>();

    public static Song getCanonicalSong(long songId) {
        return canonicalSongMap.get(songId);
    }

    public static List<Song> getSongsForArtistFromCache(String artistName) {
        if (artistName == null) return Collections.emptyList();
        String canonicalKey = Artist.getCanonicalKey(artistName);
        List<Song> cached = canonicalArtistIndex.get(canonicalKey);
        if (cached != null) {
            return cached;
        }
        return com.psthetech.swara.util.ArtistIdentityHelper.getSongsForArtist(canonicalKey, new ArrayList<>(canonicalSongMap.values()));
    }

    public Song getSongById(long songId) {
        Song cached = canonicalSongMap.get(songId);
        if (cached != null) return cached;
        List<Song> single = querySongs(
                MediaStore.Audio.Media._ID + " = ?",
                new String[]{String.valueOf(songId)},
                null
        );
        if (!single.isEmpty()) {
            Song s = single.get(0);
            canonicalSongMap.put(songId, s);
            return s;
        }
        return null;
    }

    public MusicRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Short clips are hidden unless the user turned that off in Settings. */
    private long minDurationMs() {
        return com.psthetech.swara.data.preference.LibraryPreferences.minDurationMs(context);
    }

    // ===== Songs =====

    /** Loads all songs from MediaStore asynchronously. Callback on main thread. */
    public void loadAllSongs(Callback<List<Song>> callback) {
        SwaraApplication.getInstance().getIoExecutor().execute(() -> {
            try {
                List<Song> songs = querySongs(null, null, null);
                mainHandler.post(() -> callback.onResult(songs));
            } catch (Exception e) {
                Log.e(TAG, "Failed to load songs", e);
                mainHandler.post(() -> callback.onError("Failed to load music: " + e.getMessage()));
            }
        });
    }

    /** Blocking full-library query; call from a worker thread only. */
    public List<Song> loadAllSongsBlocking() {
        return querySongs(null, null, null);
    }

    private static List<Song> capResults(List<Song> songs, int max) {
        return songs.size() > max ? new ArrayList<>(songs.subList(0, max)) : songs;
    }

    /** Loads recently added songs (last 30 days) asynchronously. */
    public void loadRecentlyAdded(Callback<List<Song>> callback) {
        SwaraApplication.getInstance().getIoExecutor().execute(() -> {
            try {
                long thirtyDaysAgoSec = (System.currentTimeMillis() / 1000) - (30L * 24 * 60 * 60);
                String selection = MediaStore.Audio.Media.IS_MUSIC + " = 1 AND "
                        + MediaStore.Audio.Media.DURATION + " >= " + minDurationMs() + " AND "
                        + MediaStore.Audio.Media.DATE_ADDED + " >= " + thirtyDaysAgoSec;
                // MediaProvider rejects "LIMIT" inside sortOrder on Android 11+; cap in Java.
                List<Song> songs = capResults(querySongs(selection, null,
                        MediaStore.Audio.Media.DATE_ADDED + " DESC"), 50);
                mainHandler.post(() -> callback.onResult(songs));
            } catch (Exception e) {
                Log.e(TAG, "Failed to load recently added", e);
                mainHandler.post(() -> callback.onError("Failed to load recently added"));
            }
        });
    }

    /** Builds albums from a list of songs (must be called off main thread or pass pre-loaded songs) */
    public void loadAlbums(Callback<List<Album>> callback) {
        SwaraApplication.getInstance().getIoExecutor().execute(() -> {
            try {
                List<Song> allSongs = querySongs(null, null, null);
                List<Album> albums = buildAlbums(allSongs);
                mainHandler.post(() -> callback.onResult(albums));
            } catch (Exception e) {
                Log.e(TAG, "Failed to load albums", e);
                mainHandler.post(() -> callback.onError("Failed to load albums"));
            }
        });
    }

    public void loadSongsForAlbum(long albumId, Callback<List<Song>> callback) {
        SwaraApplication.getInstance().getIoExecutor().execute(() -> {
            try {
                String selection = MediaStore.Audio.Media.IS_MUSIC + " = 1 AND "
                        + MediaStore.Audio.Media.ALBUM_ID + " = ?";
                String[] args = new String[]{String.valueOf(albumId)};
                List<Song> songs = querySongs(selection, args, MediaStore.Audio.Media.TRACK + " ASC");
                mainHandler.post(() -> callback.onResult(songs));
            } catch (Exception e) {
                Log.e(TAG, "Failed to load songs for album", e);
                mainHandler.post(() -> callback.onError("Failed to load album songs"));
            }
        });
    }

    public void loadSongsForArtist(String artistName, Callback<List<Song>> callback) {
        SwaraApplication.getInstance().getIoExecutor().execute(() -> {
            try {
                String canonicalKey = Artist.getCanonicalKey(artistName);
                List<Song> cached = canonicalArtistIndex.get(canonicalKey);
                if (cached != null && !cached.isEmpty()) {
                    mainHandler.post(() -> callback.onResult(new ArrayList<>(cached)));
                    return;
                }
                List<Song> allSongs = querySongs(null, null, null);
                buildArtists(allSongs);
                List<Song> songs = canonicalArtistIndex.get(canonicalKey);
                if (songs == null) {
                    songs = com.psthetech.swara.util.ArtistIdentityHelper.getSongsForArtist(canonicalKey, allSongs);
                }
                final List<Song> result = songs != null ? songs : Collections.emptyList();
                mainHandler.post(() -> callback.onResult(result));
            } catch (Exception e) {
                Log.e(TAG, "Failed to load songs for artist", e);
                mainHandler.post(() -> callback.onError("Failed to load artist songs"));
            }
        });
    }

    /** Builds artists from a list of songs */
    public void loadArtists(Callback<List<Artist>> callback) {
        SwaraApplication.getInstance().getIoExecutor().execute(() -> {
            try {
                List<Song> allSongs = querySongs(null, null, null);
                List<Artist> artists = buildArtists(allSongs);
                mainHandler.post(() -> callback.onResult(artists));
            } catch (Exception e) {
                Log.e(TAG, "Failed to load artists", e);
                mainHandler.post(() -> callback.onError("Failed to load artists"));
            }
        });
    }

    /** Searches songs, albums, artists by query. */
    public void search(String query, Callback<List<Song>> callback) {
        if (query == null || query.trim().isEmpty()) {
            callback.onResult(Collections.emptyList());
            return;
        }
        SwaraApplication.getInstance().getIoExecutor().execute(() -> {
            try {
                String lower = query.toLowerCase(Locale.getDefault()).trim();
                String selection = MediaStore.Audio.Media.IS_MUSIC + " = 1 AND "
                        + MediaStore.Audio.Media.DURATION + " >= " + minDurationMs() + " AND ("
                        + "LOWER(" + MediaStore.Audio.Media.TITLE + ") LIKE ? ESCAPE '\\' OR "
                        + "LOWER(" + MediaStore.Audio.Media.ARTIST + ") LIKE ? ESCAPE '\\' OR "
                        + "LOWER(" + MediaStore.Audio.Media.ALBUM + ") LIKE ? ESCAPE '\\')";
                String like = "%" + escapeLike(lower) + "%";
                String[] args = new String[]{like, like, like};
                List<Song> results = capResults(querySongs(selection, args,
                        MediaStore.Audio.Media.TITLE + " ASC"), 100);
                mainHandler.post(() -> callback.onResult(results));
            } catch (Exception e) {
                Log.e(TAG, "Search failed", e);
                mainHandler.post(() -> callback.onError("Search failed"));
            }
        });
    }

    /**
     * Callback for multi-category search results (Songs + Albums + Artists in one call).
     * Avoids 3 separate MediaStore queries for search.
     */
    public interface SearchCallback {
        void onResult(List<Song> songs, List<Album> albums, List<Artist> artists);
        void onError();
    }

    /**
     * Searches MediaStore once for songs matching the query, then derives Album and Artist
     * results from that same set. The query is already lower-cased by the caller.
     *
     * Matching:
     *  - Songs:   title, artist, or album contains query
     *  - Albums:  album name contains query (from any matching song)
     *  - Artists: artist name contains query (from any matching song)
     */
    public void searchAllCategories(String lowerQuery, SearchCallback callback) {
        if (lowerQuery == null || lowerQuery.trim().isEmpty()) {
            mainHandler.post(() -> callback.onResult(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList()));
            return;
        }
        SwaraApplication.getInstance().getIoExecutor().execute(() -> {
            try {
                String q = lowerQuery.trim();
                // Broad query: songs where title, artist, or album matches
                String selection = MediaStore.Audio.Media.IS_MUSIC + " = 1 AND "
                        + MediaStore.Audio.Media.DURATION + " >= " + minDurationMs() + " AND ("
                        + "LOWER(" + MediaStore.Audio.Media.TITLE + ") LIKE ? ESCAPE '\\' OR "
                        + "LOWER(" + MediaStore.Audio.Media.ARTIST + ") LIKE ? ESCAPE '\\' OR "
                        + "LOWER(" + MediaStore.Audio.Media.ALBUM + ") LIKE ? ESCAPE '\\')";
                String like = "%" + escapeLike(q) + "%";
                String[] args = new String[]{like, like, like};
                List<Song> allMatching = querySongs(selection, args,
                        MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC");

                // Songs: title matches query
                List<Song> songResults = new ArrayList<>();
                for (Song s : allMatching) {
                    if (s.getTitle().toLowerCase(Locale.getDefault()).contains(q)) {
                        songResults.add(s);
                    }
                }
                if (songResults.size() > 50) songResults = songResults.subList(0, 50);

                // Albums: distinct albums whose name matches query
                Map<Long, Song> albumReps = new LinkedHashMap<>();
                for (Song s : allMatching) {
                    if (s.getAlbum().toLowerCase(Locale.getDefault()).contains(q)) {
                        albumReps.putIfAbsent(s.getAlbumId(), s);
                    }
                }
                List<Album> albumResults = new ArrayList<>();
                for (Map.Entry<Long, Song> e : albumReps.entrySet()) {
                    Song rep = e.getValue();
                    albumResults.add(new Album(rep.getAlbumId(), rep.getAlbum(), rep.getArtist(),
                            1, rep.getYear(), Collections.singletonList(rep)));
                }

                // Artist results show each artist's full song list, which needs the library-wide
                // index. Build it now if the Artists tab hasn't been opened yet this session.
                if (canonicalArtistIndex.isEmpty()) {
                    buildArtists(querySongs(null, null, null));
                }

                // Artists: distinct canonical artists whose name matches query, containing all their songs
                List<Artist> allArtistsFromMatching = com.psthetech.swara.util.ArtistIdentityHelper.buildArtists(allMatching);
                List<Artist> artistResults = new ArrayList<>();
                String canonicalQuery = com.psthetech.swara.util.ArtistIdentityHelper.getCanonicalKey(q);
                for (Artist a : allArtistsFromMatching) {
                    if (a.getName().toLowerCase(Locale.getDefault()).contains(q)
                            || com.psthetech.swara.util.ArtistIdentityHelper.getCanonicalKey(a.getName()).contains(canonicalQuery)) {
                        List<Song> fullSongs = canonicalArtistIndex.get(a.getCanonicalKey());
                        if (fullSongs != null && !fullSongs.isEmpty()) {
                            long distinctAlbums = fullSongs.stream().map(Song::getAlbumId).distinct().count();
                            long repAlbumId = fullSongs.get(0).getAlbumId();
                            artistResults.add(new Artist(a.getName(), a.getCanonicalKey(), fullSongs.size(), (int) distinctAlbums, repAlbumId, fullSongs));
                        } else {
                            artistResults.add(a);
                        }
                    }
                }

                final List<Song>   finalSongs   = songResults;
                final List<Album>  finalAlbums  = albumResults;
                final List<Artist> finalArtists = artistResults;
                mainHandler.post(() -> callback.onResult(finalSongs, finalAlbums, finalArtists));
            } catch (Exception e) {
                Log.e(TAG, "searchAllCategories failed", e);
                mainHandler.post(callback::onError);
            }
        });
    }

    /** Escapes LIKE wildcards so "100%" or "a_b" match literally (paired with ESCAPE '\\'). */
    public static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    // ===== Internal query =====

    private List<Song> querySongs(String selection, String[] selectionArgs, String sortOrder) {
        List<Song> songs = new ArrayList<>();

        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.DATE_ADDED,
        };

        // A full-library query lets us drop cached songs that no longer exist.
        final boolean fullLibrary = selection == null;

        // Default selection: music files longer than threshold
        if (selection == null) {
            selection = MediaStore.Audio.Media.IS_MUSIC + " = 1 AND "
                    + MediaStore.Audio.Media.DURATION + " >= " + minDurationMs();
        }

        if (sortOrder == null) {
            sortOrder = MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC";
        }

        try (Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, sortOrder)) {
            if (cursor == null) {
                Log.w(TAG, "MediaStore query returned null cursor");
                return songs;
            }

            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
            int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK);
            int yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR);
            int dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED);

            while (cursor.moveToNext()) {
                try {
                    long id = cursor.getLong(idCol);
                    String title = cursor.getString(titleCol);
                    String artist = cursor.getString(artistCol);
                    String album = cursor.getString(albumCol);
                    long albumId = cursor.getLong(albumIdCol);
                    long duration = cursor.getLong(durationCol);
                    int track = cursor.getInt(trackCol);
                    int year = cursor.getInt(yearCol);
                    long dateAdded = cursor.getLong(dateAddedCol);

                    // Skip songs with id 0 (malformed entries)
                    if (id <= 0) continue;

                    Song song = new Song(id, title, artist, album, albumId, duration, track, year, dateAdded);
                    canonicalSongMap.put(id, song);
                    songs.add(song);
                } catch (Exception rowEx) {
                    Log.w(TAG, "Skipping malformed MediaStore row", rowEx);
                }
            }
        }

        if (fullLibrary) {
            java.util.Set<Long> present = new java.util.HashSet<>();
            for (Song s : songs) present.add(s.getId());
            canonicalSongMap.keySet().retainAll(present);
        }

        Log.d(TAG, "Loaded " + songs.size() + " songs from MediaStore");
        return songs;
    }

    // ===== Album / Artist builders =====

    private List<Album> buildAlbums(List<Song> songs) {
        Map<Long, List<Song>> albumMap = new LinkedHashMap<>();
        for (Song song : songs) {
            albumMap.computeIfAbsent(song.getAlbumId(), k -> new ArrayList<>()).add(song);
        }

        List<Album> albums = new ArrayList<>();
        for (Map.Entry<Long, List<Song>> entry : albumMap.entrySet()) {
            List<Song> albumSongs = entry.getValue();
            Song first = albumSongs.get(0);
            // Sort by track number
            Collections.sort(albumSongs, (a, b) -> Integer.compare(a.getTrackNumber(), b.getTrackNumber()));
            int year = 0;
            for (Song s : albumSongs) {
                if (s.getYear() > 0) { year = s.getYear(); break; }
            }
            albums.add(new Album(entry.getKey(), first.getAlbum(), first.getArtist(),
                    albumSongs.size(), year, albumSongs));
        }

        Collections.sort(albums, (a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle()));
        return albums;
    }

    public static List<Artist> buildArtists(List<Song> songs) {
        List<Artist> artists = com.psthetech.swara.util.ArtistIdentityHelper.buildArtists(songs);
        canonicalArtistIndex.clear();
        for (Artist a : artists) {
            canonicalArtistIndex.put(a.getCanonicalKey(), a.getSongs());
        }
        return artists;
    }
}
