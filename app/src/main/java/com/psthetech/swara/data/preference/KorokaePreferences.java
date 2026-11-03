package com.psthetech.swara.data.preference;

import android.content.Context;

import androidx.annotation.NonNull;

/** Korokae user settings: how strongly vocals are reduced (0 = untouched, 1 = full). */
public final class KorokaePreferences {

    private static final String PREFS = "swara_korokae_settings";
    private static final String KEY_STRENGTH = "strength";

    public static final float DEFAULT_STRENGTH = 1f;

    private KorokaePreferences() {}

    /** Clamps to 0..1; NaN falls back to the default (full strength). */
    public static float clampStrength(float value) {
        if (Float.isNaN(value)) return DEFAULT_STRENGTH;
        return Math.max(0f, Math.min(1f, value));
    }

    public static float getStrength(@NonNull Context context) {
        return clampStrength(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getFloat(KEY_STRENGTH, DEFAULT_STRENGTH));
    }

    public static void setStrength(@NonNull Context context, float strength) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putFloat(KEY_STRENGTH, clampStrength(strength)).apply();
    }
}
