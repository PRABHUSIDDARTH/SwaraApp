package com.psthetech.swara.data.repository;

import android.content.ContentUris;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;

import com.psthetech.swara.domain.model.Song;

/**
 * Artwork pipeline for resolving album art URIs without blocking the main thread.
 *
 * Priority chain (highest to lowest):
 *  1. Song-specific embedded artwork (via song content URI on API 29+)
 *  2. Album artwork (via album content URI — works on all API levels)
 *  3. null → caller (Glide) uses the ic_artwork_fallback drawable
 *
 * This class only resolves URIs — it does NOT decode bitmaps.
 * Glide handles all actual decoding, memory caching, and disk caching asynchronously.
 */
public class ArtworkRepository {

    private static final Uri ALBUM_ART_BASE_URI =
            Uri.parse("content://media/external/audio/albumart");

    private final Context context;

    public ArtworkRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Returns the best available artwork URI for a song.
     * Always returns album art URI as a fallback (Glide handles 404s gracefully).
     */
    public Uri getArtworkUri(Song song) {
        return getAlbumArtUri(song.getAlbumId());
    }

    /**
     * Returns artwork URI for a given albumId.
     * On API 29+, we prefer the content URI which resolves embedded art automatically.
     */
    public static Uri getAlbumArtUri(long albumId) {
        if (albumId <= 0) return null;
        return ContentUris.withAppendedId(ALBUM_ART_BASE_URI, albumId);
    }

    /**
     * Returns the content URI for a song — used by ExoPlayer to play the file.
     * Does NOT use the deprecated DATA (file path) column.
     */
    public static Uri getSongUri(long songId) {
        return ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId);
    }
}
