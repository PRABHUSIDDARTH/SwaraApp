package com.psthetech.swara.data.repository;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.RecoverableSecurityException;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;

import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.domain.model.Song;

import java.util.Collections;

/**
 * SongDeletionManager — authoritative manager for deleting audio files from MediaStore
 * and performing cascade database, playlist, queue, favorite, and artwork cleanups.
 *
 * MediaStore Deletion Architecture:
 * - Uses MediaStore content URI (content://media/external/audio/media/<songId>)
 * - Does NOT use deprecated DATA file paths
 * - On Android 11+ (API 30+): Uses MediaStore.createDeleteRequest for system consent prompts
 * - On Android 10 (API 29): Handles RecoverableSecurityException for intent sender request
 * - On API 28 and lower: Direct ContentResolver.delete()
 */
public class SongDeletionManager {

    private static final String TAG = "SongDeletionManager";

    public interface DeletionCallback {
        void onDeletionSuccess(Song song);
        void onDeletionFailed(Song song, String reason);
        void onSystemPromptRequired(IntentSenderRequest request, Song song);
    }

    private final Context context;
    private final AppDatabase db;
    private final PlaylistRepository playlistRepository;
    private final FavoritesRepository favoritesRepository;
    private final PlayHistoryRepository historyRepository;
    private final CustomArtworkStore customArtworkStore;

    public SongDeletionManager(Context context) {
        this.context = context.getApplicationContext();
        this.db = AppDatabase.getInstance(this.context);
        this.playlistRepository = new PlaylistRepository(db);
        this.favoritesRepository = new FavoritesRepository(db);
        this.historyRepository = new PlayHistoryRepository(db);
        this.customArtworkStore = new CustomArtworkStore(this.context);
    }

    /**
     * Initiates deletion of a song from MediaStore.
     */
    public void deleteSong(Song song, DeletionCallback callback) {
        if (song == null || song.getId() <= 0) {
            if (callback != null) callback.onDeletionFailed(song, "Invalid song ID");
            return;
        }

        Uri songUri = ArtworkRepository.getSongUri(song.getId());
        ContentResolver resolver = context.getContentResolver();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // API 30+ (Android 11+): System delete request dialog
                PendingIntent pi = MediaStore.createDeleteRequest(resolver, Collections.singletonList(songUri));
                IntentSenderRequest request = new IntentSenderRequest.Builder(pi.getIntentSender()).build();
                if (callback != null) {
                    callback.onSystemPromptRequired(request, song);
                }
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                // API 29 (Android 10): Try direct delete or catch RecoverableSecurityException
                try {
                    int rows = resolver.delete(songUri, null, null);
                    if (rows > 0) {
                        performCascadeCleanup(song);
                        if (callback != null) callback.onDeletionSuccess(song);
                    } else {
                        if (callback != null) callback.onDeletionFailed(song, "Song file not found in MediaStore");
                    }
                } catch (RecoverableSecurityException rse) {
                    IntentSenderRequest request = new IntentSenderRequest.Builder(
                            rse.getUserAction().getActionIntent().getIntentSender()).build();
                    if (callback != null) {
                        callback.onSystemPromptRequired(request, song);
                    }
                }
            } else {
                // API 28 and lower
                int rows = resolver.delete(songUri, null, null);
                if (rows > 0) {
                    performCascadeCleanup(song);
                    if (callback != null) callback.onDeletionSuccess(song);
                } else {
                    if (callback != null) callback.onDeletionFailed(song, "Song file not found in MediaStore");
                }
            }
        } catch (SecurityException se) {
            Log.e(TAG, "SecurityException deleting song " + song.getId(), se);
            if (callback != null) callback.onDeletionFailed(song, "Permission denied by system");
        } catch (Exception e) {
            Log.e(TAG, "Error deleting song " + song.getId(), e);
            if (callback != null) callback.onDeletionFailed(song, e.getMessage());
        }
    }

    /**
     * Called after system delete prompt (createDeleteRequest) completes successfully.
     */
    public void onSystemDeleteConfirmed(Song song) {
        if (song == null) return;
        performCascadeCleanup(song);
    }

    /**
     * Performs cascade cleanup across Room database, playlists, favorites, artwork, and queue.
     */
    public void performCascadeCleanup(Song song) {
        if (song == null) return;
        long songId = song.getId();
        Log.d(TAG, "Performing cascade cleanup for deleted song ID " + songId);

        // 1. Remove favorite reference
        favoritesRepository.removeFavorite(songId);

        // 2. Remove song from all playlists
        playlistRepository.deleteSongFromAllPlaylists(songId);

        // 3. Remove history record
        historyRepository.deleteHistoryForSong(songId);

        // 4. Custom artwork file cleanup
        customArtworkStore.removeCustomArtwork(songId);
    }
}
