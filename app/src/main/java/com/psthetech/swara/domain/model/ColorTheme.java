package com.psthetech.swara.domain.model;

import com.psthetech.swara.R;

/**
 * First-class Application Color Theme options.
 */
public enum ColorTheme {
    SWARA("SWARA", R.string.color_theme_swara_title, R.string.color_theme_swara_desc),
    MIDNIGHT("MIDNIGHT", R.string.color_theme_midnight_title, R.string.color_theme_midnight_desc),
    LAVENDER("LAVENDER", R.string.color_theme_lavender_title, R.string.color_theme_lavender_desc),
    CHAMPAGNE("CHAMPAGNE", R.string.color_theme_champagne_title, R.string.color_theme_champagne_desc),
    ROSE("ROSE", R.string.color_theme_rose_title, R.string.color_theme_rose_desc),
    OCEAN("OCEAN", R.string.color_theme_ocean_title, R.string.color_theme_ocean_desc),
    FOREST("FOREST", R.string.color_theme_forest_title, R.string.color_theme_forest_desc),
    OFF_WHITE("OFF_WHITE", R.string.color_theme_off_white_title, R.string.color_theme_off_white_desc);

    private final String key;
    private final int titleResId;
    private final int descResId;

    ColorTheme(String key, int titleResId, int descResId) {
        this.key = key;
        this.titleResId = titleResId;
        this.descResId = descResId;
    }

    public String getKey() {
        return key;
    }

    public int getTitleResId() {
        return titleResId;
    }

    public int getDescResId() {
        return descResId;
    }

    public static ColorTheme fromKey(String key) {
        if (key == null) return SWARA;
        for (ColorTheme theme : values()) {
            if (theme.key.equalsIgnoreCase(key) || theme.name().equalsIgnoreCase(key)) {
                return theme;
            }
        }
        return SWARA; // Default Swara brand theme
    }
}
