package com.psthetech.swara.ui.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.psthetech.swara.data.preference.ThemePreferences;
import com.psthetech.swara.domain.model.ColorTheme;
import com.psthetech.swara.domain.model.MorphismStyle;
import com.psthetech.swara.domain.model.ThemeMode;

/**
 * Global Morphism & Theme Manager singleton.
 * Emits active DesignTokens via LiveData and applies style properties dynamically to UI components.
 */
public class MorphismThemeManager {

    private static MorphismThemeManager instance;

    private ThemePreferences preferences;
    private final MutableLiveData<DesignTokens> designTokensLiveData = new MutableLiveData<>();
    private final MutableLiveData<MorphismStyle> activeStyleLiveData = new MutableLiveData<>();
    private final MutableLiveData<ThemeMode> activeThemeModeLiveData = new MutableLiveData<>();
    private final MutableLiveData<ColorTheme> activeColorThemeLiveData = new MutableLiveData<>();

    private Context appContext;

    private MorphismThemeManager() {}

    public static synchronized MorphismThemeManager getInstance() {
        if (instance == null) {
            instance = new MorphismThemeManager();
        }
        return instance;
    }

    public synchronized void init(Context context) {
        if (this.appContext != null) return;
        this.appContext = context.getApplicationContext();
        this.preferences = new ThemePreferences(appContext);

        ThemeMode mode = preferences.getThemeMode();
        MorphismStyle style = preferences.getMorphismStyle();
        ColorTheme colorTheme = preferences.getColorTheme();

        // Apply night mode setting to AppCompat
        AppCompatDelegate.setDefaultNightMode(mode.getNightMode());

        boolean isNight = isNightMode(appContext, mode);
        DesignTokens tokens = new DesignTokens(appContext, colorTheme, style, isNight);

        activeThemeModeLiveData.setValue(mode);
        activeStyleLiveData.setValue(style);
        activeColorThemeLiveData.setValue(colorTheme);
        designTokensLiveData.setValue(tokens);
    }

    public LiveData<DesignTokens> getDesignTokens() {
        return designTokensLiveData;
    }

    public LiveData<MorphismStyle> getActiveStyle() {
        return activeStyleLiveData;
    }

    public LiveData<ThemeMode> getActiveThemeMode() {
        return activeThemeModeLiveData;
    }

    public LiveData<ColorTheme> getActiveColorTheme() {
        return activeColorThemeLiveData;
    }

    public DesignTokens getCurrentTokens() {
        DesignTokens tokens = designTokensLiveData.getValue();
        if (tokens == null && appContext != null) {
            ThemeMode mode = preferences != null ? preferences.getThemeMode() : ThemeMode.DARK;
            MorphismStyle style = preferences != null ? preferences.getMorphismStyle() : MorphismStyle.LIQUID_GLASS;
            ColorTheme colorTheme = preferences != null ? preferences.getColorTheme() : ColorTheme.SWARA;
            tokens = new DesignTokens(appContext, colorTheme, style, isNightMode(appContext, mode));
        }
        return tokens;
    }

    public void setThemeMode(Context context, ThemeMode mode) {
        if (preferences != null) {
            preferences.setThemeMode(mode);
        }
        AppCompatDelegate.setDefaultNightMode(mode.getNightMode());
        activeThemeModeLiveData.setValue(mode);
        refreshTokens(context);
    }

    public void setMorphismStyle(Context context, MorphismStyle style) {
        if (preferences != null) {
            preferences.setMorphismStyle(style);
        }
        activeStyleLiveData.setValue(style);
        refreshTokens(context);
    }

    public void setColorTheme(Context context, ColorTheme colorTheme) {
        if (preferences != null) {
            preferences.setColorTheme(colorTheme);
        }
        activeColorThemeLiveData.setValue(colorTheme);
        refreshTokens(context);
    }

