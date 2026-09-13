package com.psthetech.swara.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.ColorTheme;
import com.psthetech.swara.domain.model.MorphismStyle;
import com.psthetech.swara.domain.model.ThemeMode;
import com.psthetech.swara.ui.theme.DesignTokens;
import com.psthetech.swara.ui.theme.MorphismThemeManager;

public class SettingsFragment extends Fragment {

    private View layoutSettingsRoot;
    private ImageButton btnBack;
    private TextView tvSettingsTitle;
    private TextView tvThemeHeader;
    private TextView tvColorThemeHeader;
    private MaterialCardView cardThemeContainer;
    private RadioGroup rgTheme;
    private RadioButton rbThemeDark;
    private RadioButton rbThemeSystem;
    private RadioButton rbThemeLight;
    private RecyclerView rvColorThemes;

    private ColorThemePreviewAdapter colorThemeAdapter;
    private boolean isUpdatingRadioState = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        layoutSettingsRoot = view.findViewById(R.id.layoutSettingsRoot);
        btnBack = view.findViewById(R.id.btnBack);
        tvSettingsTitle = view.findViewById(R.id.tvSettingsTitle);
        tvThemeHeader = view.findViewById(R.id.tvThemeHeader);
        tvColorThemeHeader = view.findViewById(R.id.tvColorThemeHeader);
        cardThemeContainer = view.findViewById(R.id.cardThemeContainer);
        rgTheme = view.findViewById(R.id.rgTheme);
        rbThemeDark = view.findViewById(R.id.rbThemeDark);
        rbThemeSystem = view.findViewById(R.id.rbThemeSystem);
        rbThemeLight = view.findViewById(R.id.rbThemeLight);
        rvColorThemes = view.findViewById(R.id.rvColorThemes);

        btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        rvColorThemes.setLayoutManager(new LinearLayoutManager(requireContext()));

        MorphismThemeManager manager = MorphismThemeManager.getInstance();

        ThemeMode initialThemeMode = manager.getActiveThemeMode().getValue();
        if (initialThemeMode == null) initialThemeMode = ThemeMode.DARK;

        MorphismStyle initialStyle = MorphismStyle.LIQUID_GLASS;

        ColorTheme initialColorTheme = manager.getActiveColorTheme().getValue();
        if (initialColorTheme == null) initialColorTheme = ColorTheme.SWARA;

        colorThemeAdapter = new ColorThemePreviewAdapter(initialColorTheme, initialThemeMode, initialStyle, selectedColorTheme -> {
            manager.setColorTheme(requireContext(), selectedColorTheme);
        });
        rvColorThemes.setAdapter(colorThemeAdapter);

        updateRadioSelection(initialThemeMode);

        rgTheme.setOnCheckedChangeListener((group, checkedId) -> {
            if (isUpdatingRadioState) return;
            ThemeMode mode;
            if (checkedId == R.id.rbThemeDark) {
                mode = ThemeMode.DARK;
            } else if (checkedId == R.id.rbThemeLight) {
                mode = ThemeMode.LIGHT;
            } else {
                mode = ThemeMode.SYSTEM;
            }
            manager.setThemeMode(requireContext(), mode);
        });

        manager.getDesignTokens().observe(getViewLifecycleOwner(), this::applyDesignTokens);
        manager.getActiveThemeMode().observe(getViewLifecycleOwner(), mode -> {
            updateRadioSelection(mode);
            updateAdaptersState();
        });
        manager.getActiveColorTheme().observe(getViewLifecycleOwner(), colorTheme -> updateAdaptersState());
    }

    private void updateAdaptersState() {
        MorphismThemeManager manager = MorphismThemeManager.getInstance();
        ThemeMode mode = manager.getActiveThemeMode().getValue();
        if (mode == null) mode = ThemeMode.DARK;

        MorphismStyle style = MorphismStyle.LIQUID_GLASS;

        ColorTheme colorTheme = manager.getActiveColorTheme().getValue();
        if (colorTheme == null) colorTheme = ColorTheme.SWARA;

        if (colorThemeAdapter != null) {
            colorThemeAdapter.updateState(colorTheme, mode, style);
        }
    }

    private void updateRadioSelection(ThemeMode mode) {
        isUpdatingRadioState = true;
        if (mode == ThemeMode.DARK) {
            rbThemeDark.setChecked(true);
        } else if (mode == ThemeMode.LIGHT) {
            rbThemeLight.setChecked(true);
        } else {
            rbThemeSystem.setChecked(true);
        }
        isUpdatingRadioState = false;
    }

    private void applyDesignTokens(DesignTokens tokens) {
        if (tokens == null || getView() == null) return;
        layoutSettingsRoot.setBackgroundColor(tokens.getBackgroundColor());
        tvSettingsTitle.setTextColor(tokens.getTextPrimaryColor());
        tvThemeHeader.setTextColor(tokens.getAccentColor());
        if (tvColorThemeHeader != null) tvColorThemeHeader.setTextColor(tokens.getAccentColor());
        btnBack.setColorFilter(tokens.getTextPrimaryColor());

        // Dynamic radio button tint — checked = accent, unchecked = primary text
        android.content.res.ColorStateList radioTint = new android.content.res.ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{
                        tokens.getAccentColor(),
                        tokens.getTextPrimaryColor()
                }
        );
        rbThemeDark.setTextColor(tokens.getTextPrimaryColor());
        rbThemeSystem.setTextColor(tokens.getTextPrimaryColor());
        rbThemeLight.setTextColor(tokens.getTextPrimaryColor());
        rbThemeDark.setButtonTintList(radioTint);
        rbThemeSystem.setButtonTintList(radioTint);
        rbThemeLight.setButtonTintList(radioTint);

        MorphismThemeManager.getInstance().applyToCard(cardThemeContainer, tokens);
    }
}
