package com.psthetech.swara.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

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
import com.psthetech.swara.wrapped.backup.HistoryBackupRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
    private TextView tvLibraryHeader;
    private com.google.android.material.card.MaterialCardView cardLibraryContainer;
    private com.google.android.material.materialswitch.MaterialSwitch switchHideShortClips;
    private com.google.android.material.materialswitch.MaterialSwitch switchAutoplay;
    private TextView tvHistoryHeader;
    private TextView tvHistoryBody;
    private MaterialCardView cardHistoryContainer;
    private View btnHistoryExport;
    private View btnHistoryImport;
    private View historyProgress;

    private final ActivityResultLauncher<String> createHistoryFile =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                if (uri == null) return;
                setHistoryBusy(true);
                new HistoryBackupRepository(requireContext()).export(uri, new HistoryBackupRepository.Callback<Integer>() {
                    @Override public void onDone(Integer songs) {
                        if (!isAdded()) return;
                        setHistoryBusy(false);
                        toast(getString(R.string.history_export_done));
                    }

                    @Override public void onError(@NonNull Exception e) {
                        if (!isAdded()) return;
                        setHistoryBusy(false);
                        toast(getString(R.string.history_export_failed));
                    }
                });
            });

    private final ActivityResultLauncher<String[]> openHistoryFile =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                setHistoryBusy(true);
                new HistoryBackupRepository(requireContext()).importFrom(uri,
                        new HistoryBackupRepository.Callback<HistoryBackupRepository.ImportResult>() {
                            @Override public void onDone(HistoryBackupRepository.ImportResult r) {
                                if (!isAdded()) return;
                                setHistoryBusy(false);
                                toast(r.daysAdded == 0 ? getString(R.string.history_import_nothing)
                                        : getResources().getQuantityString(R.plurals.history_import_done,
                                        r.daysAdded, r.daysAdded, r.matchedSongs, r.unmatchedSongs));
                            }

                            @Override public void onError(@NonNull Exception e) {
                                if (!isAdded()) return;
                                setHistoryBusy(false);
                                toast(getString(R.string.history_import_failed));
                            }
                        });
            });

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

        tvLibraryHeader = view.findViewById(R.id.tvLibraryHeader);
        cardLibraryContainer = view.findViewById(R.id.cardLibraryContainer);
        switchHideShortClips = view.findViewById(R.id.switchHideShortClips);
        switchHideShortClips.setChecked(
                com.psthetech.swara.data.preference.LibraryPreferences.hideShortClips(requireContext()));
        switchHideShortClips.setOnCheckedChangeListener((b, checked) -> {
            com.psthetech.swara.data.preference.LibraryPreferences.setHideShortClips(requireContext(), checked);
            new androidx.lifecycle.ViewModelProvider(requireActivity())
                    .get(com.psthetech.swara.ui.viewmodel.LibraryViewModel.class).refreshAll();
        });

        switchAutoplay = view.findViewById(R.id.switchAutoplay);
        switchAutoplay.setChecked(
                com.psthetech.swara.data.preference.LibraryPreferences.autoplayEnabled(requireContext()));
        switchAutoplay.setOnCheckedChangeListener((b, checked) ->
                com.psthetech.swara.data.preference.LibraryPreferences.setAutoplayEnabled(requireContext(), checked));

        tvHistoryHeader = view.findViewById(R.id.tvHistoryHeader);
        tvHistoryBody = view.findViewById(R.id.tvHistoryBody);
        cardHistoryContainer = view.findViewById(R.id.cardHistoryContainer);
        btnHistoryExport = view.findViewById(R.id.btnHistoryExport);
        btnHistoryImport = view.findViewById(R.id.btnHistoryImport);
        historyProgress = view.findViewById(R.id.historyProgress);
        btnHistoryExport.setOnClickListener(v -> createHistoryFile.launch(
                "swara-wrapped-" + new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()) + ".json"));
        btnHistoryImport.setOnClickListener(v -> openHistoryFile.launch(new String[]{"application/json", "text/plain", "application/octet-stream"}));

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

    private void setHistoryBusy(boolean busy) {
        if (getView() == null) return;
        historyProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        btnHistoryExport.setEnabled(!busy);
        btnHistoryImport.setEnabled(!busy);
    }

    private void toast(String message) {
        if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
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
        if (tvLibraryHeader != null) tvLibraryHeader.setTextColor(tokens.getAccentColor());
        if (cardLibraryContainer != null) MorphismThemeManager.getInstance().applyToCard(cardLibraryContainer, tokens);
        if (switchHideShortClips != null) switchHideShortClips.setTextColor(tokens.getTextPrimaryColor());
        if (switchAutoplay != null) switchAutoplay.setTextColor(tokens.getTextPrimaryColor());
        if (tvHistoryHeader != null) tvHistoryHeader.setTextColor(tokens.getAccentColor());
        if (tvHistoryBody != null) tvHistoryBody.setTextColor(tokens.getTextSecondaryColor());
        if (cardHistoryContainer != null) MorphismThemeManager.getInstance().applyToCard(cardHistoryContainer, tokens);
    }
}
