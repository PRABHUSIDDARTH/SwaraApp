package com.psthetech.swara.widget;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.psthetech.swara.service.SwaraPlaybackService;

/**
 * BroadcastReceiver that handles widget button taps and playback-state update
 * broadcasts from SwaraPlaybackService.
 *
 * Button tap flow:
 *   Widget ImageButton (PendingIntent broadcast)
 *       → SwaraWidgetReceiver.onReceive()
 *       → Sends a ACTION_MEDIA_BUTTON Intent to SwaraPlaybackService
 *         (Media3 MediaSessionService auto-processes media key events)
 *
 * Update flow:
 *   SwaraPlaybackService → SwaraWidgetUpdater.pushUpdate() → AppWidgetManager
 *   The ACTION_WIDGET_UPDATE broadcast is kept for external/test callers.
 */
public class SwaraWidgetReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        switch (intent.getAction()) {

            case SwaraWidgetUpdater.ACTION_PLAY_PAUSE:
                sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                break;

            case SwaraWidgetUpdater.ACTION_NEXT:
                sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT);
                break;

            case SwaraWidgetUpdater.ACTION_PREV:
                sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                break;

            case SwaraWidgetUpdater.ACTION_WIDGET_UPDATE:
                // External / test callers can broadcast this to request a full widget redraw.
                SwaraWidgetUpdater.pushUpdate(context, null, false);
                break;

            default:
                break;
        }
    }

    /**
     * Dispatch a media key event to SwaraPlaybackService.
     *
     * Media3 MediaSessionService handles ACTION_MEDIA_BUTTON intents automatically:
     * it forwards the KeyEvent to the active MediaSession, which in turn controls
     * ExoPlayer. This keeps widget button handling fully decoupled from service internals.
     *
     * We send both DOWN and UP events — some media session implementations require
     * the full pair to register the action.
     */
    private void sendMediaKey(Context context, int keyCode) {
        long now = SystemClock.uptimeMillis();

        // Build the DOWN + UP key event pair
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
        KeyEvent up   = new KeyEvent(now, now, KeyEvent.ACTION_UP,   keyCode, 0);

        ComponentName serviceComponent = new ComponentName(context, SwaraPlaybackService.class);

        Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        downIntent.setComponent(serviceComponent);
        downIntent.putExtra(Intent.EXTRA_KEY_EVENT, down);

        Intent upIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        upIntent.setComponent(serviceComponent);
        upIntent.putExtra(Intent.EXTRA_KEY_EVENT, up);

        // startService so the service is started if it was previously stopped
        try {
            context.startService(downIntent);
            context.startService(upIntent);
        } catch (Exception e) {
            // Service may not be startable in background on some Android versions;
            // fall through — playback was likely already stopped.
        }
    }
}

