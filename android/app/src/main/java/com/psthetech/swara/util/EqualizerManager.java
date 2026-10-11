package com.psthetech.swara.util;

import android.app.Activity;
import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

/**
 * EqualizerManager — launches the system equalizer for Swara's audio session.
 *
 * Strategy:
 *  1. Try to open the system equalizer app via AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL.
 *  2. If no system EQ is available, show a Toast informing the user.
 *
 * The audio session ID comes from ExoPlayer and must be passed in from PlaybackViewModel.
 * PlaybackViewModel exposes getAudioSessionId() once the MediaController is connected.
 */
public class EqualizerManager {

    private static final String TAG = "EqualizerManager";

    private EqualizerManager() {}

    /**
     * Attempts to open the system equalizer for the given audio session.
     *
     * @param activity       The calling activity (for startActivityForResult context)
     * @param audioSessionId ExoPlayer's audio session ID (0 = unknown/unsupported)
     */
    public static void openSystemEqualizer(@NonNull Activity activity, int audioSessionId) {
        Intent intent = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId);
        intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, activity.getPackageName());

        if (intent.resolveActivity(activity.getPackageManager()) != null) {
            Log.d(TAG, "Opening system EQ for session: " + audioSessionId);
            activity.startActivity(intent);
        } else {
            Log.w(TAG, "No system equalizer available on this device.");
            Toast.makeText(activity,
                    activity.getString(com.psthetech.swara.R.string.equalizer_not_available),
                    Toast.LENGTH_SHORT).show();
        }
    }
}
