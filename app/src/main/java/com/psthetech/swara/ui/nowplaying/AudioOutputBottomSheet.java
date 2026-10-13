package com.psthetech.swara.ui.nowplaying;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.AudioOutputDevice;
import com.psthetech.swara.ui.adapter.AudioOutputAdapter;
import com.psthetech.swara.ui.theme.DesignTokens;
import com.psthetech.swara.ui.theme.MorphismThemeManager;
import com.psthetech.swara.util.AudioOutputManager;

public class AudioOutputBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "AudioOutputBottomSheet";

    private ImageView ivActiveDeviceIcon;
    private TextView tvActiveDeviceName;
    private TextView tvActiveStatus;
    private View viewActiveDot;
    private View cardActiveOutput;
    private TextView tvSheetTitle;
    private TextView tvAvailableHeader;
    private ImageView btnCloseOutputSheet;
    private RecyclerView rvAudioOutputs;
    private View layoutMultiOutputBanner;
    private View dragPill;

    private AudioOutputAdapter adapter;
    private AudioOutputManager audioOutputManager;

    public static AudioOutputBottomSheet newInstance() {
        return new AudioOutputBottomSheet();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_audio_output, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        audioOutputManager = AudioOutputManager.getInstance(requireContext());

        dragPill = view.findViewById(R.id.dragPill);
        tvSheetTitle = view.findViewById(R.id.tvSheetTitle);
        btnCloseOutputSheet = view.findViewById(R.id.btnCloseOutputSheet);
        cardActiveOutput = view.findViewById(R.id.cardActiveOutput);
        ivActiveDeviceIcon = view.findViewById(R.id.ivActiveDeviceIcon);
        tvActiveDeviceName = view.findViewById(R.id.tvActiveDeviceName);
        tvActiveStatus = view.findViewById(R.id.tvActiveStatus);
        viewActiveDot = view.findViewById(R.id.viewActiveDot);
        tvAvailableHeader = view.findViewById(R.id.tvAvailableHeader);
        rvAudioOutputs = view.findViewById(R.id.rvAudioOutputs);
        layoutMultiOutputBanner = view.findViewById(R.id.layoutMultiOutputBanner);

        btnCloseOutputSheet.setOnClickListener(v -> dismiss());

        adapter = new AudioOutputAdapter(device -> {
            if (device.isCurrent()) {
                dismiss();
                return;
            }
            boolean handled = audioOutputManager.requestRouting(device);
            if (!handled) {
                Toast.makeText(requireContext(),
                        "Audio is playing through " + device.getName(),
                        Toast.LENGTH_SHORT).show();
            }
            dismiss();
        });

        rvAudioOutputs.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvAudioOutputs.setAdapter(adapter);

        com.google.android.material.button.MaterialButton btnOpenMediaOutput =
                view.findViewById(R.id.btnOpenMediaOutput);
        if (btnOpenMediaOutput != null) {
            btnOpenMediaOutput.setOnClickListener(v -> {
                dismiss();
                openMediaOutputPanel(requireContext());
            });
        }

        // Apply DesignTokens
        MorphismThemeManager.getInstance().getDesignTokens().observe(getViewLifecycleOwner(), tokens -> {
            if (tokens == null || getView() == null) return;
            applyDesignTokens(tokens);
        });

        // Observe outputs
        audioOutputManager.getCurrentOutput().observe(getViewLifecycleOwner(), current -> {
            if (current != null) {
                bindActiveDevice(current);
            }
        });

        audioOutputManager.getAvailableOutputs().observe(getViewLifecycleOwner(), devices -> {
            if (devices != null) {
                adapter.setDevices(devices);
            }
        });

        audioOutputManager.getIsMultiOutputSupported().observe(getViewLifecycleOwner(), isSupported -> {
            if (layoutMultiOutputBanner != null) {
                layoutMultiOutputBanner.setVisibility(Boolean.TRUE.equals(isSupported) ? View.VISIBLE : View.GONE);
            }
        });

        // Force a fresh scan on open
        audioOutputManager.refreshAudioDevices();
    }

    public static void openMediaOutputPanel(@NonNull android.content.Context context) {
        boolean launched = false;

        // 1. Android 10+ Media Output Panel (API 29+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                android.content.Intent panelIntent = new android.content.Intent("android.settings.panel.action.MEDIA_OUTPUT");
                panelIntent.putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.getPackageName());
                panelIntent.putExtra("android.provider.extra.PACKAGE_NAME", context.getPackageName());
                panelIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(panelIntent);
                launched = true;
            } catch (Exception e) {
                android.util.Log.d(TAG, "Standard Media Output panel not available: " + e.getMessage());
            }
        }

        // 2. Samsung OneUI Media Output / Quickboard
        if (!launched) {
            String[][] samsungIntents = {
                    {"com.samsung.android.mdx.quickboard", "com.samsung.android.mdx.quickboard.MediaOutputActivity"},
                    {"com.android.settings", "com.android.settings.panel.MediaOutputPanelActivity"},
                    {"com.samsung.android.app.soundalive", "com.samsung.android.app.soundalive.activity.MediaOutputActivity"}
            };
            for (String[] target : samsungIntents) {
                try {
                    android.content.Intent intent = new android.content.Intent();
                    intent.setComponent(new android.content.ComponentName(target[0], target[1]));
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    launched = true;
                    break;
                } catch (Exception ignored) {}
            }
        }

        // 3. Fallback: Bluetooth settings
        if (!launched) {
            try {
                android.content.Intent btIntent = new android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                btIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(btIntent);
                launched = true;
            } catch (Exception e) {
                try {
                    android.content.Intent soundIntent = new android.content.Intent(android.provider.Settings.ACTION_SOUND_SETTINGS);
                    soundIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(soundIntent);
                    launched = true;
                } catch (Exception ignored) {}
            }
        }

        if (!launched) {
            Toast.makeText(context, "Open Quick Panel to access Samsung Media Output & Dual Audio", Toast.LENGTH_LONG).show();
        }
    }

    private void bindActiveDevice(@NonNull AudioOutputDevice device) {
        tvActiveDeviceName.setText(device.getName());
        tvActiveStatus.setText("Active output • " + device.getTypeDisplayName());

        switch (device.getType()) {
            case BUILT_IN_SPEAKER:
                ivActiveDeviceIcon.setImageResource(R.drawable.ic_speaker);
                break;
            case WIRED_HEADSET:
                ivActiveDeviceIcon.setImageResource(R.drawable.ic_headphones);
                break;
            case USB_AUDIO:
                ivActiveDeviceIcon.setImageResource(R.drawable.ic_usb);
                break;
            case BLUETOOTH:
            case BLUETOOTH_LE:
                ivActiveDeviceIcon.setImageResource(R.drawable.ic_bluetooth);
                break;
            default:
                ivActiveDeviceIcon.setImageResource(R.drawable.ic_speaker);
                break;
        }
    }

    private void applyDesignTokens(@NonNull DesignTokens tokens) {
        View v = getView();
        if (v == null) return;

        v.setBackground(tokens.createAmbientDrawable());

        tvSheetTitle.setTextColor(tokens.getTextPrimaryColor());
        btnCloseOutputSheet.setColorFilter(tokens.getTextSecondaryColor());
        tvAvailableHeader.setTextColor(tokens.getTextTertiaryColor());

        cardActiveOutput.setBackground(tokens.createCardDrawable(requireContext()));
        tvActiveDeviceName.setTextColor(tokens.getTextPrimaryColor());
        tvActiveStatus.setTextColor(tokens.getReadableAccentColor());
        ivActiveDeviceIcon.setColorFilter(tokens.getAccentColor());
        viewActiveDot.setBackgroundTintList(ColorStateList.valueOf(tokens.getAccentColor()));

        if (layoutMultiOutputBanner != null) {
            layoutMultiOutputBanner.setBackground(tokens.createCardDrawable(requireContext()));
            TextView tvHeader = layoutMultiOutputBanner.findViewById(R.id.tvMultiOutputHeader);
            if (tvHeader != null) tvHeader.setTextColor(tokens.getTextPrimaryColor());
            TextView tvMulti = layoutMultiOutputBanner.findViewById(R.id.tvMultiOutputText);
            if (tvMulti != null) tvMulti.setTextColor(tokens.getTextSecondaryColor());
            ImageView ivMulti = layoutMultiOutputBanner.findViewById(R.id.ivMultiOutputIcon);
            if (ivMulti != null) ivMulti.setColorFilter(tokens.getAccentColor());
            com.google.android.material.button.MaterialButton btnOpen =
                    layoutMultiOutputBanner.findViewById(R.id.btnOpenMediaOutput);
            if (btnOpen != null) {
                btnOpen.setBackgroundTintList(ColorStateList.valueOf(tokens.getAccentColor()));
                btnOpen.setTextColor(tokens.getOnAccentColor());
                btnOpen.setIconTint(ColorStateList.valueOf(tokens.getOnAccentColor()));
            }
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }
}
