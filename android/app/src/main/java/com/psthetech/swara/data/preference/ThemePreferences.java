package com.psthetech.swara.data.preference;

import android.content.Context;
import android.content.SharedPreferences;

import com.psthetech.swara.domain.model.ColorTheme;
import com.psthetech.swara.domain.model.MorphismStyle;
import com.psthetech.swara.domain.model.ThemeMode;

/**
 * Persistence manager for user appearance preferences (ThemeMode, MorphismStyle, and ColorTheme).
 */
public class ThemePreferences {

    private static final String PREF_NAME = "swara_theme_prefs";
    private static final String KEY_THEME_MODE = "pref_theme_mode";
    private static final String KEY_MORPHISM_STYLE = "pref_morphism_style";
    private static final String KEY_COLOR_THEME = "pref_color_theme";

    private final SharedPreferences prefs;

    public ThemePreferences(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public ThemeMode getThemeMode() {
        try {
            int id = prefs.getInt(KEY_THEME_MODE, ThemeMode.DARK.getId());
            return ThemeMode.fromId(id);
        } catch (Exception e) {
            return ThemeMode.DARK;
        }
    }

    public void setThemeMode(ThemeMode mode) {
        if (mode == null) mode = ThemeMode.DARK;
        prefs.edit().putInt(KEY_THEME_MODE, mode.getId()).apply();
    }

    public MorphismStyle getMorphismStyle() {
        return MorphismStyle.LIQUID_GLASS;
    }

    public void setMorphismStyle(MorphismStyle style) {
        prefs.edit().putString(KEY_MORPHISM_STYLE, MorphismStyle.LIQUID_GLASS.getKey()).apply();
    }

    public ColorTheme getColorTheme() {
        try {
            String key = prefs.getString(KEY_COLOR_THEME, ColorTheme.SWARA.getKey());
            return ColorTheme.fromKey(key);
        } catch (Exception e) {
            return ColorTheme.SWARA;
        }
    }

    public void setColorTheme(ColorTheme colorTheme) {
        if (colorTheme == null) colorTheme = ColorTheme.SWARA;
        prefs.edit().putString(KEY_COLOR_THEME, colorTheme.getKey()).apply();
    }
}
