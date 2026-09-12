package com.psthetech.swara.service;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import com.psthetech.swara.ui.MainActivity;
import com.psthetech.swara.util.SleepTimerManager;

/**
 * Swara V2 Playback Service — the authoritative playback engine.
 *
 * Architecture:
 *  - Extends MediaSessionService (Media3) — handles foreground service, notification, MediaSession
 *  - ExoPlayer is the single source of playback state (queue, position, playing/paused)
 *  - MediaSession exposes ExoPlayer to the UI layer (MediaController) and the system
 *    (notification, lock screen, Bluetooth controls)
 *  - The UI layer interacts ONLY via MediaController — no direct service references
 *
 * Background Playback:
 *  - MediaSessionService keeps the foreground notification active while playing
 *  - onTaskRemoved is NOT overridden — background playback continues when task is swiped away
 *  - When the user dismisses the notification, Media3 handles teardown automatically
 *
 * Audio Focus:
 *  - Handled by ExoPlayer via setAudioAttributes(..., handleAudioFocus = true)
 *  - No manual AudioManager focus management needed
 *
 * Headphone Unplug:
 *  - Handled by ExoPlayer via setHandleAudioBecomingNoisy(true)
 */
public class SwaraPlaybackService extends MediaSessionService {

    private static final String TAG = "SwaraPlaybackService";

    private ExoPlayer player;
    private MediaSession mediaSession;

    @Override
    public void onCreate() {
        super.onCreate();

        // Build ExoPlayer with automatic audio focus and headphone-unplug handling
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build();

        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(audioAttributes, /* handleAudioFocus */ true)
                .setHandleAudioBecomingNoisy(true)
                .build();

        // Notify SleepTimerManager when the song changes (for end-of-song sleep mode)
        player.addListener(new Player.Listener() {
            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    // Natural track finish → trigger end-of-song sleep if armed
                    SleepTimerManager.getInstance().onSongTransition();
                }
            }
        });

        // PendingIntent to open the app from the notification
        PendingIntent activityIntent = PendingIntent.getActivity(
                this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        mediaSession = new MediaSession.Builder(this, player)
                .setSessionActivity(activityIntent)
                .setCallback(new CustomCallback())
                .build();

        Log.d(TAG, "SwaraPlaybackService created");
    }

    @Nullable
    @Override
    public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "SwaraPlaybackService destroying");
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    /**
     * Custom MediaSession.Callback — handles commands from the notification and external controllers.
     * ExoPlayer already handles most commands automatically via MediaSession integration.
     */
    private class CustomCallback implements MediaSession.Callback {

        @Override
        @NonNull
        public MediaSession.ConnectionResult onConnect(
                @NonNull MediaSession session,
                @NonNull MediaSession.ControllerInfo controller) {
            // Accept connections from the app and notification
            return MediaSession.Callback.super.onConnect(session, controller);
        }

        @Override
        public void onPostConnect(@NonNull MediaSession session,
                                  @NonNull MediaSession.ControllerInfo controller) {
            // Connection established — no additional setup needed
        }
    }
}
