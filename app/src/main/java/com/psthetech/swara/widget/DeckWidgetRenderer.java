package com.psthetech.swara.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.ColorTheme;
import com.psthetech.swara.domain.model.MorphismStyle;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.MainActivity;
import com.psthetech.swara.ui.deck.DeckPalette;
import com.psthetech.swara.ui.deck.DeckReelView;
import com.psthetech.swara.ui.deck.DeckStandbyActivity;
import com.psthetech.swara.ui.theme.DesignTokens;
import com.psthetech.swara.ui.theme.MorphismThemeManager;

import java.util.Arrays;

/** Builds and pushes the Deck widget. Colours come from {@link DeckPalette}. */
final class DeckWidgetRenderer {

    private static final int REEL_DP = 112;

    private static final int[] SCREWS = {R.id.deck_screw_tl, R.id.deck_screw_tr,
            R.id.deck_screw_bl, R.id.deck_screw_br};
    private static final int[] SLOTS = {R.id.deck_screw_slot_tl, R.id.deck_screw_slot_tr,
            R.id.deck_screw_slot_bl, R.id.deck_screw_slot_br};

    /** What the last full update showed; while it matches, a tick only moves the position. */
    @Nullable private static String lastSignature;

    private DeckWidgetRenderer() { }

    @NonNull
    private static int[] placedIds(Context context) {
        int[] ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                new ComponentName(context, SwaraDeckWidgetProvider.class));
        return ids != null ? ids : new int[0];
    }

    static boolean hasPlaced(@NonNull Context context) {
        try {
            return placedIds(context).length > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Called with every widget update from the playback service. */
    static synchronized void push(@NonNull Context context, @Nullable Song song,
                                  @NonNull SwaraWidgetUpdater.State state, @Nullable Bitmap art) {
        if (!hasPlaced(context)) {
            lastSignature = null;
            return;
        }
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = placedIds(context);
        DesignTokens tokens = tokens(context);
        String signature = DeckWidgetModel.signature(song != null ? song.getId() : -1,
                state.isPlaying, state.favorite, state.vibe, state.durationMs,
                System.identityHashCode(art),
                31 * tokens.getAccentColor() + tokens.getBackgroundColor())
                + "|" + Arrays.toString(ids);
        if (signature.equals(lastSignature)) {
            manager.partiallyUpdateAppWidget(ids, tick(context, song != null, state));
        } else {
            manager.updateAppWidget(ids, build(context, song, state, art));
            lastSignature = signature;
        }
    }

    @NonNull
    private static DesignTokens tokens(Context context) {
        DesignTokens tokens = MorphismThemeManager.getInstance().getCurrentTokens();
        return tokens != null ? tokens
                : new DesignTokens(context, ColorTheme.SWARA, MorphismStyle.LIQUID_GLASS, true);
    }

    /** After anything else draws the widget, the next update must send all of it again. */
    static synchronized void invalidate() {
        lastSignature = null;
    }

    /** The partial update sent each second: the position and nothing else. */
    @NonNull
    static RemoteViews tick(Context context, boolean hasSong, SwaraWidgetUpdater.State state) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_swara_deck);
        bindPosition(views, hasSong, state);
        return views;
    }

    private static void bindPosition(RemoteViews views, boolean hasSong,
                                     SwaraWidgetUpdater.State state) {
        views.setChronometer(R.id.deck_elapsed, SystemClock.elapsedRealtime() - state.positionMs,
                null, hasSong && state.isPlaying);
        views.setProgressBar(R.id.deck_progress, DeckWidgetModel.PROGRESS_MAX,
                hasSong ? DeckWidgetModel.progress(state.positionMs, state.durationMs) : 0, false);
    }

    @NonNull
    static RemoteViews build(@NonNull Context context, @Nullable Song song,
                             @NonNull SwaraWidgetUpdater.State state, @Nullable Bitmap art) {
        DeckPalette p = new DeckPalette(tokens(context));
        boolean hasSong = song != null;
        boolean spinning = DeckWidgetModel.spins(hasSong, state.isPlaying, Build.VERSION.SDK_INT);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_swara_deck);

        tint(views, R.id.deck_widget_body, p.body);
        tint(views, R.id.deck_widget_outline, p.bodyEdge);
        for (int id : SCREWS) tint(views, id, p.metal);
        for (int id : SLOTS) tint(views, id, p.textTertiary);

        views.setImageViewBitmap(R.id.deck_reel, reel(context, p, art, !spinning));
        views.setViewVisibility(R.id.deck_spin_windows, spinning ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.deck_spin_marker, spinning ? View.VISIBLE : View.GONE);
        if (spinning && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(R.id.deck_spin_windows, "setIndeterminateTintList",
                    ColorStateList.valueOf(p.tape));
            views.setColorStateList(R.id.deck_spin_marker, "setIndeterminateTintList",
                    ColorStateList.valueOf(p.accent));
        }

        views.setTextViewText(R.id.deck_title,
                hasSong ? song.getTitle() : context.getString(R.string.widget_no_song));
        views.setTextViewText(R.id.deck_artist,
                hasSong ? song.getArtist() : context.getString(R.string.widget_tap_to_play));
        views.setTextColor(R.id.deck_title, p.text);
        views.setTextColor(R.id.deck_artist, p.accentText);
        tint(views, R.id.deck_window, p.well);
        views.setTextColor(R.id.deck_elapsed, p.accentText);
        views.setTextColor(R.id.deck_total, p.textSecondary);
        // Formatted as the Chronometer beside it formats the elapsed time.
        views.setTextViewText(R.id.deck_total, "/ " + DateUtils.formatElapsedTime(
                (hasSong ? state.durationMs : 0) / 1000));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(R.id.deck_progress, "setProgressTintList",
                    ColorStateList.valueOf(p.accent));
            views.setColorStateList(R.id.deck_progress, "setProgressBackgroundTintList",
                    ColorStateList.valueOf(p.capEdge));
        }
        bindPosition(views, hasSong, state);

        key(views, p, R.id.deck_favorite_side, R.id.deck_favorite_cap, R.id.deck_favorite_icon,
                false, state.favorite);
        views.setImageViewResource(R.id.deck_favorite_icon,
                state.favorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        views.setContentDescription(R.id.deck_favorite, context.getString(
                state.favorite ? R.string.cd_widget_unfavorite : R.string.cd_widget_favorite));
        key(views, p, R.id.deck_prev_side, R.id.deck_prev_cap, R.id.deck_prev_icon, false, false);
        key(views, p, R.id.deck_play_side, R.id.deck_play_cap, R.id.deck_play_icon, true, false);
        views.setImageViewResource(R.id.deck_play_icon,
                state.isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        views.setContentDescription(R.id.deck_play,
                context.getString(state.isPlaying ? R.string.pause : R.string.play));
        key(views, p, R.id.deck_next_side, R.id.deck_next_cap, R.id.deck_next_icon, false, false);
        key(views, p, R.id.deck_vibe_side, R.id.deck_vibe_cap, R.id.deck_vibe_icon,
                false, state.vibe);
        views.setContentDescription(R.id.deck_vibe, context.getString(
                state.vibe ? R.string.cd_widget_vibe_off : R.string.cd_widget_vibe_on));
        key(views, p, R.id.deck_standby_side, R.id.deck_standby_cap, R.id.deck_standby_icon,
                false, false);

        click(views, context, R.id.deck_favorite, SwaraWidgetUpdater.ACTION_TOGGLE_FAVORITE, 5);
        click(views, context, R.id.deck_prev, SwaraWidgetUpdater.ACTION_PREV, 2);
        click(views, context, R.id.deck_play, SwaraWidgetUpdater.ACTION_PLAY_PAUSE, 1);
        click(views, context, R.id.deck_next, SwaraWidgetUpdater.ACTION_NEXT, 3);
        click(views, context, R.id.deck_vibe, SwaraWidgetUpdater.ACTION_TOGGLE_VIBE, 7);

        // From the home screen the standby screen gets its own task.
        Intent standby = DeckStandbyActivity.intent(context)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        views.setOnClickPendingIntent(R.id.deck_standby, PendingIntent.getActivity(context, 200,
                standby, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        Intent open = new Intent(context, MainActivity.class)
                .putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        // On the panel, not the root: the see-through space around it should not open the app.
        views.setOnClickPendingIntent(R.id.deck_widget_panel, PendingIntent.getActivity(context, 0,
                open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return views;
    }

    private static void tint(RemoteViews views, int id, int color) {
        views.setInt(id, "setColorFilter", color);
    }

    /** A physical-looking key: side wall, cap and icon. A key that is on gets an accent icon. */
    private static void key(RemoteViews views, DeckPalette p, int side, int cap, int icon,
                            boolean primary, boolean on) {
        tint(views, side, primary ? p.accentSide : p.capSide);
        tint(views, cap, primary ? p.accent : p.cap);
        tint(views, icon, primary ? p.onAccent : on ? p.accentText : p.icon);
    }

    private static void click(RemoteViews views, Context context, int id, String action, int code) {
        views.setOnClickPendingIntent(id,
                SwaraWidgetUpdater.makeBroadcastPI(context, action, code));
    }

    /** The app's reel, drawn off screen. Without its rotating parts it is the spinning base. */
    private static Bitmap reel(Context context, DeckPalette p, @Nullable Bitmap art,
                               boolean still) {
        int size = Math.round(REEL_DP * context.getResources().getDisplayMetrics().density);
        DeckReelView view = new DeckReelView(context);
        view.setPalette(p);
        view.setRotatingParts(still);
        view.setArtworkBitmap(art != null && !art.isRecycled() ? art : null);
        int spec = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY);
        view.measure(spec, spec);
        view.layout(0, 0, size, size);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }
}