    public void refreshTokens(Context context) {
        Context ctx = (context != null ? context.getApplicationContext() : appContext);
        if (ctx == null) return;

        ThemeMode mode = (preferences != null ? preferences.getThemeMode() : ThemeMode.DARK);
        MorphismStyle style = (preferences != null ? preferences.getMorphismStyle() : MorphismStyle.LIQUID_GLASS);
        ColorTheme colorTheme = (preferences != null ? preferences.getColorTheme() : ColorTheme.SWARA);

        boolean isNight = isNightMode(ctx, mode);
        DesignTokens tokens = new DesignTokens(ctx, colorTheme, style, isNight);

        designTokensLiveData.setValue(tokens);
    }

    public static boolean isNightMode(Context context, ThemeMode mode) {
        if (mode == ThemeMode.DARK) return true;
        if (mode == ThemeMode.LIGHT) return false;
        // System Default
        int currentNightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * Apply active design tokens to a MaterialCardView.
     */
    public void applyToCard(MaterialCardView card, @Nullable DesignTokens tokens) {
        if (card == null) return;
        if (tokens == null) tokens = getCurrentTokens();
        if (tokens == null) return;

        float density = card.getResources().getDisplayMetrics().density;

        card.setCardBackgroundColor(tokens.getSurfaceColor());

        ShapeAppearanceModel shapeModel = card.getShapeAppearanceModel().toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, tokens.getCornerRadiusDp() * density)
                .build();
        card.setShapeAppearanceModel(shapeModel);

        card.setStrokeColor(tokens.getStrokeColor());
        card.setStrokeWidth(Math.max(0, Math.round(tokens.getStrokeWidthDp() * density)));
        card.setCardElevation(tokens.getElevationDp() * density);
    }

    /**
     * Apply active design tokens to a generic View (surface or surface variant).
     */
    public void applyToView(View view, boolean isVariant, @Nullable DesignTokens tokens) {
        if (view == null) return;
        if (tokens == null) tokens = getCurrentTokens();
        if (tokens == null) return;

        Context context = view.getContext();
        GradientDrawable drawable = isVariant
                ? (GradientDrawable) tokens.createSurfaceVariantDrawable(context)
                : (GradientDrawable) tokens.createCardDrawable(context);

        view.setBackground(drawable);
    }

    /**
     * Apply design tokens to text views.
     */
    public void applyToTextViews(@Nullable TextView title, @Nullable TextView subtitle, @Nullable DesignTokens tokens) {
        if (tokens == null) tokens = getCurrentTokens();
        if (tokens == null) return;

        if (title != null) {
            title.setTextColor(tokens.getTextPrimaryColor());
        }
        if (subtitle != null) {
            subtitle.setTextColor(tokens.getTextSecondaryColor());
        }
    }

    /**
     * Apply design tokens to an input EditText (Search box).
     */
    public void applyToInputBox(EditText input, @Nullable DesignTokens tokens) {
        if (input == null) return;
        if (tokens == null) tokens = getCurrentTokens();
        if (tokens == null) return;

        input.setTextColor(tokens.getTextPrimaryColor());
        input.setHintTextColor(tokens.getTextTertiaryColor());
    }

    /**
     * Apply active design tokens to BottomNavigationView.
     */
    public void applyToBottomNav(BottomNavigationView nav, @Nullable DesignTokens tokens) {
        if (nav == null) return;
        if (tokens == null) tokens = getCurrentTokens();
        if (tokens == null) return;

        Context context = nav.getContext();
        float density = context.getResources().getDisplayMetrics().density;

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(tokens.getSurfaceColor());

        if (tokens.getStyle() == MorphismStyle.BRUTALISM) {
            bg.setStroke(Math.round(3 * density), tokens.getAccentColor());
        } else if (tokens.getStrokeWidthDp() > 0) {
            bg.setStroke(Math.max(1, Math.round(tokens.getStrokeWidthDp() * density)), tokens.getStrokeColor());
        }
        nav.setBackground(bg);

        // Active state indicator color
        int activeColor = ColorStateList.valueOf(tokens.getAccentColor()).getDefaultColor();
        int unselectedColor = tokens.getTextSecondaryColor();

        ColorStateList stateList = new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{
                        activeColor,
                        unselectedColor
                }
        );
        nav.setItemIconTintList(stateList);
        nav.setItemTextColor(stateList);
    }
}
