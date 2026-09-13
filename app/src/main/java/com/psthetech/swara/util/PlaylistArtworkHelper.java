package com.psthetech.swara.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;
import com.psthetech.swara.R;
import com.psthetech.swara.data.db.entity.Playlist;
import com.psthetech.swara.data.repository.ArtworkRepository;
import com.psthetech.swara.data.repository.PlaylistArtworkStore;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Playlist artwork loading helper.
 *
 * Priority Chain (per playlist):
 *  1. User-custom artwork override (PlaylistArtworkStore.getCustomArtworkUri)
 *  2. Cached 2×2 album art collage (PlaylistArtworkStore.getCollageFile)
 *  3. ic_artwork_fallback drawable
 *
 * Collage generation:
 *  - Triggered when no custom and no cached collage exists.
 *  - Generated off the main thread via a single-thread executor.
 *  - Written to PlaylistArtworkStore.getCollageFile(playlistId) as a JPEG.
 *  - After generation, Glide loads from the collage file with a file-mtime signature
 *    (so it reloads automatically after invalidation via invalidateCollage()).
 *
 * All Glide loads use a signature key so that the cache is invalidated when artwork changes.
 */
public class PlaylistArtworkHelper {

    private PlaylistArtworkHelper() {} // Static utility

    private static final int COLLAGE_SIZE_PX = 512;
    private static final ExecutorService COLLAGE_EXECUTOR = Executors.newSingleThreadExecutor();

