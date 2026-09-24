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
import androidx.core.view.ViewCompat;
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
        MorphismStyle style = MorphismStyle.LIQUID_GLASS;
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
            MorphismStyle style = MorphismStyle.LIQUID_GLASS;
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
        MorphismStyle fixedStyle = MorphismStyle.LIQUID_GLASS;
        if (preferences != null) {
            preferences.setMorphismStyle(fixedStyle);
        }
        activeStyleLiveData.setValue(fixedStyle);
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
        MorphismStyle style = MorphismStyle.LIQUID_GLASS;
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

        if (view instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) view;
            applyToCard(card, tokens);
            // MaterialCardView owns its background. Decorate its content with glass drawable.
            if (card.getChildCount() > 0) {
                card.getChildAt(0).setBackground(tokens.createCardDrawable(card.getContext()));
            }
            return;
        }

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
     * Apply design tokens to a TextInputLayout and inner EditText.
     */
    public void applyToSearchInput(@Nullable com.google.android.material.textfield.TextInputLayout til,
                                  @Nullable EditText et,
                                  @Nullable DesignTokens tokens) {
        if (tokens == null) tokens = getCurrentTokens();
        if (tokens == null) return;

        if (et != null) {
            et.setTextColor(tokens.getSearchTextColor());
            et.setHintTextColor(tokens.getSearchHintColor());
        }

        if (til != null) {
            ColorStateList strokeStateList = new ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_focused},
                            new int[]{}
                    },
                    new int[]{
                            tokens.getAccentColor(),
                            tokens.getStrokeColor()
                    }
            );
            til.setBoxStrokeColorStateList(strokeStateList);
            til.setBoxBackgroundColor(tokens.getSearchBackgroundColor());
            til.setHintTextColor(ColorStateList.valueOf(tokens.getAccentColor()));
            til.setDefaultHintTextColor(ColorStateList.valueOf(tokens.getSearchHintColor()));
            if (til.getStartIconDrawable() != null) {
                til.setStartIconTintList(ColorStateList.valueOf(tokens.getIconSecondaryColor()));
            }
            if (til.getEndIconDrawable() != null) {
                til.setEndIconTintList(ColorStateList.valueOf(tokens.getIconSecondaryColor()));
            }
            float rPx = 28f * til.getContext().getResources().getDisplayMetrics().density;
            til.setBoxCornerRadii(rPx, rPx, rPx, rPx);
        }
    }

    /**
     * Creates a MaterialAlertDialogBuilder respecting the current theme.
     */
    public static com.google.android.material.dialog.MaterialAlertDialogBuilder createDialogBuilder(Context context) {
        return new com.google.android.material.dialog.MaterialAlertDialogBuilder(context);
    }

    /**
     * Apply active design tokens to BottomNavigationView.
     * Uses Liquid Glass nav drawable — floating pill with TL→BR highlight gradient.
     */
    public void applyToBottomNav(BottomNavigationView nav, @Nullable DesignTokens tokens) {
        if (nav == null) return;
        if (tokens == null) tokens = getCurrentTokens();
        if (tokens == null) return;

        Context context = nav.getContext();
        float density = context.getResources().getDisplayMetrics().density;

        // Glass pill background (highlight gradient + semi-transparent fill + stroke)
        nav.setBackground(tokens.createGlassNavDrawable(context));
        nav.setClipToOutline(true);
        ViewCompat.setElevation(nav, 8 * density);

        // Active indicator — crisp luminous accent pill sized cleanly for the icon without crowding text
        nav.setItemActiveIndicatorEnabled(true);
        nav.setItemActiveIndicatorWidth((int) (48 * density));
        nav.setItemActiveIndicatorHeight((int) (26 * density));
        nav.setActiveIndicatorLabelPadding((int) (2 * density));

        com.google.android.material.shape.ShapeAppearanceModel shape =
                com.google.android.material.shape.ShapeAppearanceModel.builder()
                        .setAllCornerSizes(13 * density)
                        .build();
        nav.setItemActiveIndicatorShapeAppearance(shape);

        int indicatorColor = tokens.isNightMode()
                ? Color.argb(85, Color.red(tokens.getAccentColor()), Color.green(tokens.getAccentColor()), Color.blue(tokens.getAccentColor()))
                : Color.argb(45, Color.red(tokens.getAccentColor()), Color.green(tokens.getAccentColor()), Color.blue(tokens.getAccentColor()));
        nav.setItemActiveIndicatorColor(ColorStateList.valueOf(indicatorColor));

        // Icon + text color state list
        int activeColor = tokens.getReadableAccentColor();
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

    /**
     * Apply glass surface to any view with configurable corner radius.
     * Delegates to LiquidGlassRenderer for consistent application.
     *
     * @param view           Target view.
     * @param tokens         Current design tokens.
     * @param cornerRadiusDp Corner radius in dp (999 = full pill, 20 = mini-player, etc.)
     */
    public void applyGlassToView(View view, @Nullable DesignTokens tokens, float cornerRadiusDp) {
        if (view == null) return;
        if (tokens == null) tokens = getCurrentTokens();
        if (tokens == null) return;
        com.psthetech.swara.ui.glass.LiquidGlassRenderer.applyGlassBackground(
                view, tokens, cornerRadiusDp);
    }
}
