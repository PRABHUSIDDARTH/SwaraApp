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
import com.psthetech.swara.R;
import com.psthetech.swara.data.repository.ArtworkRepository;
import com.psthetech.swara.domain.model.Song;

/**
 * Glide-backed artwork loading helper.
 *
 * Handles all artwork display throughout the app:
 * - Song list thumbnails (48dp)
 * - Album card thumbnails (140dp)
 * - Artist card thumbnails (90dp)
 * - Mini-player thumbnail (48dp)
 * - Now Playing large artwork (280dp)
 *
 * All image loading is asynchronous. The fallback drawable is shown instantly
 * while Glide resolves and decodes artwork in the background.
 */
public class ArtworkHelper {

    private ArtworkHelper() {} // Static utility

    public static Uri getAlbumArtUri(long albumId) {
        return ArtworkRepository.getAlbumArtUri(albumId);
    }

    private static final RequestOptions THUMBNAIL_OPTIONS = new RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .error(R.drawable.ic_artwork_fallback)
            .placeholder(R.drawable.ic_artwork_fallback)
            .centerCrop();

    private static final RequestOptions LARGE_OPTIONS = new RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .error(R.drawable.ic_artwork_fallback)
            .placeholder(R.drawable.ic_artwork_fallback)
            .centerCrop();

    /**
     * Loads song artwork into an ImageView (for song list items, mini-player, etc.)
     */
    public static void loadSongArt(Context context, Song song, ImageView into) {
        Uri uri = new ArtworkRepository(context).getArtworkUri(song);
        Glide.with(context)
                .load(uri)
                .apply(THUMBNAIL_OPTIONS)
                .into(into);
    }

    /**
     * Loads artwork by albumId (for album cards, artist thumbnails)
     */
    public static void loadAlbumArt(Context context, long albumId, ImageView into) {
        Uri uri = new ArtworkRepository(context).getAlbumArtUri(albumId);
        Glide.with(context)
                .load(uri)
                .apply(THUMBNAIL_OPTIONS)
                .into(into);
    }

    /**
     * Loads the large Now Playing artwork. Uses a slightly longer crossfade.
     */
    public static void loadNowPlayingArt(Context context, Song song, ImageView into) {
        Uri uri = new ArtworkRepository(context).getArtworkUri(song);
        Glide.with(context)
                .load(uri)
                .apply(LARGE_OPTIONS)
                .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
                        .withCrossFade(350))
                .into(into);
    }

    /**
     * Loads artwork and delivers the Drawable for custom use (e.g., palette extraction).
     * Returns via callback on the main thread.
     */
    public static void loadArtworkDrawable(Context context, Song song,
                                           @Nullable ArtworkCallback callback) {
        Uri uri = new ArtworkRepository(context).getArtworkUri(song);
        Glide.with(context)
                .load(uri)
                .apply(LARGE_OPTIONS)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                               Target<Drawable> target, boolean isFirstResource) {
                        if (callback != null) callback.onFailed();
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model,
                                                  Target<Drawable> target,
                                                  DataSource dataSource, boolean isFirstResource) {
                        if (callback != null) callback.onLoaded(resource);
                        return false;
                    }
                })
                .preload();
    }

    /** Clears Glide load for a view (call in RecyclerView.onViewRecycled) */
    public static void clear(Context context, ImageView view) {
        Glide.with(context).clear(view);
    }

    public interface ArtworkCallback {
        void onLoaded(Drawable drawable);
        void onFailed();
    }
}
