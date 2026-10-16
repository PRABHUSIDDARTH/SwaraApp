package com.psthetech.swara.data.preference;

import android.content.Context;

import androidx.annotation.NonNull;

/** User-facing library scanning options. */
public final class LibraryPreferences {

    private static final String PREFS = "swara_library_settings";
    private static final String KEY_HIDE_SHORT_CLIPS = "hide_short_clips";

    /** Clips shorter than this are hidden when "hide short clips" is on. */
    public static final long SHORT_CLIP_THRESHOLD_MS = 30_000L;

    private LibraryPreferences() {}

    public static boolean hideShortClips(@NonNull Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_HIDE_SHORT_CLIPS, true);
    }

    public static void setHideShortClips(@NonNull Context context, boolean hide) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_HIDE_SHORT_CLIPS, hide).apply();
    }

    /** Minimum duration for a file to appear in the library. */
    public static long minDurationMs(@NonNull Context context) {
        return hideShortClips(context) ? SHORT_CLIP_THRESHOLD_MS : 0L;
    }
}