    private static final RequestOptions PLAYLIST_OPTIONS = new RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.ic_playlist)
            .error(R.drawable.ic_playlist)
            .centerCrop();

    // ===== Public API =====

    /**
     * Loads playlist artwork into the given ImageView following the priority chain.
     *
     * @param ctx           Android context
     * @param playlist      Playlist entity (must not be null)
     * @param store         PlaylistArtworkStore for this playlist
     * @param songAlbumIds  List of albumIds for songs in this playlist (used for collage generation)
     * @param into          Target ImageView
     */
    public static void loadPlaylistArt(
            Context ctx,
            @Nullable Playlist playlist,
            PlaylistArtworkStore store,
            ImageView into) {
        loadPlaylistArt(ctx, playlist, store, null, into);
    }

    public static void loadPlaylistArt(
            Context ctx,
            @Nullable Playlist playlist,
            PlaylistArtworkStore store,
            @Nullable List<Long> songAlbumIds,
            ImageView into) {

        if (ctx == null || into == null || playlist == null) {
            if (into != null) into.setImageResource(R.drawable.ic_playlist);
            return;
        }

        if (store == null) {
            store = new PlaylistArtworkStore(ctx);
        }

        long playlistId = playlist.id;
        into.setTag(R.id.ivPlaylistArtwork, playlistId);

        // Priority 1: Custom artwork override
        File customFile = store.getCustomArtworkFile(playlistId);
        boolean hasCustom = store.hasCustomArtwork(playlistId)
                || (playlist.artworkPath != null && new File(playlist.artworkPath).exists() && new File(playlist.artworkPath).length() > 0);
        if (hasCustom) {
            Uri customUri = store.getCustomArtworkUri(playlistId);
            if (customUri == null && playlist.artworkPath != null) {
                customFile = new File(playlist.artworkPath);
                customUri = Uri.fromFile(customFile);
            }
            ObjectKey sig = signatureForFile(customFile);
            Glide.with(ctx)
                    .load(customUri)
                    .signature(sig)
                    .apply(PLAYLIST_OPTIONS)
                    .into(into);
            return;
        }

        // Priority 2: Cached collage
        File collageFile = store.getCollageFile(playlistId);
        if (store.hasCollage(playlistId)) {
            ObjectKey sig = signatureForFile(collageFile);
            Glide.with(ctx)
                    .load(collageFile)
                    .signature(sig)
                    .apply(PLAYLIST_OPTIONS)
                    .into(into);
            return;
        }

        // Priority 3: Generate collage (async) → then load, or fallback if no songs
        final Context appContext = ctx.getApplicationContext();
        final PlaylistArtworkStore finalStore = store;
        COLLAGE_EXECUTOR.execute(() -> {
            List<com.psthetech.swara.data.db.entity.PlaylistSong> songs = null;
            try {
                songs = com.psthetech.swara.data.db.AppDatabase.getInstance(appContext)
                        .playlistDao()
                        .getPlaylistSongs(playlistId);
            } catch (Exception e) {
                // Ignore DB error
            }

            if (songs == null || songs.isEmpty()) {
                into.post(() -> {
                    Object tag = into.getTag(R.id.ivPlaylistArtwork);
                    if (tag instanceof Long && (Long) tag == playlistId) {
                        into.setImageResource(R.drawable.ic_playlist);
                    }
                });
                return;
            }

            generateCollageFromSongsInternal(appContext, playlistId, finalStore, songs, () -> {
                into.post(() -> {
                    Object tag = into.getTag(R.id.ivPlaylistArtwork);
                    if (tag instanceof Long && (Long) tag == playlistId) {
                        if (finalStore.hasCollage(playlistId)) {
                            ObjectKey sig = signatureForFile(finalStore.getCollageFile(playlistId));
                            Glide.with(appContext)
                                    .load(finalStore.getCollageFile(playlistId))
                                    .signature(sig)
                                    .apply(PLAYLIST_OPTIONS)
                                    .into(into);
                        } else {
                            into.setImageResource(R.drawable.ic_playlist);
                        }
                    }
                });
            });
        });
    }

    /**
     * Clears the Glide load and playlist tag for a recycled view.
     */
    public static void clear(Context ctx, ImageView view) {
        if (view != null) {
            view.setTag(R.id.ivPlaylistArtwork, null);
            if (ctx != null) {
                Glide.with(ctx).clear(view);
            }
        }
    }

    /**
     * Invalidates the collage cache and notifies Glide memory cache.
     * Call when songs are added/removed from a playlist.
     */
    public static void invalidatePlaylistCollage(Context ctx, long playlistId, PlaylistArtworkStore store) {
        if (store != null) store.invalidateCollage(playlistId);
        if (ctx != null) Glide.get(ctx).clearMemory();
    }

    // ===== Collage Generation =====

    private static void generateCollageFromSongsInternal(
            Context appContext,
            long playlistId,
            PlaylistArtworkStore store,
            List<com.psthetech.swara.data.db.entity.PlaylistSong> songs,
            Runnable onComplete) {

        // Select up to 4 unique songs
        List<com.psthetech.swara.data.db.entity.PlaylistSong> uniqueSongs = new ArrayList<>();
        List<Long> seenSongIds = new ArrayList<>();
        for (com.psthetech.swara.data.db.entity.PlaylistSong s : songs) {
            if (s != null && s.songId > 0 && !seenSongIds.contains(s.songId)) {
                seenSongIds.add(s.songId);
                uniqueSongs.add(s);
                if (uniqueSongs.size() == 4) break;
            }
        }

        try {
            if (uniqueSongs.isEmpty()) {
                if (onComplete != null) onComplete.run();
                return;
            }

            List<Bitmap> bitmaps = new ArrayList<>();
            int cellSize = COLLAGE_SIZE_PX / 2;

            for (com.psthetech.swara.data.db.entity.PlaylistSong s : uniqueSongs) {
                Bitmap bmp = null;
                // Try embedded artwork from audio content URI first (supported on Android 10, 11, 12, 13, 14)
                if (s.songId > 0) {
                    Uri songUri = ArtworkRepository.getSongUri(s.songId);
                    if (songUri != null) {
                        try {
                            bmp = Glide.with(appContext)
                                    .asBitmap()
                                    .load(songUri)
                                    .apply(new RequestOptions()
                                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                                            .centerCrop()
                                            .override(cellSize, cellSize))
                                    .submit(cellSize, cellSize)
                                    .get();
                        } catch (Exception ignored) {}
                    }
                }
                // Fallback to MediaStore album artwork URI if song content URI failed
                if (bmp == null && s.albumId > 0) {
                    Uri albumUri = ArtworkRepository.getAlbumArtUri(s.albumId);
                    if (albumUri != null) {
                        try {
                            bmp = Glide.with(appContext)
                                    .asBitmap()
                                    .load(albumUri)
                                    .apply(new RequestOptions()
                                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                                            .centerCrop()
                                            .override(cellSize, cellSize))
                                    .submit(cellSize, cellSize)
                                    .get();
                        } catch (Exception ignored) {}
                    }
                }
                if (bmp != null) bitmaps.add(bmp);
            }

            if (bitmaps.isEmpty()) {
                if (onComplete != null) onComplete.run();
                return;
            }

            Bitmap collage = Bitmap.createBitmap(COLLAGE_SIZE_PX, COLLAGE_SIZE_PX, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(collage);

            if (bitmaps.size() == 1) {
                Bitmap scaled = Bitmap.createScaledBitmap(bitmaps.get(0), COLLAGE_SIZE_PX, COLLAGE_SIZE_PX, true);
                canvas.drawBitmap(scaled, 0, 0, null);
            } else {
                int[][] positions = {
                        {0, 0}, {cellSize, 0}, {0, cellSize}, {cellSize, cellSize}
                };
                for (int i = 0; i < 4; i++) {
                    Bitmap src = bitmaps.get(i % bitmaps.size());
                    Bitmap cell = Bitmap.createScaledBitmap(src, cellSize, cellSize, true);
                    canvas.drawBitmap(cell, positions[i][0], positions[i][1], null);
                    if (!cell.isRecycled()) cell.recycle();
                }
            }

            store.saveCollageArtwork(playlistId, collage);
            collage.recycle();

        } catch (Exception e) {
            android.util.Log.e("PlaylistArtworkHelper", "Collage generation failed", e);
        }

        if (onComplete != null) onComplete.run();
    }

    /**
     * Generates a 2×2 album art collage asynchronously and saves it to the store.
     * onComplete is called on a background thread after the file is written.
     */
    static void generateCollageAsync(
            Context ctx,
            long playlistId,
            PlaylistArtworkStore store,
            List<Long> albumIds,
            Runnable onComplete) {

        final Context appContext = ctx.getApplicationContext();
        COLLAGE_EXECUTOR.execute(() -> {
            List<com.psthetech.swara.data.db.entity.PlaylistSong> songs = null;
            try {
                songs = com.psthetech.swara.data.db.AppDatabase.getInstance(appContext)
                        .playlistDao()
                        .getPlaylistSongs(playlistId);
            } catch (Exception ignored) {}

            if (songs != null && !songs.isEmpty()) {
                generateCollageFromSongsInternal(appContext, playlistId, store, songs, onComplete);
                return;
            }

            // Fallback for legacy calls with only albumIds
            List<Long> uniqueIds = new ArrayList<>();
            if (albumIds != null) {
                for (Long id : albumIds) {
                    if (id != null && id > 0 && !uniqueIds.contains(id)) {
                        uniqueIds.add(id);
                        if (uniqueIds.size() == 4) break;
                    }
                }
            }

            try {
                if (uniqueIds.isEmpty()) {
                    if (onComplete != null) onComplete.run();
                    return;
                }

                List<Bitmap> bitmaps = new ArrayList<>();
                int cellSize = COLLAGE_SIZE_PX / 2;

                for (Long albumId : uniqueIds) {
                    Uri uri = ArtworkRepository.getAlbumArtUri(albumId);
                    if (uri == null) continue;
                    try {
                        Bitmap bmp = Glide.with(appContext)
                                .asBitmap()
                                .load(uri)
                                .apply(new RequestOptions()
                                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                                        .centerCrop()
                                        .override(cellSize, cellSize))
                                .submit(cellSize, cellSize)
                                .get();
                        if (bmp != null) bitmaps.add(bmp);
                    } catch (Exception ignored) {}
                }

                if (!bitmaps.isEmpty()) {
                    Bitmap collage = Bitmap.createBitmap(COLLAGE_SIZE_PX, COLLAGE_SIZE_PX, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(collage);
                    if (bitmaps.size() == 1) {
                        Bitmap scaled = Bitmap.createScaledBitmap(bitmaps.get(0), COLLAGE_SIZE_PX, COLLAGE_SIZE_PX, true);
                        canvas.drawBitmap(scaled, 0, 0, null);
                    } else {
                        int[][] positions = {{0, 0}, {cellSize, 0}, {0, cellSize}, {cellSize, cellSize}};
                        for (int i = 0; i < 4; i++) {
                            Bitmap src = bitmaps.get(i % bitmaps.size());
                            Bitmap cell = Bitmap.createScaledBitmap(src, cellSize, cellSize, true);
                            canvas.drawBitmap(cell, positions[i][0], positions[i][1], null);
                            if (!cell.isRecycled()) cell.recycle();
                        }
                    }
                    store.saveCollageArtwork(playlistId, collage);
                    collage.recycle();
                }
            } catch (Exception e) {
                android.util.Log.e("PlaylistArtworkHelper", "Collage generation failed", e);
            }

            if (onComplete != null) onComplete.run();
        });
    }

    // ===== Helpers =====

    /** Glide cache signature derived from file modification time. */
    private static ObjectKey signatureForFile(File file) {
        return new ObjectKey(file.exists() ? file.lastModified() : 0L);
    }
}
