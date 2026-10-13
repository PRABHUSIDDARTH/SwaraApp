package com.psthetech.swara.data.repository;

import android.app.PendingIntent;
import android.app.RecoverableSecurityException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import androidx.activity.result.IntentSenderRequest;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psthetech.swara.SwaraApplication;
import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.domain.model.SongMetadata;
import com.psthetech.swara.ui.metadata.SaveMetadataResult;
import com.psthetech.swara.util.Id3MetadataWriter;

import java.io.File;
import java.io.OutputStream;
import java.util.Collections;

/**
 * Repository responsible for reading, validating, and authoritatively persisting
 * song metadata to audio file tags (ID3v2), MediaStore, and Room cache.
 */
public class SongMetadataRepository {

    private static final String TAG = "SongMetadataRepository";

    private final Context context;
    private final AppDatabase db;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface MetadataLoadCallback {
        void onLoaded(@NonNull SongMetadata metadata);
        void onError(@NonNull String error);
    }

    public interface MetadataSaveCallback {
        void onResult(@NonNull SaveMetadataResult result);
    }

    public SongMetadataRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.db = AppDatabase.getInstance(this.context);
    }

    /**
     * Loads complete metadata for a song asynchronously.
     */
    public void loadSongMetadata(@NonNull Song song, @NonNull MetadataLoadCallback callback) {
        SwaraApplication.getInstance().getIoExecutor().execute(() -> {
            try {
                SongMetadata metadata = querySongMetadata(song);
                mainHandler.post(() -> callback.onLoaded(metadata));
            } catch (Exception e) {
                Log.e(TAG, "Error loading metadata for song " + song.getId(), e);
                mainHandler.post(() -> callback.onError("Failed to load metadata: " + e.getMessage()));
            }
        });
    }

    /**
     * Authoritatively persists updated metadata to ID3 file tags, MediaStore, and Room caches.
     */
    public void saveSongMetadata(@NonNull SongMetadata original,
                                 @NonNull SongMetadata updated,
                                 @NonNull MetadataSaveCallback callback) {
        SwaraApplication.getInstance().getIoExecutor().execute(() -> {
            try {
                SaveMetadataResult result = performSave(original, updated);
                mainHandler.post(() -> callback.onResult(result));
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error saving song metadata", e);
                mainHandler.post(() -> callback.onResult(SaveMetadataResult.failed("Error saving metadata: " + e.getMessage())));
            }
        });
    }

    private SaveMetadataResult performSave(SongMetadata original, SongMetadata updated) {
        Uri songUri = updated.getContentUri();
        if (songUri == null) {
            songUri = ArtworkRepository.getSongUri(updated.getSongId());
        }

        ContentResolver resolver = context.getContentResolver();

        // Step 1: Pre-check write permission / Scoped Storage consent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Check if we can write to the URI or if we need OS consent
            try (OutputStream os = resolver.openOutputStream(songUri, "wa")) {
                // If this succeeds, write permission is already granted
            } catch (SecurityException se) {
                Log.d(TAG, "Scoped storage prompt required for URI: " + songUri);
                PendingIntent pi = MediaStore.createWriteRequest(resolver, Collections.singletonList(songUri));
                IntentSenderRequest request = new IntentSenderRequest.Builder(pi.getIntentSender()).build();
                return SaveMetadataResult.systemPromptRequired(request);
            } catch (Exception e) {
                // Ignore file append check errors, proceed to full write
            }
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            try (OutputStream os = resolver.openOutputStream(songUri, "wa")) {
                // Write permission check
            } catch (RecoverableSecurityException rse) {
                IntentSenderRequest request = new IntentSenderRequest.Builder(
                        rse.getUserAction().getActionIntent().getIntentSender()).build();
                return SaveMetadataResult.systemPromptRequired(request);
            } catch (SecurityException se) {
                return SaveMetadataResult.failed("Write permission denied by Android system");
            } catch (Exception ignored) {}
        }

        // Step 2: Write embedded file tags (ID3 for MP3)
        boolean embeddedWritten = false;
        boolean isMp3 = Id3MetadataWriter.isSupportedFormat(updated.getMimeType(), updated.getFilePath());

        if (isMp3) {
            embeddedWritten = Id3MetadataWriter.writeMetadata(context, songUri, updated);
            if (!embeddedWritten) {
                Log.w(TAG, "ID3 tag write failed or skipped for " + songUri);
            }
        }

        // Step 3: Update MediaStore columns
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.TITLE, updated.getTitle());
        values.put(MediaStore.Audio.Media.ARTIST, updated.getArtist());
        values.put(MediaStore.Audio.Media.ALBUM, updated.getAlbum());
        if (updated.getYear() > 0) {
            values.put(MediaStore.Audio.Media.YEAR, updated.getYear());
        }
        if (updated.getTrackNumber() > 0) {
            values.put(MediaStore.Audio.Media.TRACK, updated.getTrackNumber());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (updated.getGenre() != null && !updated.getGenre().isEmpty()) {
                values.put(MediaStore.Audio.Media.GENRE, updated.getGenre());
            }
            if (updated.getComposer() != null && !updated.getComposer().isEmpty()) {
                values.put(MediaStore.Audio.Media.COMPOSER, updated.getComposer());
            }
            if (updated.getAlbumArtist() != null && !updated.getAlbumArtist().isEmpty()) {
                values.put(MediaStore.Audio.Media.ALBUM_ARTIST, updated.getAlbumArtist());
            }
            if (updated.getDiscNumber() > 0) {
                values.put(MediaStore.Audio.Media.DISC_NUMBER, String.valueOf(updated.getDiscNumber()));
            }
        }

        try {
            int rowsUpdated = resolver.update(songUri, values, null, null);
            Log.d(TAG, "MediaStore rows updated: " + rowsUpdated);
        } catch (SecurityException se) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                PendingIntent pi = MediaStore.createWriteRequest(resolver, Collections.singletonList(songUri));
                IntentSenderRequest request = new IntentSenderRequest.Builder(pi.getIntentSender()).build();
                return SaveMetadataResult.systemPromptRequired(request);
            } else {
                return SaveMetadataResult.failed("Write permission denied by Android system");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating MediaStore for " + songUri, e);
        }

        // Step 4: Rescan the file via MediaScannerConnection so relational tables refresh
        if (updated.getFilePath() != null && !updated.getFilePath().isEmpty()) {
            File f = new File(updated.getFilePath());
            if (f.exists()) {
                MediaScannerConnection.scanFile(context, new String[]{updated.getFilePath()}, null, null);
            }
        }

        // Step 5: Synchronize Room denormalized caches
        try {
            db.runInTransaction(() -> {
                db.favoriteDao().updateSongMetadata(updated.getSongId(), updated.getTitle(), updated.getArtist(), updated.getAlbum());
                db.playlistDao().updateSongMetadata(updated.getSongId(), updated.getTitle(), updated.getArtist(), updated.getAlbum());
                db.playHistoryDao().updateSongMetadata(updated.getSongId(), updated.getTitle(), updated.getArtist(), updated.getAlbum());
            });
        } catch (Exception e) {
            Log.w(TAG, "Error updating Room caches for song " + updated.getSongId(), e);
        }

        if (!isMp3) {
            return SaveMetadataResult.partialSuccess(updated,
                    "MediaStore updated. Embedded tags are only supported for MP3 audio files.");
        }

        return SaveMetadataResult.success(updated);
    }

    private SongMetadata querySongMetadata(Song song) {
        long songId = song.getId();
        Uri songUri = ArtworkRepository.getSongUri(songId);

        String title = song.getTitle();
        String artist = song.getArtist();
        String album = song.getAlbum();
        String albumArtist = "";
        String genre = "";
        int year = song.getYear();
        int trackNumber = song.getTrackNumber();
        int discNumber = 0;
        String composer = "";
        String comment = "";
        long albumId = song.getAlbumId();
        long duration = song.getDuration();
        long dateAdded = song.getDateAdded();
        String mimeType = "";
        String filePath = "";

        // Query MediaStore
        ContentResolver resolver = context.getContentResolver();
        try (Cursor c = resolver.query(songUri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int titleIdx = c.getColumnIndex(MediaStore.Audio.Media.TITLE);
                if (titleIdx >= 0 && !c.isNull(titleIdx)) title = c.getString(titleIdx);

                int artistIdx = c.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                if (artistIdx >= 0 && !c.isNull(artistIdx)) artist = c.getString(artistIdx);

                int albumIdx = c.getColumnIndex(MediaStore.Audio.Media.ALBUM);
                if (albumIdx >= 0 && !c.isNull(albumIdx)) album = c.getString(albumIdx);

                int yearIdx = c.getColumnIndex(MediaStore.Audio.Media.YEAR);
                if (yearIdx >= 0 && !c.isNull(yearIdx) && year == 0) year = c.getInt(yearIdx);

                int trackIdx = c.getColumnIndex(MediaStore.Audio.Media.TRACK);
                if (trackIdx >= 0 && !c.isNull(trackIdx) && trackNumber == 0) trackNumber = c.getInt(trackIdx);

                int dataIdx = c.getColumnIndex(MediaStore.Audio.Media.DATA);
                if (dataIdx >= 0 && !c.isNull(dataIdx)) filePath = c.getString(dataIdx);

                int mimeIdx = c.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);
                if (mimeIdx >= 0 && !c.isNull(mimeIdx)) mimeType = c.getString(mimeIdx);

                int compIdx = c.getColumnIndex(MediaStore.Audio.Media.COMPOSER);
                if (compIdx >= 0 && !c.isNull(compIdx)) composer = c.getString(compIdx);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    int genreIdx = c.getColumnIndex(MediaStore.Audio.Media.GENRE);
                    if (genreIdx >= 0 && !c.isNull(genreIdx)) genre = c.getString(genreIdx);

                    int albArtIdx = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST);
                    if (albArtIdx >= 0 && !c.isNull(albArtIdx)) albumArtist = c.getString(albArtIdx);

                    int discIdx = c.getColumnIndex(MediaStore.Audio.Media.DISC_NUMBER);
                    if (discIdx >= 0 && !c.isNull(discIdx)) {
                        try {
                            String discStr = c.getString(discIdx);
                            if (discStr != null) {
                                int slash = discStr.indexOf('/');
                                if (slash > 0) discStr = discStr.substring(0, slash);
                                discNumber = Integer.parseInt(discStr.trim());
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error querying MediaStore columns for song " + songId, e);
        }

        // Secondary fallback: MediaMetadataRetriever to extract embedded tags
        try (MediaMetadataRetriever mmr = new MediaMetadataRetriever()) {
            mmr.setDataSource(context, songUri);

            if (genre == null || genre.isEmpty()) {
                String g = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
                if (g != null) genre = g;
            }
            if (composer == null || composer.isEmpty()) {
                String comp = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER);
                if (comp != null) composer = comp;
            }
            if (albumArtist == null || albumArtist.isEmpty()) {
                String aa = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
                if (aa != null) albumArtist = aa;
            }
            if (discNumber == 0) {
                String disc = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER);
                if (disc != null) {
                    try {
                        int slash = disc.indexOf('/');
                        if (slash > 0) disc = disc.substring(0, slash);
                        discNumber = Integer.parseInt(disc.trim());
                    } catch (Exception ignored) {}
                }
            }
            if (year == 0) {
                String date = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR);
                if (date == null) date = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE);
                if (date != null && date.length() >= 4) {
                    try {
                        year = Integer.parseInt(date.substring(0, 4));
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            // MediaMetadataRetriever may throw on unsupported files; safe to ignore
        }

        return new SongMetadata.Builder()
                .setSongId(songId)
                .setTitle(title)
                .setArtist(artist)
                .setAlbum(album)
                .setAlbumArtist(albumArtist)
                .setGenre(genre)
                .setYear(year)
                .setTrackNumber(trackNumber)
                .setDiscNumber(discNumber)
                .setComposer(composer)
                .setComment(comment)
                .setAlbumId(albumId)
                .setDuration(duration)
                .setDateAdded(dateAdded)
                .setMimeType(mimeType)
                .setFilePath(filePath)
                .setContentUri(songUri)
                .build();
    }
}
