package com.psthetech.swara.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.widget.RemoteViews;

import androidx.exifinterface.media.ExifInterface;

import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.MainActivity;

import java.io.FileDescriptor;
import java.io.InputStream;

/**
 * Static utility that builds the widget's RemoteViews and pushes them to the
 * AppWidgetManager.
 *
 * Called by SwaraPlaybackService whenever playback state changes (track switch,
 * play/pause toggle). No polling — the service drives all updates.
 *
 * RemoteViews restrictions:
 *   - Cannot use Glide or Picasso — those require a running Activity/Fragment context.
 *   - Album art must be decoded synchronously from a ContentResolver URI into a Bitmap
 *     and set via RemoteViews.setImageViewBitmap().
 *   - All work is done on the calling thread; the service calls this from its
 *     Player.Listener which runs on the main thread — keep art loading lightweight.
 *     For large images a background thread (AsyncTask/Executor) could be added, but
 *     ContentResolver thumbnail calls are fast enough in practice.
 */
public final class SwaraWidgetUpdater {

    // ── Broadcast action constants (shared with SwaraWidgetReceiver) ──────────
    public static final String ACTION_PLAY_PAUSE   = "com.psthetech.swara.ACTION_PLAY_PAUSE";
    public static final String ACTION_NEXT         = "com.psthetech.swara.ACTION_NEXT";
    public static final String ACTION_PREV         = "com.psthetech.swara.ACTION_PREV";
    public static final String ACTION_WIDGET_UPDATE = "com.psthetech.swara.ACTION_WIDGET_UPDATE";

    // Extra keys carried on the update broadcast (used by SwaraPlaybackService)
    public static final String EXTRA_SONG_TITLE    = "extra_song_title";
    public static final String EXTRA_ARTIST_NAME   = "extra_artist_name";
    public static final String EXTRA_ARTWORK_URI   = "extra_artwork_uri";
    public static final String EXTRA_IS_PLAYING    = "extra_is_playing";
    public static final String EXTRA_SONG_ID       = "extra_song_id";

    private SwaraWidgetUpdater() { /* static only */ }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Push a full widget refresh to every active Swara widget on the home screen.
     *
     * @param context   Application or service context.
     * @param song      Currently playing song, or null if nothing is playing.
     * @param isPlaying True if music is actively playing.
     */
    public static void pushUpdate(Context context, Song song, boolean isPlaying) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName standardProvider = new ComponentName(context, SwaraWidgetProvider.class);
        ComponentName verticalProvider = new ComponentName(context, SwaraVerticalWidgetProvider.class);

        int[] standardIds = manager.getAppWidgetIds(standardProvider);
        int[] verticalIds = manager.getAppWidgetIds(verticalProvider);

        boolean hasStandard = (standardIds != null && standardIds.length > 0);
        boolean hasVertical = (verticalIds != null && verticalIds.length > 0);

        if (!hasStandard && !hasVertical) return; // no widgets placed

