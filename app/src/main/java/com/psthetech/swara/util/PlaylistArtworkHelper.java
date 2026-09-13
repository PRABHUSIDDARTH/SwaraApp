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

        // Priority 1: Custom artwork
        if (store.hasCustomArtwork(playlistId)) {
            Uri customUri = store.getCustomArtworkUri(playlistId);
            ObjectKey sig = signatureForFile(store.getCustomArtworkFile(playlistId));
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
        final PlaylistArtworkStore finalStore = store;
        if (songAlbumIds != null && !songAlbumIds.isEmpty()) {
            generateCollageAsync(ctx, playlistId, finalStore, songAlbumIds, () -> {
                into.post(() -> {
                    Object tag = into.getTag(R.id.ivPlaylistArtwork);
                    if (tag instanceof Long && (Long) tag == playlistId) {
                        if (finalStore.hasCollage(playlistId)) {
                            ObjectKey sig = signatureForFile(collageFile);
                            Glide.with(ctx)
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
        } else if (songAlbumIds == null) {
            COLLAGE_EXECUTOR.execute(() -> {
                List<Long> albumIds = new ArrayList<>();
                try {
                    List<com.psthetech.swara.data.db.entity.PlaylistSong> songs =
                            com.psthetech.swara.data.db.AppDatabase.getInstance(ctx.getApplicationContext())
                                    .playlistDao()
                                    .getPlaylistSongs(playlistId);
                    if (songs != null) {
                        for (com.psthetech.swara.data.db.entity.PlaylistSong s : songs) {
                            albumIds.add(s.albumId);
                        }
                    }
                } catch (Exception e) {
                    // Fallback to empty if DB query fails
                }
                if (!albumIds.isEmpty()) {
                    generateCollageAsync(ctx, playlistId, finalStore, albumIds, () -> {
                        into.post(() -> {
                            Object tag = into.getTag(R.id.ivPlaylistArtwork);
                            if (tag instanceof Long && (Long) tag == playlistId) {
                                if (finalStore.hasCollage(playlistId)) {
                                    ObjectKey sig = signatureForFile(collageFile);
                                    Glide.with(ctx)
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
                } else {
                    into.post(() -> {
                        Object tag = into.getTag(R.id.ivPlaylistArtwork);
                        if (tag instanceof Long && (Long) tag == playlistId) {
                            into.setImageResource(R.drawable.ic_playlist);
                        }
                    });
                }
            });
        } else {
            into.setImageResource(R.drawable.ic_playlist);
        }
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

        // Use up to 4 unique album IDs for the collage
        List<Long> uniqueIds = new ArrayList<>();
        for (Long id : albumIds) {
            if (id != null && id > 0 && !uniqueIds.contains(id)) {
                uniqueIds.add(id);
                if (uniqueIds.size() == 4) break;
            }
        }
        List<Long> collageIds = new ArrayList<>(uniqueIds);

        COLLAGE_EXECUTOR.execute(() -> {
            try {
                int count = collageIds.size();
                if (count == 0) {
                    if (onComplete != null) onComplete.run();
                    return;
                }

                // Load bitmaps synchronously (we are on a background thread)
                List<Bitmap> bitmaps = new ArrayList<>();
                int cellSize = COLLAGE_SIZE_PX / 2;

                for (Long albumId : collageIds) {
                    Uri uri = ArtworkRepository.getAlbumArtUri(albumId);
                    if (uri == null) continue;
                    try {
                        Bitmap bmp = Glide.with(ctx)
                                .asBitmap()
                                .load(uri)
                                .apply(new RequestOptions()
                                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                                        .centerCrop()
                                        .override(cellSize, cellSize))
                                .submit(cellSize, cellSize)
                                .get();
                        if (bmp != null) bitmaps.add(bmp);
                    } catch (Exception e) {
                        // Skip failed album art, continue with others
                    }
                }

                if (bitmaps.isEmpty()) {
                    if (onComplete != null) onComplete.run();
                    return;
                }

                Bitmap collage = Bitmap.createBitmap(COLLAGE_SIZE_PX, COLLAGE_SIZE_PX,
                        Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(collage);

                if (bitmaps.size() == 1) {
                    // Single album art — fill the whole canvas
                    Bitmap scaled = Bitmap.createScaledBitmap(bitmaps.get(0),
                            COLLAGE_SIZE_PX, COLLAGE_SIZE_PX, true);
                    canvas.drawBitmap(scaled, 0, 0, null);
                } else {
                    // 2×2 grid — fill with up to 4 bitmaps, repeating if fewer than 4
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
        });
    }

    // ===== Helpers =====

    /** Glide cache signature derived from file modification time. */
    private static ObjectKey signatureForFile(File file) {
        return new ObjectKey(file.exists() ? file.lastModified() : 0L);
    }
}
