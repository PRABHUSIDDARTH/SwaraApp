package com.psthetech.swara.data.repository;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * PlaylistArtworkStore — persists user-selected custom artwork overrides per playlist
 * and cached collage artwork in internal storage.
 *
 * Files are stored at:
 *   <filesDir>/playlist_artwork/<playlistId>.jpg   ← user-selected custom artwork
 *   <filesDir>/playlist_artwork/collage_<playlistId>.jpg ← auto-generated collage cache
 *
 * Using internal files avoids Android permission issues and URI invalidation that occurs
 * when storing external content URIs directly.
 *
 * Playlist identity is tracked via Room playlist ID (long).
 */
public class PlaylistArtworkStore {

    private static final String TAG = "PlaylistArtworkStore";
    private static final String FOLDER_NAME = "playlist_artwork";

    private final Context context;
    private final File artworkDir;

    public PlaylistArtworkStore(Context context) {
        this.context = context.getApplicationContext();
        this.artworkDir = new File(this.context.getFilesDir(), FOLDER_NAME);
        if (!artworkDir.exists()) {
            artworkDir.mkdirs();
        }
    }

    // ===== Custom Artwork (user-selected) =====

    /**
     * Saves a user-selected image URI as custom artwork for the given playlistId.
     * Returns true on success.
     */
    public boolean saveCustomArtwork(long playlistId, Uri sourceUri) {
        if (playlistId <= 0 || sourceUri == null) return false;
        File targetFile = getCustomArtworkFile(playlistId);
        try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
             FileOutputStream out = new FileOutputStream(targetFile)) {

            if (in == null) return false;

            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            Log.d(TAG, "Saved custom playlist artwork for playlist ID " + playlistId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to save custom playlist artwork for ID " + playlistId, e);
            if (targetFile.exists()) targetFile.delete();
            return false;
        }
    }

    /**
     * Removes the custom artwork override for the given playlistId.
     */
    public boolean removeCustomArtwork(long playlistId) {
        File file = getCustomArtworkFile(playlistId);
        if (file.exists()) {
            boolean deleted = file.delete();
            Log.d(TAG, "Removed custom playlist artwork for ID " + playlistId + ": " + deleted);
            return deleted;
        }
        return false;
    }

    /**
     * Checks whether a custom artwork override exists for the given playlistId.
     */
    public boolean hasCustomArtwork(long playlistId) {
        File file = getCustomArtworkFile(playlistId);
        return file.exists() && file.length() > 0;
    }

    /**
     * Returns the Uri for custom playlist artwork if it exists, or null.
     */
    @Nullable
    public Uri getCustomArtworkUri(long playlistId) {
        File file = getCustomArtworkFile(playlistId);
        if (file.exists() && file.length() > 0) {
            return Uri.fromFile(file);
        }
        return null;
    }

    /**
     * Returns the File for custom playlist artwork (may not exist yet).
     */
    public File getCustomArtworkFile(long playlistId) {
        return new File(artworkDir, playlistId + ".jpg");
    }

    // ===== Collage Cache =====

    /**
     * Saves a generated collage Bitmap as cached collage artwork for the given playlistId.
     * Call only from a background thread (performs file I/O).
     */
    public boolean saveCollageArtwork(long playlistId, Bitmap collage) {
        if (playlistId <= 0 || collage == null) return false;
        File targetFile = getCollageFile(playlistId);
        try (FileOutputStream out = new FileOutputStream(targetFile)) {
            collage.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            Log.d(TAG, "Saved collage artwork for playlist ID " + playlistId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to save collage artwork for ID " + playlistId, e);
            if (targetFile.exists()) targetFile.delete();
            return false;
        }
    }

    /**
     * Invalidates (touches) the collage file so Glide reloads it after playlist changes.
     */
    public void invalidateCollage(long playlistId) {
        File f = getCollageFile(playlistId);
        if (f.exists()) f.setLastModified(System.currentTimeMillis());
    }

    /**
     * Returns the File for collage playlist artwork cache (may not exist yet).
     */
    public File getCollageFile(long playlistId) {
        return new File(artworkDir, "collage_" + playlistId + ".jpg");
    }

    /**
     * Returns true if a cached collage exists for the playlist.
     */
    public boolean hasCollage(long playlistId) {
        File f = getCollageFile(playlistId);
        return f.exists() && f.length() > 0;
    }

    // ===== Cleanup =====

    /**
     * Deletes all artwork files (custom + collage) for the given playlistId.
     * Call when a playlist is deleted.
     */
    public void deleteAllArtwork(long playlistId) {
        File custom = getCustomArtworkFile(playlistId);
        File collage = getCollageFile(playlistId);
        if (custom.exists()) custom.delete();
        if (collage.exists()) collage.delete();
        Log.d(TAG, "Deleted all artwork for playlist ID " + playlistId);
    }
}
