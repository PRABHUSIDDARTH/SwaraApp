package com.psthetech.swara.data.preference;

import android.content.Context;
import android.content.SharedPreferences;

import com.psthetech.swara.domain.model.MorphismStyle;
import com.psthetech.swara.domain.model.ThemeMode;

/**
 * Persistence manager for user appearance preferences (ThemeMode and MorphismStyle).
 */
public class ThemePreferences {

    private static final String PREF_NAME = "swara_theme_prefs";
    private static final String KEY_THEME_MODE = "pref_theme_mode";
    private static final String KEY_MORPHISM_STYLE = "pref_morphism_style";

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
        try {
            String key = prefs.getString(KEY_MORPHISM_STYLE, MorphismStyle.LIQUID_GLASS.getKey());
            return MorphismStyle.fromKey(key);
        } catch (Exception e) {
            return MorphismStyle.LIQUID_GLASS;
        }
    }

    public void setMorphismStyle(MorphismStyle style) {
        if (style == null) style = MorphismStyle.LIQUID_GLASS;
        prefs.edit().putString(KEY_MORPHISM_STYLE, style.getKey()).apply();
    }
}
