package com.psthetech.swara.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.AudioOutputDevice;
import com.psthetech.swara.ui.theme.DesignTokens;
import com.psthetech.swara.ui.theme.MorphismThemeManager;

import java.util.ArrayList;
import java.util.List;

public class AudioOutputAdapter extends RecyclerView.Adapter<AudioOutputAdapter.ViewHolder> {

    public interface OnDeviceSelectedListener {
        void onDeviceSelected(@NonNull AudioOutputDevice device);
    }

    private final List<AudioOutputDevice> devices = new ArrayList<>();
    private final OnDeviceSelectedListener listener;

    public AudioOutputAdapter(@NonNull OnDeviceSelectedListener listener) {
        this.listener = listener;
    }

    public void setDevices(@NonNull List<AudioOutputDevice> newDevices) {
        this.devices.clear();
        this.devices.addAll(newDevices);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_audio_output_device, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AudioOutputDevice device = devices.get(position);
        holder.bind(device, listener);
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final View container;
        private final ImageView ivDeviceIcon;
        private final TextView tvDeviceName;
        private final TextView tvDeviceType;
        private final ImageView ivSelectedCheck;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.containerDeviceItem);
            ivDeviceIcon = itemView.findViewById(R.id.ivDeviceIcon);
            tvDeviceName = itemView.findViewById(R.id.tvDeviceName);
            tvDeviceType = itemView.findViewById(R.id.tvDeviceType);
            ivSelectedCheck = itemView.findViewById(R.id.ivSelectedCheck);
        }

        void bind(@NonNull AudioOutputDevice device, @NonNull OnDeviceSelectedListener listener) {
            tvDeviceName.setText(device.getName());
            tvDeviceType.setText(device.getTypeDisplayName());

            // Icon by device type
            switch (device.getType()) {
                case BUILT_IN_SPEAKER:
                    ivDeviceIcon.setImageResource(R.drawable.ic_speaker);
                    break;
                case WIRED_HEADSET:
                    ivDeviceIcon.setImageResource(R.drawable.ic_headphones);
                    break;
                case USB_AUDIO:
                    ivDeviceIcon.setImageResource(R.drawable.ic_usb);
                    break;
                case BLUETOOTH:
                case BLUETOOTH_LE:
                    ivDeviceIcon.setImageResource(R.drawable.ic_bluetooth);
                    break;
                default:
                    ivDeviceIcon.setImageResource(R.drawable.ic_speaker);
                    break;
            }

            DesignTokens tokens = MorphismThemeManager.getInstance().getCurrentTokens();
            if (tokens != null) {
                if (device.isCurrent()) {
                    tvDeviceName.setTextColor(tokens.getReadableAccentColor());
                    tvDeviceType.setTextColor(tokens.getReadableAccentColor());
                    ivDeviceIcon.setColorFilter(tokens.getAccentColor());
                    ivSelectedCheck.setVisibility(View.VISIBLE);
                    ivSelectedCheck.setColorFilter(tokens.getAccentColor());
                    container.setBackground(tokens.createSurfaceVariantDrawable(itemView.getContext()));
                } else {
                    tvDeviceName.setTextColor(tokens.getTextPrimaryColor());
                    tvDeviceType.setTextColor(tokens.getTextSecondaryColor());
                    ivDeviceIcon.setColorFilter(tokens.getIconSecondaryColor());
                    ivSelectedCheck.setVisibility(View.GONE);
                    container.setBackgroundResource(R.drawable.ripple_item);
                }
            } else {
                ivSelectedCheck.setVisibility(device.isCurrent() ? View.VISIBLE : View.GONE);
            }

            container.setOnClickListener(v -> listener.onDeviceSelected(device));
        }
    }
}
