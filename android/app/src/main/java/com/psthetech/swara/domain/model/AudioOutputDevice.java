package com.psthetech.swara.domain.model;

import android.media.AudioDeviceInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Domain model representing an audio output destination.
 * Abstracts Android AudioDeviceInfo into capability-aware, theme-ready device entries.
 */
public class AudioOutputDevice {

    public enum OutputType {
        BUILT_IN_SPEAKER,
        WIRED_HEADSET,
        USB_AUDIO,
        BLUETOOTH,
        BLUETOOTH_LE,
        UNKNOWN
    }

    private final String id;
    private final String name;
    private final OutputType type;
    private final boolean isCurrent;
    private final boolean isSelectable;
    private final int rawType;
    @Nullable private final AudioDeviceInfo audioDeviceInfo;

    public AudioOutputDevice(@NonNull String id,
                             @NonNull String name,
                             @NonNull OutputType type,
                             boolean isCurrent,
                             boolean isSelectable,
                             int rawType,
                             @Nullable AudioDeviceInfo audioDeviceInfo) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.isCurrent = isCurrent;
        this.isSelectable = isSelectable;
        this.rawType = rawType;
        this.audioDeviceInfo = audioDeviceInfo;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public OutputType getType() {
        return type;
    }

    public boolean isCurrent() {
        return isCurrent;
    }

    public boolean isSelectable() {
        return isSelectable;
    }

    public int getRawType() {
        return rawType;
    }

    @Nullable
    public AudioDeviceInfo getAudioDeviceInfo() {
        return audioDeviceInfo;
    }

    public String getDisplayName() {
        return name;
    }

    public int getIconResId() {
        switch (type) {
            case BUILT_IN_SPEAKER:
                return com.psthetech.swara.R.drawable.ic_speaker;
            case WIRED_HEADSET:
                return com.psthetech.swara.R.drawable.ic_headphones;
            case USB_AUDIO:
                return com.psthetech.swara.R.drawable.ic_usb;
            case BLUETOOTH:
            case BLUETOOTH_LE:
                return com.psthetech.swara.R.drawable.ic_bluetooth;
            default:
                return com.psthetech.swara.R.drawable.ic_speaker;
        }
    }

    public String getTypeDisplayName() {
        switch (type) {
            case BUILT_IN_SPEAKER:
                return "Phone Speaker";
            case WIRED_HEADSET:
                return "Wired Audio / Headphones";
            case USB_AUDIO:
                return "USB-C Audio";
            case BLUETOOTH:
                return "Bluetooth Audio";
            case BLUETOOTH_LE:
                return "Bluetooth LE Audio";
            default:
                return "Audio Output";
        }
    }

    /**
     * Maps Android AudioDeviceInfo type constant to Swara OutputType enum.
     */
    @NonNull
    public static OutputType mapDeviceInfoType(int deviceType) {
        switch (deviceType) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:
                return OutputType.BUILT_IN_SPEAKER;

            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
            case AudioDeviceInfo.TYPE_LINE_ANALOG:
            case AudioDeviceInfo.TYPE_LINE_DIGITAL:
                return OutputType.WIRED_HEADSET;

            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_ACCESSORY:
            case 22: // AudioDeviceInfo.TYPE_USB_HEADSET (API 26+)
                return OutputType.USB_AUDIO;

            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
            case 23: // AudioDeviceInfo.TYPE_HEARING_AID (API 28+)
                return OutputType.BLUETOOTH;

            case 26: // AudioDeviceInfo.TYPE_BLE_HEADSET (API 31+)
            case 27: // AudioDeviceInfo.TYPE_BLE_SPEAKER (API 31+)
            case 30: // AudioDeviceInfo.TYPE_BLE_BROADCAST (API 33+)
                return OutputType.BLUETOOTH_LE;

            default:
                return OutputType.UNKNOWN;
        }
    }

    /**
     * Builds a human-friendly display name from AudioDeviceInfo.
     */
    @NonNull
    public static String resolveDeviceName(@NonNull AudioDeviceInfo info, @NonNull OutputType type) {
        CharSequence label = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            label = info.getProductName();
        }
        if (label != null && label.length() > 0) {
            return label.toString().trim();
        }

        switch (type) {
            case BUILT_IN_SPEAKER:
                return "Phone Speaker";
            case WIRED_HEADSET:
                return "Wired Headphones";
            case USB_AUDIO:
                return "USB-C Audio Device";
            case BLUETOOTH:
                return "Bluetooth Audio Device";
            case BLUETOOTH_LE:
                return "Bluetooth LE Device";
            default:
                return "Audio Output";
        }
    }

    public AudioOutputDevice copyWithCurrent(boolean isCurrent) {
        return new AudioOutputDevice(id, name, type, isCurrent, isSelectable, rawType, audioDeviceInfo);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AudioOutputDevice)) return false;
        AudioOutputDevice that = (AudioOutputDevice) o;
        return rawType == that.rawType &&
                id.equals(that.id) &&
                type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, rawType);
    }

    @NonNull
    @Override
    public String toString() {
        return "AudioOutputDevice{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", isCurrent=" + isCurrent +
                '}';
    }
}
