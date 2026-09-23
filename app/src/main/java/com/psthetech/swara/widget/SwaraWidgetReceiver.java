package com.psthetech.swara.widget;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;
import com.psthetech.swara.service.SwaraPlaybackService;

/**
 * BroadcastReceiver for widget button taps.
 *
 * ARCHITECTURE FIX (vs old startService() approach):
 *   Old: sendMediaKey() via startService() — BROKEN on Android 8+ when app is in background.
 *   New: Connect a temporary MediaController via SessionToken and call transport controls.
 *        MediaController connects to the running MediaSession directly, with no service start
 *        restrictions. The receiver uses goAsync() to keep the process alive during the
 *        async MediaController build.
 *
 * Flow:
 *   Widget button tap → PendingIntent broadcast → SwaraWidgetReceiver.onReceive()
 *       → goAsync() to survive async work
 *       → SessionToken + MediaController.Builder.buildAsync()
 *       → controller.play()/pause()/seekToNext()/seekToPrevious()
 *       → controller.release()
 *       → pendingResult.finish()
 *
 * NOTE: If SwaraPlaybackService is not running (no active session), the MediaController
 * connection will fail silently — which is correct behavior (nothing to control).
 */
@UnstableApi
public class SwaraWidgetReceiver extends BroadcastReceiver {

    private static final String TAG = "SwaraWidgetReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();

        // Handle widget-update broadcast immediately (sync, no Media3 needed)
        if (SwaraWidgetUpdater.ACTION_WIDGET_UPDATE.equals(action)) {
            SwaraWidgetUpdater.pushUpdate(context, null, false);
            return;
        }

        // For playback control: use goAsync() + MediaController
        boolean isPlaybackAction =
                SwaraWidgetUpdater.ACTION_PLAY_PAUSE.equals(action)
                        || SwaraWidgetUpdater.ACTION_NEXT.equals(action)
                        || SwaraWidgetUpdater.ACTION_PREV.equals(action);

        if (!isPlaybackAction) return;

        // goAsync() keeps this BroadcastReceiver's process alive for the async MC connection
        final PendingResult pendingResult = goAsync();

        try {
            SessionToken sessionToken = new SessionToken(
                    context.getApplicationContext(),
                    new ComponentName(context, SwaraPlaybackService.class));

            ListenableFuture<MediaController> controllerFuture =
                    new MediaController.Builder(context.getApplicationContext(), sessionToken)
                            .buildAsync();

            controllerFuture.addListener(() -> {
                try {
                    MediaController controller = controllerFuture.get();
                    if (controller == null) {
                        Log.w(TAG, "MediaController is null; ignoring action: " + action);
                        pendingResult.finish();
                        return;
                    }

                    switch (action) {
                        case SwaraWidgetUpdater.ACTION_PLAY_PAUSE:
                            if (controller.isPlaying()) {
                                controller.pause();
                            } else {
                                controller.play();
                            }
                            break;
                        case SwaraWidgetUpdater.ACTION_NEXT:
                            controller.seekToNextMediaItem();
                            break;
                        case SwaraWidgetUpdater.ACTION_PREV:
                            controller.seekToPreviousMediaItem();
                            break;
                    }

                    // Release controller after sending the command
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed(() -> {
                                try {
                                    controller.release();
                                } catch (Exception ignored) {
                                } finally {
                                    try {
                                        pendingResult.finish();
                                    } catch (Exception ignored) {}
                                }
                            }, 350L);

                } catch (Exception e) {
                    Log.e(TAG, "Error controlling playback from widget: " + e.getMessage());
                    try {
                        pendingResult.finish();
                    } catch (Exception ignored) {}
                }
            }, androidx.core.content.ContextCompat.getMainExecutor(context));

        } catch (Exception e) {
            Log.e(TAG, "Failed to build MediaController for widget action: " + e.getMessage());
            try {
                pendingResult.finish();
            } catch (Exception ignored) {}
        }
    }
}
