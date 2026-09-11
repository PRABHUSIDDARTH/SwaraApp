package com.psthetech.swara.data.repository;

import android.content.ContentUris;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.Nullable;

import com.psthetech.swara.domain.model.Song;

/**
 * Centralized Artwork Repository for resolving artwork URIs across SWARA.
 *
 * Priority chain (highest to lowest):
 *  1. User-custom artwork override (via CustomArtworkStore internal storage)
 *  2. Embedded artwork from audio file (via song content URI: content://media/external/audio/media/<id>)
 *  3. MediaStore album artwork (via album content URI: content://media/external/audio/albumart/<albumId>)
 *  4. Cached previously-resolved artwork (Glide disk/memory cache)
 *  5. Swara fallback artwork (ic_artwork_fallback)
 *
 * This repository resolves URIs asynchronously without blocking the UI thread or performing heavy bitmap decoding.
 */
public class ArtworkRepository {

    private static final Uri ALBUM_ART_BASE_URI =
            Uri.parse("content://media/external/audio/albumart");

    private final Context context;
    private final CustomArtworkStore customArtworkStore;

    public ArtworkRepository(Context context) {
        this.context = context.getApplicationContext();
        this.customArtworkStore = new CustomArtworkStore(this.context);
    }

    /**
     * Returns the primary artwork URI for a given song according to the priority chain.
     * 1. Returns custom artwork URI if override exists.
     * 2. Returns song content URI for embedded artwork extraction.
     * 3. Fallback to album art URI if song is null or ID is invalid.
     */
    public Uri getArtworkUri(@Nullable Song song) {
        if (song == null) return null;
        
        // Priority 1: Custom Artwork Override
        Uri customUri = customArtworkStore.getCustomArtworkUri(song.getId());
        if (customUri != null) {
            return customUri;
        }

        // Priority 2: Embedded Artwork from Song Content URI
        if (song.getId() > 0) {
            return getSongUri(song.getId());
        }

        // Priority 3: MediaStore Album Artwork
        return getAlbumArtUri(song.getAlbumId());
    }

    /**
     * Checks if a custom artwork override exists for the given songId.
     */
    public boolean hasCustomArtwork(long songId) {
        return customArtworkStore.hasCustomArtwork(songId);
    }

    /**
     * Saves a user-selected image URI as custom artwork for the given songId.
     */
    public boolean saveCustomArtwork(long songId, Uri imageUri) {
        return customArtworkStore.saveCustomArtwork(songId, imageUri);
    }

    /**
     * Resets custom artwork override for the given songId back to default.
     */
    public boolean removeCustomArtwork(long songId) {
        return customArtworkStore.removeCustomArtwork(songId);
    }

    /**
     * Returns artwork URI for a given albumId.
     */
    public static Uri getAlbumArtUri(long albumId) {
        if (albumId <= 0) return null;
        return ContentUris.withAppendedId(ALBUM_ART_BASE_URI, albumId);
    }

    /**
     * Returns the content URI for a song (used for playing audio and embedded artwork loading).
     */
    public static Uri getSongUri(long songId) {
        if (songId <= 0) return null;
        return ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId);
    }

    public CustomArtworkStore getCustomArtworkStore() {
        return customArtworkStore;
    }
}
