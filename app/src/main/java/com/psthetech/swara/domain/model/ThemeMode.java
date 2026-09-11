package com.psthetech.swara.domain.model;

import androidx.appcompat.app.AppCompatDelegate;
import com.psthetech.swara.R;

/**
 * Application Theme Mode options.
 */
public enum ThemeMode {
    SYSTEM(0, R.string.theme_system, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(1, R.string.theme_light, AppCompatDelegate.MODE_NIGHT_NO),
    DARK(2, R.string.theme_dark, AppCompatDelegate.MODE_NIGHT_YES);

    private final int id;
    private final int titleResId;
    private final int nightMode;

    ThemeMode(int id, int titleResId, int nightMode) {
        this.id = id;
        this.titleResId = titleResId;
        this.nightMode = nightMode;
    }

    public int getId() {
        return id;
    }

    public int getTitleResId() {
        return titleResId;
    }

    public int getNightMode() {
        return nightMode;
    }

    public static ThemeMode fromId(int id) {
        for (ThemeMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return DARK; // Default to Dark / System experience
    }
}
