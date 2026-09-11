package com.psthetech.swara.data.repository;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * CustomArtworkStore — persists user-selected custom artwork overrides per song in internal storage.
 *
 * Artwork overrides are saved into the app's internal files directory:
 *   <filesDir>/custom_artwork/<songId>.jpg
 *
 * Storing the image as a local file avoids Android system permission loss or URI invalidation
 * that occurs when storing raw external content URIs.
 *
 * The song identity is tracked via MediaStore song ID.
 */
public class CustomArtworkStore {

    private static final String TAG = "CustomArtworkStore";
    private static final String FOLDER_NAME = "custom_artwork";

    private final Context context;
    private final File artworkDir;

    public CustomArtworkStore(Context context) {
        this.context = context.getApplicationContext();
        this.artworkDir = new File(this.context.getFilesDir(), FOLDER_NAME);
        if (!artworkDir.exists()) {
            artworkDir.mkdirs();
        }
    }

    /**
     * Saves an image input stream as custom artwork for the given songId.
     */
    public boolean saveCustomArtwork(long songId, Uri sourceUri) {
        if (songId <= 0 || sourceUri == null) return false;
        File targetFile = getArtworkFile(songId);
        try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
             FileOutputStream out = new FileOutputStream(targetFile)) {

            if (in == null) return false;

            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            Log.d(TAG, "Successfully saved custom artwork for song ID " + songId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to save custom artwork for song ID " + songId, e);
            if (targetFile.exists()) {
                targetFile.delete();
            }
            return false;
        }
    }

    /**
     * Removes the custom artwork override for the given songId.
     */
    public boolean removeCustomArtwork(long songId) {
        File file = getArtworkFile(songId);
        if (file.exists()) {
            boolean deleted = file.delete();
            Log.d(TAG, "Removed custom artwork for song ID " + songId + ": " + deleted);
            return deleted;
        }
        return false;
    }

    /**
     * Checks whether a custom artwork override exists for the given songId.
     */
    public boolean hasCustomArtwork(long songId) {
        File file = getArtworkFile(songId);
        return file.exists() && file.length() > 0;
    }

    /**
     * Returns the Uri for custom artwork if it exists, or null if no custom override is set.
     */
    @Nullable
    public Uri getCustomArtworkUri(long songId) {
        File file = getArtworkFile(songId);
        if (file.exists() && file.length() > 0) {
            return Uri.fromFile(file);
        }
        return null;
    }

    /**
     * Returns the File location for custom artwork of a given songId.
     */
    public File getArtworkFile(long songId) {
        return new File(artworkDir, songId + ".jpg");
    }
}
