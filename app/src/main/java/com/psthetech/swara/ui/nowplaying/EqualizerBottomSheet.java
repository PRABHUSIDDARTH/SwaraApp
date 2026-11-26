package com.psthetech.swara.ui.nowplaying;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.psthetech.swara.R;
import com.psthetech.swara.audio.EqPresets;
import com.psthetech.swara.audio.SwaraEqualizer;
import com.psthetech.swara.ui.theme.DesignTokens;
import com.psthetech.swara.ui.theme.MorphismThemeManager;
import com.psthetech.swara.ui.widget.EqBandSlider;

/** In-app equalizer: on/off, preset chips (plus Custom) and five band sliders. */
public class EqualizerBottomSheet extends BottomSheetDialogFragment
        implements SwaraEqualizer.Listener {

    public static final String TAG = "EqualizerBottomSheet";

    public static EqualizerBottomSheet newInstance() { return new EqualizerBottomSheet(); }

    private SwaraEqualizer eq;
    private ChipGroup chips;
    private MaterialSwitch enableSwitch;
    private final EqBandSlider[] sliders = new EqBandSlider[EqPresets.BANDS];
    private final TextView[] dbLabels = new TextView[EqPresets.BANDS];
    private boolean binding;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_equalizer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        eq = SwaraEqualizer.get(requireContext());

        DesignTokens t = MorphismThemeManager.getInstance().getCurrentTokens();
        if (t != null) view.setBackgroundColor(t.getSurfaceColor());
        int accent = t != null ? t.getAccentColor() : 0xFF1DB954;
        int textPrimary = t != null ? t.getTextPrimaryColor() : 0xFFFFFFFF;
        int textSecondary = t != null ? t.getTextSecondaryColor() : 0xB3FFFFFF;

        chips = view.findViewById(R.id.chipsPresets);
        enableSwitch = view.findViewById(R.id.switchEq);

        for (EqPresets.Preset p : EqPresets.PRESETS) addChip(p.id, p.label);
        addChip(EqPresets.CUSTOM, getString(R.string.eq_custom));
        chips.setOnCheckedStateChangeListener((group, ids) -> {
            if (binding || ids.isEmpty()) return;
            Object tag = group.findViewById(ids.get(0)).getTag();
            if (tag != null) eq.selectPreset((String) tag);
        });

        enableSwitch.setOnCheckedChangeListener((b, on) -> {
            if (!binding) eq.setEnabled(on);
        });

        LinearLayout bands = view.findViewById(R.id.bandsContainer);
        float dp = getResources().getDisplayMetrics().density;
        for (int i = 0; i < EqPresets.BANDS; i++) {
            final int band = i;
            LinearLayout col = new LinearLayout(requireContext());
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            bands.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView db = new TextView(requireContext());
            db.setTextColor(textSecondary);
            db.setTextSize(12);
            db.setGravity(Gravity.CENTER);
            dbLabels[i] = db;
            col.addView(db, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            EqBandSlider s = new EqBandSlider(requireContext());
            s.setColors(0x33FFFFFF, accent, textPrimary);
            s.setContentDescription(freqLabel(EqPresets.REF_FREQS_HZ[i]));
            s.setOnChange((mb, fromUser) -> {
                if (fromUser) eq.setBandLevel(band, mb);
            });
            sliders[i] = s;
            col.addView(s, new LinearLayout.LayoutParams((int) (48 * dp), getResources().getDimensionPixelSize(R.dimen.eq_slider_height)));

            TextView hz = new TextView(requireContext());
            hz.setTextColor(textSecondary);
            hz.setTextSize(12);
            hz.setGravity(Gravity.CENTER);
            hz.setText(freqLabel(EqPresets.REF_FREQS_HZ[i]));
            col.addView(hz, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        bind();
    }

    @Override public void onStart() {
        super.onStart();
        eq.addListener(this);
        bind();
    }

    @Override public void onStop() {
        super.onStop();
        eq.removeListener(this);
    }

    @Override public void onEqualizerChanged() {
        View v = getView();
        if (v != null) v.post(this::bind);
    }

    private void addChip(String id, String label) {
        Chip c = new Chip(requireContext());
        c.setText(label);
        c.setCheckable(true);
        c.setTag(id);
        c.setId(View.generateViewId());
        chips.addView(c);
    }

    private void bind() {
        if (chips == null) return;
        binding = true;
        try {
            enableSwitch.setChecked(eq.isEnabled());
            String id = eq.getPresetId();
            for (int i = 0; i < chips.getChildCount(); i++) {
                View c = chips.getChildAt(i);
                if (id.equals(c.getTag())) {
                    chips.check(c.getId());
                    if (chips.getParent() instanceof android.widget.HorizontalScrollView) {
                        android.widget.HorizontalScrollView sv = (android.widget.HorizontalScrollView) chips.getParent();
                        sv.post(() -> sv.smoothScrollTo(Math.max(0, c.getLeft() - 2 * c.getWidth()), 0));
                    }
                    break;
                }
            }
            int[] lv = eq.getLevels();
            for (int i = 0; i < EqPresets.BANDS; i++) {
                sliders[i].setLevelMb(lv[i]);
                sliders[i].setActiveLook(eq.isEnabled());
                int db = lv[i] / 100;
                dbLabels[i].setText((db > 0 ? "+" : "") + db);
            }
        } finally {
            binding = false;
        }
    }

    private static String freqLabel(int hz) {
        return hz >= 1000 ? (hz % 1000 == 0 ? hz / 1000 + "k" : String.format(java.util.Locale.US, "%.1fk", hz / 1000f)) : String.valueOf(hz);
    }
}
