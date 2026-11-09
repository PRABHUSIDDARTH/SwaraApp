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

import androidx.annotation.OptIn;
import androidx.exifinterface.media.ExifInterface;
import androidx.media3.common.util.UnstableApi;

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
    public static final String ACTION_SEEK_FORWARD = "com.psthetech.swara.ACTION_SEEK_FORWARD";
    public static final String ACTION_SEEK_BACKWARD = "com.psthetech.swara.ACTION_SEEK_BACKWARD";
    public static final String ACTION_WIDGET_UPDATE = "com.psthetech.swara.ACTION_WIDGET_UPDATE";
    /** Jump to a fraction of the track; carries {@link #EXTRA_SEEK_FRACTION}. */
    public static final String ACTION_SEEK_TO      = "com.psthetech.swara.ACTION_SEEK_TO";
    public static final String ACTION_TOGGLE_FAVORITE = "com.psthetech.swara.ACTION_TOGGLE_FAVORITE";
    public static final String ACTION_TOGGLE_KOROKAE  = "com.psthetech.swara.ACTION_TOGGLE_KOROKAE";
    public static final String ACTION_TOGGLE_VIBE     = "com.psthetech.swara.ACTION_TOGGLE_VIBE";

    // Extra keys carried on the update broadcast (used by SwaraPlaybackService)
    public static final String EXTRA_SONG_TITLE    = "extra_song_title";
    public static final String EXTRA_ARTIST_NAME   = "extra_artist_name";
    public static final String EXTRA_ARTWORK_URI   = "extra_artwork_uri";
    public static final String EXTRA_IS_PLAYING    = "extra_is_playing";
    public static final String EXTRA_SONG_ID       = "extra_song_id";
    public static final String EXTRA_SEEK_FRACTION = "extra_seek_fraction";

    /** Number of tap targets along the seekbar (each jumps to its centre). */
    public static final int SEEK_ZONES = 10;
    private static final int[] SEEK_ZONE_IDS = {
            R.id.widget_seek_zone_0, R.id.widget_seek_zone_1, R.id.widget_seek_zone_2,
            R.id.widget_seek_zone_3, R.id.widget_seek_zone_4, R.id.widget_seek_zone_5,
            R.id.widget_seek_zone_6, R.id.widget_seek_zone_7, R.id.widget_seek_zone_8,
            R.id.widget_seek_zone_9
    };

    /**
     * Snapshot of everything the widget renders besides the song itself. Positions
     * come from the player; the widget ticks time on its own via Chronometer.
     */
    public static final class State {
        public final boolean isPlaying;
        public final long positionMs;
        public final long durationMs;
        public final boolean favorite;
        public final boolean korokae;
        public final boolean vibe;

        public State(boolean isPlaying, long positionMs, long durationMs, boolean favorite, boolean korokae) {
            this(isPlaying, positionMs, durationMs, favorite, korokae, false);
        }

        public State(boolean isPlaying, long positionMs, long durationMs, boolean favorite, boolean korokae,
                     boolean vibe) {
            this.isPlaying = isPlaying;
            this.positionMs = Math.max(0L, positionMs);
            this.durationMs = Math.max(0L, durationMs);
            this.favorite = favorite;
            this.korokae = korokae;
            this.vibe = vibe;
        }

        static State idle() { return new State(false, 0, 0, false, false); }

        float fraction() {
            return durationMs > 0 ? WidgetWaveformRenderer.clampProgress(positionMs / (float) durationMs) : 0f;
        }

        State withPlaying(boolean playing) {
            return new State(playing, positionMs, durationMs, favorite, korokae, vibe);
        }
    }

    // ── Artwork caching & async background decoding ──────────────────────────
    private static volatile Long cachedSongId = null;
    @androidx.annotation.Nullable private static volatile Bitmap cachedArtwork = null;
    private static final Object CACHE_LOCK = new Object();
    private static final java.util.concurrent.ExecutorService ARTWORK_EXECUTOR =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    // ── Last pushed state (theme changes / resizes re-render with it) ─────────
    @androidx.annotation.Nullable private static volatile Song lastSong = null;
    private static volatile State lastState = State.idle();
    /** Wall-clock time lastState.positionMs was sampled, to extrapolate on re-render. */
    private static volatile long lastStateAtElapsed = 0L;

    private SwaraWidgetUpdater() { /* static only */ }

    /** Clear cached artwork bitmap (e.g. for testing or memory pressure). */
    public static void clearArtworkCache() {
        synchronized (CACHE_LOCK) {
            cachedSongId = null;
            cachedArtwork = null;
        }
    }

    @androidx.annotation.Nullable
    static Song getLastSong() { return lastSong; }

    static boolean getLastIsPlaying() { return lastState.isPlaying; }

    /** Last state with the position advanced by the time spent playing since it was sampled. */
    static State currentState() {
        State s = lastState;
        if (!s.isPlaying) return s;
        long advanced = s.positionMs + (android.os.SystemClock.elapsedRealtime() - lastStateAtElapsed);
        if (s.durationMs > 0) advanced = Math.min(advanced, s.durationMs);
        return new State(true, advanced, s.durationMs, s.favorite, s.korokae, s.vibe);
    }

    /** True when at least one Swara widget is on the home screen. */
    public static boolean hasPlacedWidgets(Context context) {
        try {
            int[] ids = AppWidgetManager.getInstance(context)
                    .getAppWidgetIds(new ComponentName(context, SwaraWidgetProvider.class));
            return ids != null && ids.length > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // Rounded artwork tile cache: key = source bitmap identity; avoids re-rendering on each tick
    @androidx.annotation.Nullable private static Bitmap tileSource = null;
    @androidx.annotation.Nullable private static Bitmap tileCached = null;
    private static int placeholderKey = 0;
    @androidx.annotation.Nullable private static Bitmap placeholderCached = null;

    private static synchronized Bitmap artworkTile(Context context, @androidx.annotation.Nullable Bitmap art,
                                                   WidgetArt.Palette pal) {
        if (art != null && !art.isRecycled()) {
            if (art != tileSource || tileCached == null) {
                tileCached = WidgetArt.roundedSquare(art);
                tileSource = art;
            }
            return tileCached;
        }
        int key = 31 * pal.accent + pal.onAccent;
        if (placeholderCached == null || placeholderKey != key) {
            placeholderCached = WidgetArt.placeholder(context, pal.accent, pal.onAccent);
            placeholderKey = key;
        }
        return placeholderCached;
    }

    /** Re-render every placed widget with the last known song and state. */
    public static void refreshWithLastState(Context context) {
        if (context == null) return;
        pushUpdate(context.getApplicationContext(), lastSong, currentState());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /** Legacy entry point: keeps the last known position / favorite / Korokae. */
    public static void pushUpdate(Context context, Song song, boolean isPlaying) {
        State base = (song != null && lastSong != null && lastSong.getId() == song.getId())
                ? currentState() : State.idle();
        pushUpdate(context, song, base.withPlaying(isPlaying));
    }

    /** Legacy entry point with a progress ratio (no duration known). */
    public static void pushUpdate(Context context, Song song, boolean isPlaying, float progressRatio) {
        long dur = song != null ? song.getDuration() : 0L;
        long pos = (long) (WidgetWaveformRenderer.clampProgress(progressRatio) * dur);
        State prev = lastState;
        pushUpdate(context, song, new State(isPlaying, pos, dur, prev.favorite, prev.korokae, prev.vibe));
    }

    /** Push a full refresh to every placed Swara widget. */
    public static void pushUpdate(Context context, Song song, State state) {
        if (context == null) return;
        if (state == null) state = State.idle();
        lastSong = song;
        lastState = state;
        lastStateAtElapsed = android.os.SystemClock.elapsedRealtime();

        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, SwaraWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(provider);
        if (ids == null || ids.length == 0) return; // no widgets placed

        if (song == null) {
            clearArtworkCache();
            renderAndPush(context, manager, ids, null, state, null);
            return;
        }

        Bitmap artToUse = null;
        boolean needsBackgroundLoad;
        final long targetSongId = song.getId();
        synchronized (CACHE_LOCK) {
            if (cachedSongId != null && cachedSongId == targetSongId
                    && cachedArtwork != null && !cachedArtwork.isRecycled()) {
                artToUse = cachedArtwork;
                needsBackgroundLoad = false;
            } else {
                needsBackgroundLoad = true;
            }
        }

        // Immediate update so text / transport / seekbar respond without delay
        renderAndPush(context, manager, ids, song, state, artToUse);

        if (needsBackgroundLoad) {
            final Context appContext = context.getApplicationContext();
            final Song songSnapshot = song;
            final State stateSnapshot = state;
            ARTWORK_EXECUTOR.execute(() -> {
                Bitmap decoded = loadArtworkBitmap(appContext, songSnapshot);
                if (decoded == null) return; // placeholder already shown
                synchronized (CACHE_LOCK) {
                    cachedSongId = songSnapshot.getId();
                    cachedArtwork = decoded;
                }
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    // Skip if the track changed while decoding
                    Song now = lastSong;
                    if (now == null || now.getId() != songSnapshot.getId()) return;
                    int[] sIds = manager.getAppWidgetIds(provider);
                    if (sIds != null && sIds.length > 0) {
                        renderAndPush(appContext, manager, sIds, songSnapshot, currentState(), decoded);
                    }
                });
            });
        }
    }

    private static void renderAndPush(Context context, AppWidgetManager manager, int[] ids,
                                      Song song, State state, Bitmap art) {
        if (ids == null || ids.length == 0) return;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            manager.updateAppWidget(ids, buildResponsiveViews(context, song, state, art));
        } else {
            for (int id : ids) {
                manager.updateAppWidget(id, buildForOptions(context, manager.getAppWidgetOptions(id), song, state, art));
            }
        }
    }

    /**
     * Android 12+: one RemoteViews per size class; the launcher picks the best fit.
     * Strip (wide + short), compact square, and the full card.
     */
    public static RemoteViews buildResponsiveViews(Context context, Song song, State state, Bitmap art) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            java.util.Map<android.util.SizeF, RemoteViews> mapping = new android.util.ArrayMap<>();
            mapping.put(new android.util.SizeF(180f, 50f),
                    buildSingleView(context, song, state, R.layout.widget_swara_player_strip, art));
            mapping.put(new android.util.SizeF(110f, 150f),
                    buildSingleView(context, song, state, R.layout.widget_swara_player_vertical, art));
            mapping.put(new android.util.SizeF(240f, 150f),
                    buildSingleView(context, song, state, R.layout.widget_swara_player, art));
            return new RemoteViews(mapping);
        }
        return buildSingleView(context, song, state, R.layout.widget_swara_player, art);
    }

    /** Pre-12 layout choice from the widget's reported size. Package-private for tests. */
    static int layoutForSize(int minWidthDp, int minHeightDp) {
        if (minWidthDp <= 0 || minHeightDp <= 0) return R.layout.widget_swara_player;
        if (minHeightDp < 120) return R.layout.widget_swara_player_strip;
        if (minWidthDp < 230) return R.layout.widget_swara_player_vertical;
        return R.layout.widget_swara_player;
    }

    private static RemoteViews buildForOptions(Context context, @androidx.annotation.Nullable android.os.Bundle options,
                                               Song song, State state, Bitmap art) {
        int w = options != null ? options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) : 0;
        int h = options != null ? options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) : 0;
        return buildSingleView(context, song, state, layoutForSize(w, h), art);
    }

    /** Views for the provider's onUpdate (uses cached artwork only). */
    public static RemoteViews buildViews(Context context, Song song, boolean isPlaying) {
        Bitmap art = cachedArtFor(song);
        State state = (song != null && lastSong != null && lastSong.getId() == song.getId())
                ? currentState().withPlaying(isPlaying) : State.idle().withPlaying(isPlaying);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return buildResponsiveViews(context, song, state, art);
        }
        return buildSingleView(context, song, state, R.layout.widget_swara_player, art);
    }

    /** Update one widget instance (placement / resize) with the last known state. */
    public static void pushSingleWidgetUpdate(Context context, AppWidgetManager manager, int widgetId) {
        Song song = lastSong;
        State state = currentState();
        Bitmap art = cachedArtFor(song);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            manager.updateAppWidget(widgetId, buildResponsiveViews(context, song, state, art));
        } else {
            manager.updateAppWidget(widgetId, buildForOptions(context, manager.getAppWidgetOptions(widgetId), song, state, art));
        }
    }

    @androidx.annotation.Nullable
    private static Bitmap cachedArtFor(@androidx.annotation.Nullable Song song) {
        if (song == null) return null;
        synchronized (CACHE_LOCK) {
            if (cachedSongId != null && cachedSongId == song.getId()
                    && cachedArtwork != null && !cachedArtwork.isRecycled()) {
                return cachedArtwork;
            }
        }
        return null;
    }

    /** Back-compat overload used by older call sites. */
    public static RemoteViews buildSingleView(Context context, Song song, boolean isPlaying,
                                              int layoutResId, Bitmap art) {
        return buildSingleView(context, song, State.idle().withPlaying(isPlaying), layoutResId, art);
    }

    /** Build RemoteViews for one layout. */
    public static RemoteViews buildSingleView(Context context, Song song, State state,
                                              int layoutResId, Bitmap art) {
        RemoteViews views = new RemoteViews(context.getPackageName(), layoutResId);
        boolean hasSong = song != null;
        boolean full = layoutResId == R.layout.widget_swara_player;
        boolean compact = layoutResId == R.layout.widget_swara_player_vertical;

        com.psthetech.swara.ui.theme.DesignTokens tokens = null;
        try {
            tokens = com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance().getCurrentTokens();
        } catch (Exception ignored) { /* neutral fallback palette */ }
        WidgetArt.Palette pal = WidgetArt.Palette.from(tokens);

        // ── Material: theme surface + accent light wash + hairline edge ───────
        views.setInt(R.id.widget_bg_fill, "setColorFilter", pal.surface);
        views.setInt(R.id.widget_bg_glow, "setColorFilter", pal.accent);
        views.setInt(R.id.widget_bg_glow, "setImageAlpha", pal.glowAlpha);
        views.setInt(R.id.widget_bg_stroke, "setColorFilter", pal.stroke);
        views.setInt(R.id.widget_bg_stroke, "setImageAlpha", pal.strokeAlpha);

        // ── Text hierarchy ────────────────────────────────────────────────────
        views.setTextViewText(R.id.widget_song_title,
                hasSong ? song.getTitle() : context.getString(R.string.widget_no_song));
        views.setTextViewText(R.id.widget_artist_name,
                hasSong ? song.getArtist() : context.getString(R.string.widget_tap_to_play));
        views.setTextColor(R.id.widget_song_title, pal.title);
        views.setTextColor(R.id.widget_artist_name, pal.subtitle);
        if (full) {
            int eyebrow = !hasSong ? R.string.widget_eyebrow_idle
                    : state.korokae ? R.string.widget_eyebrow_korokae
                    : state.vibe ? R.string.widget_eyebrow_vibe
                    : state.isPlaying ? R.string.widget_eyebrow_playing
                    : R.string.widget_eyebrow_paused;
            views.setTextViewText(R.id.widget_eyebrow, context.getString(eyebrow));
            views.setTextColor(R.id.widget_eyebrow, pal.eyebrow);
        }

        // ── Artwork (pre-rounded) or themed placeholder ───────────────────────
        Bitmap tile = null;
        try {
            tile = artworkTile(context, art, pal);
        } catch (OutOfMemoryError | RuntimeException ignored) { /* resource fallback */ }
        if (tile != null) {
            views.setImageViewBitmap(R.id.widget_album_art, tile);
        } else {
            views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_artwork_fallback);
        }

        // ── Level glyph (full + compact) ──────────────────────────────────────
        if (full || compact) {
            try {
                float seed = hasSong ? (song.getId() % 97) + state.fraction() * 6f : 0f;
                views.setImageViewBitmap(R.id.widget_wave_progress,
                        WidgetArt.levelGlyph(pal.eyebrow, state.isPlaying && hasSong, seed));
            } catch (OutOfMemoryError | RuntimeException ignored) { /* decorative */ }
        }

        // ── Seekbar ───────────────────────────────────────────────────────────
        try {
            views.setImageViewBitmap(R.id.widget_seek_progress,
                    WidgetArt.seekBar(state.fraction(), pal.accent, pal.track, full));
        } catch (OutOfMemoryError | RuntimeException ignored) { /* seekbar optional */ }
        if (full || compact) {
            for (int i = 0; i < SEEK_ZONE_IDS.length; i++) {
                if (hasSong && state.durationMs > 0) {
                    views.setOnClickPendingIntent(SEEK_ZONE_IDS[i],
                            makeSeekPI(context, zoneCenterFraction(i, SEEK_ZONES), i));
                    views.setContentDescription(SEEK_ZONE_IDS[i],
                            context.getString(R.string.cd_widget_seek_zone, Math.round(zoneCenterFraction(i, SEEK_ZONES) * 100)));
                } else {
                    views.setOnClickPendingIntent(SEEK_ZONE_IDS[i], null);
                }
            }
        }

        // ── Self-ticking time labels (full card) ──────────────────────────────
        if (full) {
            bindTimes(views, state, hasSong, pal);
        }

        // ── Transport ─────────────────────────────────────────────────────────
        views.setImageViewResource(R.id.widget_btn_play_pause,
                state.isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        views.setInt(R.id.widget_play_bg, "setColorFilter", pal.accent);
        views.setInt(R.id.widget_btn_play_pause, "setColorFilter", pal.onAccent);
        views.setOnClickPendingIntent(R.id.widget_btn_play_pause, makeBroadcastPI(context, ACTION_PLAY_PAUSE, 1));
        views.setInt(R.id.widget_btn_next, "setColorFilter", pal.glyph);
        views.setOnClickPendingIntent(R.id.widget_btn_next, makeBroadcastPI(context, ACTION_NEXT, 3));
        if (full || compact) {
            views.setInt(R.id.widget_btn_prev, "setColorFilter", pal.glyph);
            views.setOnClickPendingIntent(R.id.widget_btn_prev, makeBroadcastPI(context, ACTION_PREV, 2));
        }

        // ── Favorite, Vibe and Korokae toggles (full card) ────────────────────
        if (full) {
            views.setImageViewResource(R.id.widget_btn_favorite,
                    state.favorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
            views.setInt(R.id.widget_btn_favorite, "setColorFilter", state.favorite ? pal.eyebrow : pal.secondaryGlyph);
            views.setContentDescription(R.id.widget_btn_favorite, context.getString(
                    state.favorite ? R.string.cd_widget_unfavorite : R.string.cd_widget_favorite));
            views.setInt(R.id.widget_btn_korokae, "setColorFilter", state.korokae ? pal.eyebrow : pal.secondaryGlyph);
            views.setOnClickPendingIntent(R.id.widget_btn_favorite, makeBroadcastPI(context, ACTION_TOGGLE_FAVORITE, 5));
            views.setOnClickPendingIntent(R.id.widget_btn_korokae, makeBroadcastPI(context, ACTION_TOGGLE_KOROKAE, 6));
            views.setInt(R.id.widget_btn_vibe, "setColorFilter", state.vibe ? pal.eyebrow : pal.secondaryGlyph);
            views.setContentDescription(R.id.widget_btn_vibe, context.getString(
                    state.vibe ? R.string.cd_widget_vibe_off : R.string.cd_widget_vibe_on));
            views.setOnClickPendingIntent(R.id.widget_btn_vibe, makeBroadcastPI(context, ACTION_TOGGLE_VIBE, 7));
            float disabledAlpha = hasSong ? 255 : 90;
            views.setInt(R.id.widget_btn_favorite, "setImageAlpha", (int) disabledAlpha);
            views.setInt(R.id.widget_btn_korokae, "setImageAlpha", (int) disabledAlpha);
            views.setInt(R.id.widget_btn_vibe, "setImageAlpha", (int) disabledAlpha);
        }

        // ── Tap body / artwork / info -> open Now Playing ─────────────────────
        Intent openApp = new Intent(context, MainActivity.class);
        openApp.putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openAppPI = PendingIntent.getActivity(
                context, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, openAppPI);
        views.setOnClickPendingIntent(R.id.widget_album_art, openAppPI);
        views.setOnClickPendingIntent(R.id.widget_info, openAppPI);

        return views;
    }

    /**
     * Elapsed counts up and remaining counts down on their own (Chronometer), so
     * the labels stay live without the service waking up every second.
     */
    private static void bindTimes(RemoteViews views, State state, boolean hasSong, WidgetArt.Palette pal) {
        views.setTextColor(R.id.widget_time_elapsed, pal.subtitle);
        views.setTextColor(R.id.widget_time_remaining, pal.subtitle);
        if (!hasSong || state.durationMs <= 0) {
            views.setChronometer(R.id.widget_time_elapsed, android.os.SystemClock.elapsedRealtime(), "--:--", false);
            views.setChronometer(R.id.widget_time_remaining, android.os.SystemClock.elapsedRealtime(), "--:--", false);
            return;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        boolean ticking = state.isPlaying;
        views.setChronometer(R.id.widget_time_elapsed, now - state.positionMs, null, ticking);
        // Countdown base = moment the track ends, so the label counts down by itself.
        views.setChronometer(R.id.widget_time_remaining, now + (state.durationMs - state.positionMs), null, ticking);
        views.setChronometerCountDown(R.id.widget_time_remaining, true);
    }

    /** Centre of tap zone {@code index} as a track fraction. Package-private for tests. */
    static float zoneCenterFraction(int index, int zones) {
        if (zones <= 0) return 0f;
        int i = Math.max(0, Math.min(zones - 1, index));
        return (i + 0.5f) / zones;
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
    @OptIn(markerClass = UnstableApi.class)
    private static PendingIntent makeBroadcastPI(Context context, String action, int requestCode) {
        Intent intent = new Intent(action);
        intent.setClass(context, SwaraWidgetReceiver.class);
        return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Seek-zone PendingIntent; each zone uses a distinct request code so extras are kept. */
    @OptIn(markerClass = UnstableApi.class)
    private static PendingIntent makeSeekPI(Context context, float fraction, int zone) {
        Intent intent = new Intent(ACTION_SEEK_TO);
        intent.setClass(context, SwaraWidgetReceiver.class);
        intent.putExtra(EXTRA_SEEK_FRACTION, fraction);
        return PendingIntent.getBroadcast(
                context, 100 + zone, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
