package com.psthetech.swara.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;
import android.widget.RemoteViews;

import com.psthetech.swara.R;

/**
 * Dedicated vertical / card widget provider for Swara.
 */
public class SwaraVerticalWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        RemoteViews views = SwaraWidgetUpdater.buildSingleView(
                context, null, false, R.layout.widget_swara_player_vertical, null);
        for (int id : appWidgetIds) {
            appWidgetManager.updateAppWidget(id, views);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
                                          int appWidgetId, Bundle newOptions) {
        SwaraWidgetUpdater.pushSingleWidgetUpdate(context, appWidgetManager, appWidgetId, null, false);
    }
}
