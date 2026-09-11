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
import com.psthetech.swara.domain.model.MorphismStyle;
import com.psthetech.swara.domain.model.ThemeMode;
import com.psthetech.swara.ui.theme.DesignTokens;
import com.psthetech.swara.ui.theme.MorphismThemeManager;

public class SettingsFragment extends Fragment {

    private View layoutSettingsRoot;
    private ImageButton btnBack;
    private TextView tvSettingsTitle;
    private TextView tvThemeHeader;
    private TextView tvMorphismHeader;
    private MaterialCardView cardThemeContainer;
    private RadioGroup rgTheme;
    private RadioButton rbThemeDark;
    private RadioButton rbThemeSystem;
    private RadioButton rbThemeLight;
    private RecyclerView rvMorphismStyles;

    private MorphismPreviewAdapter adapter;
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
        tvMorphismHeader = view.findViewById(R.id.tvMorphismHeader);
        cardThemeContainer = view.findViewById(R.id.cardThemeContainer);
        rgTheme = view.findViewById(R.id.rgTheme);
        rbThemeDark = view.findViewById(R.id.rbThemeDark);
        rbThemeSystem = view.findViewById(R.id.rbThemeSystem);
        rbThemeLight = view.findViewById(R.id.rbThemeLight);
        rvMorphismStyles = view.findViewById(R.id.rvMorphismStyles);

        btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        rvMorphismStyles.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        MorphismThemeManager manager = MorphismThemeManager.getInstance();

        ThemeMode initialTheme = manager.getActiveThemeMode().getValue();
        if (initialTheme == null) initialTheme = ThemeMode.DARK;

        MorphismStyle initialStyle = manager.getActiveStyle().getValue();
        if (initialStyle == null) initialStyle = MorphismStyle.LIQUID_GLASS;

        adapter = new MorphismPreviewAdapter(initialStyle, initialTheme, selectedStyle -> {
            manager.setMorphismStyle(requireContext(), selectedStyle);
        });
        rvMorphismStyles.setAdapter(adapter);

        updateRadioSelection(initialTheme);

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
        manager.getActiveStyle().observe(getViewLifecycleOwner(), style -> {
            ThemeMode mode = manager.getActiveThemeMode().getValue();
            if (mode == null) mode = ThemeMode.DARK;
            adapter.updateState(style, mode);
        });
        manager.getActiveThemeMode().observe(getViewLifecycleOwner(), mode -> {
            updateRadioSelection(mode);
            MorphismStyle style = manager.getActiveStyle().getValue();
            if (style == null) style = MorphismStyle.LIQUID_GLASS;
            adapter.updateState(style, mode);
        });
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
        tvSettingsTitle.setTextColor(tokens.getPrimaryTextColor());
        tvThemeHeader.setTextColor(tokens.getAccentColor());
        tvMorphismHeader.setTextColor(tokens.getAccentColor());
        btnBack.setColorFilter(tokens.getPrimaryTextColor());
        MorphismThemeManager.getInstance().applyToCard(cardThemeContainer, tokens);
    }
}
