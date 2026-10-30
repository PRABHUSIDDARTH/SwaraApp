package com.psthetech.swara.util;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages audio output capability detection using standard Android APIs.
 *
 * Exposes connected devices (speaker, wired, USB, Bluetooth) without attempting
 * to force simultaneous outputs, respecting OEM audio policies.
 */
public class AudioOutputManager {

    private final AudioManager audioManager;

    public AudioOutputManager(Context context) {
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public static class OutputDevice {
        public final int type;
        public final String name;
        public final boolean isCurrent;

        public OutputDevice(int type, String name, boolean isCurrent) {
            this.type = type;
            this.name = name;
            this.isCurrent = isCurrent;
        }
    }

    /**
     * Gets a list of available audio output devices.
     * Marks the currently active devices.
     */
    public List<OutputDevice> getAvailableOutputs() {
        List<OutputDevice> outputs = new ArrayList<>();
        if (audioManager == null) return outputs;

        AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);

        // Find which ones are currently being used for media playback
        List<Integer> activeDeviceIds = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AudioDeviceInfo commDevice = audioManager.getCommunicationDevice();
            if (commDevice != null) {
               activeDeviceIds.add(commDevice.getId());
            }
        } else {
            // Fallback for older versions if needed, but accurate active media route requires MediaRouter.
            // For this implementation, we will mark devices based on AudioManager modes.
            if (audioManager.isBluetoothA2dpOn()) {
                activeDeviceIds.add(-1); // A2DP is active
            } else if (audioManager.isWiredHeadsetOn()) {
                activeDeviceIds.add(-2); // Wired is active
            } else if (audioManager.isSpeakerphoneOn()) {
                activeDeviceIds.add(-3); // Speakerphone is active
            }
        }

        // Just mapping out standard outputs
        for (AudioDeviceInfo device : devices) {
            String name = getDeviceName(device.getType(), device.getProductName());
            boolean isCurrent = false;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (activeDeviceIds.contains(device.getId())) {
                    isCurrent = true;
                }
            } else {
                if (activeDeviceIds.contains(-1) && (device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || device.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET || device.getType() == AudioDeviceInfo.TYPE_BLE_SPEAKER)) {
                    isCurrent = true;
                } else if (activeDeviceIds.contains(-2) && (device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET || device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADPHONES)) {
                    isCurrent = true;
                } else if (activeDeviceIds.contains(-3) && device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    isCurrent = true;
                } else if (activeDeviceIds.isEmpty() && device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    // Default to speaker if nothing else is active pre-S
                    isCurrent = true;
                }
            }

            outputs.add(new OutputDevice(device.getType(), name, isCurrent));
        }

        return outputs;
    }

    private String getDeviceName(int type, CharSequence productName) {
        if (productName != null && productName.length() > 0 && !productName.toString().equals("null")) {
            return productName.toString();
        }
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                return "Speaker";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                return "Wired Headphones";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
            case AudioDeviceInfo.TYPE_BLE_SPEAKER:
                return "Bluetooth Device";
            case AudioDeviceInfo.TYPE_USB_DEVICE:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
            case AudioDeviceInfo.TYPE_USB_ACCESSORY:
                return "USB Audio";
            default:
                return "Unknown Device";
        }
    }
}
