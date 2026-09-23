package com.psthetech.swara.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

import com.psthetech.swara.R;

/**
 * Swara Now Playing AppWidgetProvider.
 *
 * This class is the entry-point that Android calls when:
 *   - A user adds the widget to their home screen (onEnabled + onUpdate)
 *   - The system needs to redraw all widget instances (onUpdate)
 *   - A user removes all widget instances (onDisabled)
 *
 * All heavy lifting (building RemoteViews, loading album art, setting up
 * PendingIntents) is delegated to SwaraWidgetUpdater.buildViews() so that the
 * same logic is shared with live push updates from SwaraPlaybackService.
 *
 * The widget is registered in AndroidManifest.xml with:
 *   - android.appwidget.action.APPWIDGET_UPDATE intent-filter
 *   - android:resource="@xml/widget_swara_player_info" meta-data
 * Those two entries are what makes the widget appear in the system widget picker.
 */
public class SwaraWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context,
                         AppWidgetManager appWidgetManager,
                         int[] appWidgetIds) {
        // Called when:
        //   (a) a widget is first placed on the home screen, or
        //   (b) the system requests a periodic redraw (we set updatePeriodMillis=0,
        //       so this only fires on placement).
        //
        // We draw an idle state (no song, play button showing) as the placeholder.
        // SwaraPlaybackService will push a real update as soon as it's connected.
        RemoteViews views = SwaraWidgetUpdater.buildViews(context, /* song= */ null, /* isPlaying= */ false);
        for (int id : appWidgetIds) {
            appWidgetManager.updateAppWidget(id, views);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context,
                                          AppWidgetManager appWidgetManager,
                                          int appWidgetId,
                                          android.os.Bundle newOptions) {
        SwaraWidgetUpdater.pushSingleWidgetUpdate(
                context, appWidgetManager, appWidgetId, null, false, null, R.layout.widget_swara_player);
    }

    @Override
    public void onEnabled(Context context) {
        // First widget instance placed — nothing extra needed; onUpdate fires next.
    }

    @Override
    public void onDisabled(Context context) {
        // Last widget instance removed — nothing to clean up.
    }
}

