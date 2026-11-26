package com.psthetech.swara.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

/** The "Swara Deck" home-screen widget. Live updates come through {@link SwaraWidgetUpdater}. */
public class SwaraDeckWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        redraw(context, manager, ids);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id,
                                          android.os.Bundle options) {
        redraw(context, manager, new int[]{id});
    }

    private static void redraw(Context context, AppWidgetManager manager, int[] ids) {
        com.psthetech.swara.domain.model.Song song = SwaraWidgetUpdater.getLastSong();
        manager.updateAppWidget(ids, DeckWidgetRenderer.build(context, song,
                SwaraWidgetUpdater.currentState(), SwaraWidgetUpdater.cachedArtFor(song)));
    }
}