        Bitmap art = (song != null) ? loadArtworkBitmap(context, song) : null;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            RemoteViews responsiveViews = buildResponsiveViews(context, song, isPlaying, art);
            if (hasStandard) {
                manager.updateAppWidget(standardIds, responsiveViews);
            }
            if (hasVertical) {
                manager.updateAppWidget(verticalIds, responsiveViews);
            }
        } else {
            if (hasStandard) {
                for (int id : standardIds) {
                    pushSingleWidgetUpdate(context, manager, id, song, isPlaying, art, R.layout.widget_swara_player);
                }
            }
            if (hasVertical) {
                for (int id : verticalIds) {
                    pushSingleWidgetUpdate(context, manager, id, song, isPlaying, art, R.layout.widget_swara_player_vertical);
                }
            }
        }
    }

    /**
     * Build the RemoteViews for the given playback state.
     * Called by SwaraWidgetProvider.onUpdate().
     */
    public static RemoteViews buildViews(Context context, Song song, boolean isPlaying) {
        Bitmap art = (song != null) ? loadArtworkBitmap(context, song) : null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return buildResponsiveViews(context, song, isPlaying, art);
        }
        return buildSingleView(context, song, isPlaying, R.layout.widget_swara_player, art);
    }

    /**
     * Build responsive RemoteViews on Android 12+ that automatically switch layouts
     * based on widget size on the home screen.
     */
    public static RemoteViews buildResponsiveViews(Context context, Song song, boolean isPlaying, Bitmap art) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            java.util.Map<android.util.SizeF, RemoteViews> viewMapping = new android.util.ArrayMap<>();
            // Vertical / square mode for taller or compact widgets
            viewMapping.put(new android.util.SizeF(110f, 130f),
                    buildSingleView(context, song, isPlaying, R.layout.widget_swara_player_vertical, art));
            // Horizontal bar mode for wider widgets
            viewMapping.put(new android.util.SizeF(180f, 80f),
                    buildSingleView(context, song, isPlaying, R.layout.widget_swara_player, art));
            return new RemoteViews(viewMapping);
        }
        return buildSingleView(context, song, isPlaying, R.layout.widget_swara_player, art);
    }

    /**
     * Update a single widget instance (e.g. on resize or placement).
     */
    public static void pushSingleWidgetUpdate(Context context, AppWidgetManager manager, int widgetId,
                                              Song song, boolean isPlaying) {
        Bitmap art = (song != null) ? loadArtworkBitmap(context, song) : null;
        pushSingleWidgetUpdate(context, manager, widgetId, song, isPlaying, art, 0);
    }

    public static void pushSingleWidgetUpdate(Context context, AppWidgetManager manager, int widgetId,
                                              Song song, boolean isPlaying, Bitmap art, int defaultLayoutResId) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            manager.updateAppWidget(widgetId, buildResponsiveViews(context, song, isPlaying, art));
            return;
        }

        android.os.Bundle options = manager.getAppWidgetOptions(widgetId);
        int minWidth = options != null ? options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) : 0;
        int minHeight = options != null ? options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) : 0;

        int layoutRes = defaultLayoutResId != 0 ? defaultLayoutResId : R.layout.widget_swara_player;
        if (minHeight > 0 && minWidth > 0 && (minHeight >= 140 || minHeight >= minWidth)) {
            layoutRes = R.layout.widget_swara_player_vertical;
        }

        RemoteViews views = buildSingleView(context, song, isPlaying, layoutRes, art);
        manager.updateAppWidget(widgetId, views);
    }

    /**
     * Build RemoteViews for a specific layout XML resource.
     */
    public static RemoteViews buildSingleView(Context context, Song song, boolean isPlaying,
                                              int layoutResId, Bitmap art) {
        RemoteViews views = new RemoteViews(context.getPackageName(), layoutResId);

        // ── Text ──────────────────────────────────────────────────────────────
        String title  = (song != null) ? song.getTitle()  : context.getString(R.string.widget_no_song);
        String artist = (song != null) ? song.getArtist() : "";

        views.setTextViewText(R.id.widget_song_title,  title);
        views.setTextViewText(R.id.widget_artist_name, artist);

        // ── Album Art ─────────────────────────────────────────────────────────
        if (art != null) {
            views.setImageViewBitmap(R.id.widget_album_art, art);
        } else {
            views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_artwork_fallback);
        }

        // ── Play / Pause icon ─────────────────────────────────────────────────
        int playPauseIcon = isPlaying ? R.drawable.ic_pause : R.drawable.ic_play;
        views.setImageViewResource(R.id.widget_btn_play_pause, playPauseIcon);

        // ── PendingIntents for buttons ─────────────────────────────────────────
        views.setOnClickPendingIntent(R.id.widget_btn_play_pause, makeBroadcastPI(context, ACTION_PLAY_PAUSE, 1));
        views.setOnClickPendingIntent(R.id.widget_btn_prev,       makeBroadcastPI(context, ACTION_PREV,       2));
        views.setOnClickPendingIntent(R.id.widget_btn_next,       makeBroadcastPI(context, ACTION_NEXT,       3));

        // ── Tap widget body → open app ─────────────────────────────────────────
        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openAppPI = PendingIntent.getActivity(
                context, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, openAppPI);

        return views;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Load album art from the system MediaStore or Swara's custom artwork store.
     * Returns null if no art is available (caller will use the fallback drawable).
     *
     * We first try the custom artwork store (in-app overrides), then fall back to
     * the MediaStore album art thumbnail. Both paths use ContentResolver — safe from
     * a widget / service context.
     */
    private static Bitmap loadArtworkBitmap(Context context, Song song) {
        // 1. Try Swara's custom artwork override (stored in internal files dir)
        try {
            com.psthetech.swara.data.repository.CustomArtworkStore customStore =
                    new com.psthetech.swara.data.repository.CustomArtworkStore(context);
            Uri customUri = customStore.getCustomArtworkUri(song.getId());
            if (customUri != null) {
                Bitmap bmp = decodeBitmapFromUri(context, customUri);
                if (bmp != null) return bmp;
            }
        } catch (Exception ignored) { /* fall through */ }

        // 2. Fall back to MediaStore embedded / album art
        try {
            Uri artUri = com.psthetech.swara.data.repository.ArtworkRepository
                    .getAlbumArtUri(song.getAlbumId());
            if (artUri != null) {
                Bitmap bmp = decodeBitmapFromUri(context, artUri);
                if (bmp != null) return bmp;
            }
        } catch (Exception ignored) { /* fall through */ }

        return null;
    }

    /** Decode a Bitmap from a ContentResolver URI, returning null on failure. */
    private static Bitmap decodeBitmapFromUri(Context context, Uri uri) {
        try (ParcelFileDescriptor pfd = context.getContentResolver()
                .openFileDescriptor(uri, "r")) {
            if (pfd == null) return null;
            FileDescriptor fd = pfd.getFileDescriptor();
            // Sample down large art to ~256 px to keep widget memory sane
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFileDescriptor(fd, null, opts);
            opts.inSampleSize   = calculateInSampleSize(opts, 256, 256);
            opts.inJustDecodeBounds = false;
            Bitmap bmp = BitmapFactory.decodeFileDescriptor(fd, null, opts);
            if (bmp == null) return null;

            // Correct EXIF orientation (e.g. uploaded phone camera photos rotated left/right)
            int orientation = getExifOrientation(context, uri);
            return rotateBitmap(bmp, orientation);
        } catch (Exception e) {
            return null;
        }
    }

    private static int getExifOrientation(Context context, Uri uri) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return ExifInterface.ORIENTATION_NORMAL;
            ExifInterface exif = new ExifInterface(in);
            return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception e) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static Bitmap rotateBitmap(Bitmap bitmap, int orientation) {
        int degrees = 0;
        boolean flipHorizontal = false;
        boolean flipVertical = false;

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                degrees = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                degrees = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                degrees = 270;
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                flipHorizontal = true;
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                flipVertical = true;
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                degrees = 90;
                flipHorizontal = true;
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                degrees = 270;
                flipHorizontal = true;
                break;
            case ExifInterface.ORIENTATION_NORMAL:
            case ExifInterface.ORIENTATION_UNDEFINED:
            default:
                return bitmap;
        }

        Matrix matrix = new Matrix();
        if (degrees != 0) {
            matrix.postRotate(degrees);
        }
        if (flipHorizontal || flipVertical) {
            matrix.postScale(flipHorizontal ? -1 : 1, flipVertical ? -1 : 1);
        }

        try {
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
            }
            return rotated;
        } catch (OutOfMemoryError | Exception e) {
            return bitmap;
        }
    }

    /** Calculate power-of-2 inSampleSize to fit within reqWidth × reqHeight. */
    private static int calculateInSampleSize(BitmapFactory.Options opts,
                                             int reqWidth, int reqHeight) {
        int h = opts.outHeight, w = opts.outWidth, inSampleSize = 1;
        if (h > reqHeight || w > reqWidth) {
            final int halfH = h / 2, halfW = w / 2;
            while ((halfH / inSampleSize) >= reqHeight
                    && (halfW / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /** Create a broadcast PendingIntent for a widget button action. */
    private static PendingIntent makeBroadcastPI(Context context, String action, int requestCode) {
        Intent intent = new Intent(action);
        intent.setClass(context, SwaraWidgetReceiver.class);
        return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
