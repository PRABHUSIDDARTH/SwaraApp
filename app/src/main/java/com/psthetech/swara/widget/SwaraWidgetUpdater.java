package com.psthetech.swara.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.widget.RemoteViews;

import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.MainActivity;

import java.io.FileDescriptor;

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
        ComponentName provider   = new ComponentName(context, SwaraWidgetProvider.class);
        int[] widgetIds          = manager.getAppWidgetIds(provider);

        if (widgetIds == null || widgetIds.length == 0) return; // no widgets placed

        RemoteViews views = buildViews(context, song, isPlaying);
        manager.updateAppWidget(widgetIds, views);
    }

    /**
     * Build the RemoteViews for the given playback state.
     * Called by both pushUpdate() and SwaraWidgetProvider.onUpdate().
     */
    static RemoteViews buildViews(Context context, Song song, boolean isPlaying) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_swara_player);

        // ── Text ──────────────────────────────────────────────────────────────
        String title  = (song != null) ? song.getTitle()  : context.getString(R.string.widget_no_song);
        String artist = (song != null) ? song.getArtist() : "";

        views.setTextViewText(R.id.widget_song_title,  title);
        views.setTextViewText(R.id.widget_artist_name, artist);

        // ── Album Art ─────────────────────────────────────────────────────────
        Bitmap art = null;
        if (song != null) {
            art = loadArtworkBitmap(context, song);
        }
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
            return BitmapFactory.decodeFileDescriptor(fd, null, opts);
        } catch (Exception e) {
            return null;
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
