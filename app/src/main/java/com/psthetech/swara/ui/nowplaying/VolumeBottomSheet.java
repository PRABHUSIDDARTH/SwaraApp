package com.psthetech.swara.ui.nowplaying;

import android.content.Context;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.slider.Slider;
import com.psthetech.swara.R;
import com.psthetech.swara.ui.theme.DesignTokens;
import com.psthetech.swara.ui.theme.MorphismThemeManager;
import com.psthetech.swara.util.AudioOutputManager;

/**
 * Media (STREAM_MUSIC) volume slider. Stays in sync with the hardware volume keys through a
 * ContentObserver that lives only while the sheet is visible. Switching output device stays one
 * tap away via the row that opens {@link AudioOutputBottomSheet}.
 */
public class VolumeBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "VolumeBottomSheet";

    private AudioManager audioManager;
    private Slider slider;
    private TextView tvPercent;
    private ImageView ivVolumeIcon;
    private boolean userDragging = false;
    @Nullable private ContentObserver volumeObserver;

    public static VolumeBottomSheet newInstance() {
        return new VolumeBottomSheet();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_volume, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        audioManager = (AudioManager) requireContext().getSystemService(Context.AUDIO_SERVICE);

        slider = view.findViewById(R.id.sliderVolume);
        tvPercent = view.findViewById(R.id.tvVolumePercent);
        ivVolumeIcon = view.findViewById(R.id.ivVolumeIcon);
        view.findViewById(R.id.btnCloseVolumeSheet).setOnClickListener(v -> dismiss());

        int max = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        slider.setValueTo(max);
        slider.setStepSize(1f);
        syncFromSystem();

        slider.addOnChangeListener((s, value, fromUser) -> {
            if (!fromUser) return;
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(value), 0);
            } catch (SecurityException ignored) { /* some DND states refuse; slider re-syncs below */ }
            updateLabels(Math.round(value));
        });
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(@NonNull Slider s) { userDragging = true; }
            @Override public void onStopTrackingTouch(@NonNull Slider s) {
                userDragging = false;
                syncFromSystem();
            }
        });

        // Output device row -> existing picker. Grab the manager before dismissing.
        TextView tvDevice = view.findViewById(R.id.tvOutputDeviceName);
        ImageView ivDevice = view.findViewById(R.id.ivOutputDeviceIcon);
        view.findViewById(R.id.rowOutputDevice).setOnClickListener(v -> {
            FragmentManager fm = getParentFragmentManager();
            dismiss();
            AudioOutputBottomSheet.newInstance().show(fm, AudioOutputBottomSheet.TAG);
        });
        AudioOutputManager.getInstance(requireContext()).getCurrentOutput()
                .observe(getViewLifecycleOwner(), device -> {
                    if (device == null) return;
                    tvDevice.setText(device.getName());
                    ivDevice.setImageResource(device.getIconResId());
                });

        MorphismThemeManager.getInstance().getDesignTokens()
                .observe(getViewLifecycleOwner(), tokens -> {
                    if (tokens != null) applyTokens(view, tokens);
                });
    }

    @Override
    public void onStart() {
        super.onStart();
        syncFromSystem(); // volume may have changed while the sheet was stopped
        volumeObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange) {
                if (!userDragging) syncFromSystem();
            }
        };
        requireContext().getContentResolver().registerContentObserver(
                Settings.System.CONTENT_URI, true, volumeObserver);
    }

    @Override
    public void onStop() {
        if (volumeObserver != null) {
            requireContext().getContentResolver().unregisterContentObserver(volumeObserver);
            volumeObserver = null;
        }
        super.onStop();
    }

    private void syncFromSystem() {
        if (slider == null || audioManager == null) return;
        int vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        float clamped = Math.max(slider.getValueFrom(), Math.min(slider.getValueTo(), vol));
        if (slider.getValue() != clamped) slider.setValue(clamped);
        updateLabels(vol);
    }

    private void updateLabels(int vol) {
        int max = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        tvPercent.setText(getString(R.string.volume_percent, Math.round(100f * vol / max)));
        ivVolumeIcon.setAlpha(vol == 0 ? 0.4f : 1f);
    }

    private void applyTokens(@NonNull View root, @NonNull DesignTokens tokens) {
        root.setBackground(tokens.createAmbientDrawable());
        ((TextView) root.findViewById(R.id.tvVolumeTitle)).setTextColor(tokens.getTextPrimaryColor());
        ((ImageView) root.findViewById(R.id.btnCloseVolumeSheet)).setColorFilter(tokens.getTextSecondaryColor());
        ivVolumeIcon.setColorFilter(tokens.getAccentColor());
        tvPercent.setTextColor(tokens.getTextSecondaryColor());
        slider.setTrackActiveTintList(ColorStateList.valueOf(tokens.getAccentColor()));
        slider.setThumbTintList(ColorStateList.valueOf(tokens.getAccentColor()));
        ((TextView) root.findViewById(R.id.tvOutputLabel)).setTextColor(tokens.getTextTertiaryColor());
        ((TextView) root.findViewById(R.id.tvOutputDeviceName)).setTextColor(tokens.getTextPrimaryColor());
        ((TextView) root.findViewById(R.id.tvOutputChange)).setTextColor(tokens.getReadableAccentColor());
        ((ImageView) root.findViewById(R.id.ivOutputDeviceIcon)).setColorFilter(tokens.getAccentColor());
    }
}
