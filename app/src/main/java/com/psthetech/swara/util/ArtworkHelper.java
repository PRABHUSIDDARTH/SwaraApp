package com.psthetech.swara.util;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.ObjectKey;
import com.psthetech.swara.R;
import com.psthetech.swara.data.repository.ArtworkRepository;
import com.psthetech.swara.domain.model.Song;

import java.io.File;

/**
 * Glide-backed artwork loading helper enforcing the centralized artwork pipeline.
 *
 * Priority Chain:
 *  1. User-custom artwork override (from CustomArtworkStore)
 *  2. Embedded artwork from audio file (via song content URI)
 *  3. MediaStore album artwork (via album content URI)
 *  4. Cached previously-resolved artwork (Glide disk & memory cache)
 *  5. Swara fallback artwork (ic_artwork_fallback)
 *
 * Async loading with bounds downsampling and crossfade animations.
 */
public class ArtworkHelper {

    private ArtworkHelper() {} // Static utility

    private static final RequestOptions THUMBNAIL_OPTIONS = new RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.ic_artwork_fallback)
            .error(R.drawable.ic_artwork_fallback)
            .centerCrop();

    private static final RequestOptions LARGE_OPTIONS = new RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.ic_artwork_fallback)
            .error(R.drawable.ic_artwork_fallback)
            .centerCrop();

    /**
     * Helper to retrieve album art URI for a given albumId.
     */
    public static Uri getAlbumArtUri(long albumId) {
        return ArtworkRepository.getAlbumArtUri(albumId);
    }

    /**
     * Returns a cache signature key for a song, accounting for custom artwork modification times.
     */
    private static ObjectKey getSongSignature(Context context, Song song) {
        if (song == null) return new ObjectKey("null_song");
        ArtworkRepository repo = new ArtworkRepository(context);
        File customFile = repo.getCustomArtworkStore().getArtworkFile(song.getId());
        long time = customFile.exists() ? customFile.lastModified() : 0L;
        return new ObjectKey(song.getId() + "_" + song.getAlbumId() + "_" + time);
    }

    /**
     * Loads song artwork into an ImageView (for song lists, queue, mini-player, search).
     */
    public static void loadSongArt(Context context, @Nullable Song song, ImageView into) {
        if (context == null || into == null) return;
        if (song == null) {
            into.setImageResource(R.drawable.ic_artwork_fallback);
            return;
        }

        ArtworkRepository repo = new ArtworkRepository(context);
        Uri primaryUri = repo.getArtworkUri(song);
        Uri albumArtUri = ArtworkRepository.getAlbumArtUri(song.getAlbumId());
        ObjectKey signature = getSongSignature(context, song);

        if (primaryUri == null) {
            into.setImageResource(R.drawable.ic_artwork_fallback);
            return;
        }

        // Primary: Custom or Embedded song Uri
        // Fallback: Album Art Uri -> ic_artwork_fallback drawable
        if (albumArtUri != null && !primaryUri.equals(albumArtUri)) {
            Glide.with(context)
                    .load(primaryUri)
                    .signature(signature)
                    .apply(THUMBNAIL_OPTIONS)
                    .error(Glide.with(context)
                            .load(albumArtUri)
                            .signature(signature)
                            .apply(THUMBNAIL_OPTIONS)
                            .error(R.drawable.ic_artwork_fallback))
                    .into(into);
        } else {
            Glide.with(context)
                    .load(primaryUri)
                    .signature(signature)
                    .apply(THUMBNAIL_OPTIONS)
                    .into(into);
        }
    }

    /**
     * Loads artwork by albumId (for album cards, artist cards).
     */
    public static void loadAlbumArt(Context context, long albumId, ImageView into) {
        if (context == null || into == null) return;
        Uri uri = ArtworkRepository.getAlbumArtUri(albumId);
        if (uri == null) {
            into.setImageResource(R.drawable.ic_artwork_fallback);
            return;
        }
        Glide.with(context)
                .load(uri)
                .apply(THUMBNAIL_OPTIONS)
                .into(into);
    }

    /**
     * Loads large Now Playing artwork with crossfade transition.
     */
    public static void loadNowPlayingArt(Context context, @Nullable Song song, ImageView into) {
        if (context == null || into == null) return;
        if (song == null) {
            into.setImageResource(R.drawable.ic_artwork_fallback);
            return;
        }

        ArtworkRepository repo = new ArtworkRepository(context);
        Uri primaryUri = repo.getArtworkUri(song);
        Uri albumArtUri = ArtworkRepository.getAlbumArtUri(song.getAlbumId());
        ObjectKey signature = getSongSignature(context, song);

        if (primaryUri == null) {
            into.setImageResource(R.drawable.ic_artwork_fallback);
            return;
        }

        if (albumArtUri != null && !primaryUri.equals(albumArtUri)) {
            Glide.with(context)
                    .load(primaryUri)
                    .signature(signature)
                    .apply(LARGE_OPTIONS)
                    .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
                            .withCrossFade(300))
                    .error(Glide.with(context)
                            .load(albumArtUri)
                            .signature(signature)
                            .apply(LARGE_OPTIONS)
                            .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
                                    .withCrossFade(300))
                            .error(R.drawable.ic_artwork_fallback))
                    .into(into);
        } else {
            Glide.with(context)
                    .load(primaryUri)
                    .signature(signature)
                    .apply(LARGE_OPTIONS)
                    .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
                            .withCrossFade(300))
                    .into(into);
        }
    }

    /**
     * Clears load for recycled views.
     */
    public static void clear(Context context, ImageView view) {
        if (context != null && view != null) {
            Glide.with(context).clear(view);
        }
    }

    /**
     * Clears Glide memory cache when artwork changes.
     */
    public static void notifyArtworkChanged(Context context) {
        if (context != null) {
            Glide.get(context).clearMemory();
        }
    }

    public interface ArtworkCallback {
        void onLoaded(Drawable drawable);
        void onFailed();
    }
}
