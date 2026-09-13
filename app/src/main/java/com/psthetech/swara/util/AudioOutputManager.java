package com.psthetech.swara.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.psthetech.swara.domain.model.AudioOutputDevice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages audio output discovery, state observation, and capability-aware routing.
 * Strictly integrates with Media3/ExoPlayer architecture without creating competing players.
 */
public class AudioOutputManager {

    private static final String TAG = "AudioOutputManager";
    private static volatile AudioOutputManager instance;

    private final Context appContext;
    private final AudioManager audioManager;
    private final Handler mainHandler;

    private final MutableLiveData<AudioOutputDevice> currentOutput = new MutableLiveData<>();
    private final MutableLiveData<List<AudioOutputDevice>> availableOutputs = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isMultiOutputSupported = new MutableLiveData<>(false);

    private AudioDeviceCallback deviceCallback;
    private BroadcastReceiver hardwareReceiver;
    private boolean isRegistered = false;

    public static AudioOutputManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (AudioOutputManager.class) {
                if (instance == null) {
                    instance = new AudioOutputManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public AudioOutputManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        this.mainHandler = new Handler(Looper.getMainLooper());

        registerCallbacks();
        refreshAudioDevices();
    }

    /**
     * Registers AudioDeviceCallback and BroadcastReceiver for hardware plug/unplug events.
     */
    public synchronized void registerCallbacks() {
        if (isRegistered) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager != null) {
            deviceCallback = new AudioDeviceCallback() {
                @Override
                public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                    Log.d(TAG, "Audio devices added, refreshing output list");
                    mainHandler.post(AudioOutputManager.this::refreshAudioDevices);
                }

                @Override
                public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                    Log.d(TAG, "Audio devices removed, refreshing output list");
                    mainHandler.post(AudioOutputManager.this::refreshAudioDevices);
                }
            };
            audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler);
        }

        // Hardware intent receiver for older/additional broadcast triggers
        hardwareReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent != null ? intent.getAction() : null;
                Log.d(TAG, "Received audio hardware broadcast: " + action);
                refreshAudioDevices();
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        filter.addAction(Intent.ACTION_HEADSET_PLUG);
        appContext.registerReceiver(hardwareReceiver, filter);

        isRegistered = true;
    }

    /**
     * Unregisters callbacks to avoid memory leaks.
     */
    public synchronized void unregisterCallbacks() {
        if (!isRegistered) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager != null && deviceCallback != null) {
            audioManager.unregisterAudioDeviceCallback(deviceCallback);
            deviceCallback = null;
        }

        if (hardwareReceiver != null) {
            try {
                appContext.unregisterReceiver(hardwareReceiver);
            } catch (Exception ignored) {}
            hardwareReceiver = null;
        }

        isRegistered = false;
    }

    /**
     * Scans AudioManager for audio sink devices and computes current and available outputs.
     */
    public synchronized void refreshAudioDevices() {
        if (audioManager == null) return;

        List<AudioOutputDevice> devices = new ArrayList<>();
        AudioDeviceInfo activeCommDevice = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activeCommDevice = audioManager.getCommunicationDevice();
        }

        AudioDeviceInfo[] rawDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        Map<String, AudioOutputDevice> deduplicated = new LinkedHashMap<>();

        boolean hasBluetooth = false;
        boolean hasWired = false;
        int bluetoothCount = 0;

        for (AudioDeviceInfo info : rawDevices) {
            if (!info.isSink()) continue;

            AudioOutputDevice.OutputType type = AudioOutputDevice.mapDeviceInfoType(info.getType());
            if (type == AudioOutputDevice.OutputType.UNKNOWN) continue;
            // Ignore telephone receiver / internal earpiece in music player UI
            if (info.getType() == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) continue;

            String name = AudioOutputDevice.resolveDeviceName(info, type);
            String id = String.valueOf(info.getId());

            boolean isCurrent = false;
            if (activeCommDevice != null) {
                isCurrent = (info.getId() == activeCommDevice.getId());
            }

            if (type == AudioOutputDevice.OutputType.BLUETOOTH || type == AudioOutputDevice.OutputType.BLUETOOTH_LE) {
                hasBluetooth = true;
                bluetoothCount++;
            } else if (type == AudioOutputDevice.OutputType.WIRED_HEADSET || type == AudioOutputDevice.OutputType.USB_AUDIO) {
                hasWired = true;
            }

            AudioOutputDevice device = new AudioOutputDevice(
                    id, name, type, isCurrent, true, info.getType(), info);

            // Deduplicate by name and type
            String key = type.name() + ":" + name.toLowerCase();
            if (!deduplicated.containsKey(key) || isCurrent) {
                deduplicated.put(key, device);
            }
        }

        // Always ensure Phone Speaker is present as a fallback destination
        boolean hasSpeaker = false;
        for (AudioOutputDevice d : deduplicated.values()) {
            if (d.getType() == AudioOutputDevice.OutputType.BUILT_IN_SPEAKER) {
                hasSpeaker = true;
                break;
            }
        }
        if (!hasSpeaker) {
            AudioOutputDevice defaultSpeaker = new AudioOutputDevice(
                    "builtin_speaker",
                    "Phone Speaker",
                    AudioOutputDevice.OutputType.BUILT_IN_SPEAKER,
                    false,
                    true,
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                    null
            );
            deduplicated.put("BUILT_IN_SPEAKER:speaker", defaultSpeaker);
        }

        devices.addAll(deduplicated.values());

        // Determine current output if not explicitly assigned by communication device
        AudioOutputDevice current = null;
        for (AudioOutputDevice d : devices) {
            if (d.isCurrent()) {
                current = d;
                break;
            }
        }

        if (current == null) {
            // Priority: Bluetooth > Wired/USB > Speaker
            if (hasBluetooth) {
                for (AudioOutputDevice d : devices) {
                    if (d.getType() == AudioOutputDevice.OutputType.BLUETOOTH
                            || d.getType() == AudioOutputDevice.OutputType.BLUETOOTH_LE) {
                        current = d;
                        break;
                    }
                }
            } else if (hasWired) {
                for (AudioOutputDevice d : devices) {
                    if (d.getType() == AudioOutputDevice.OutputType.WIRED_HEADSET
                            || d.getType() == AudioOutputDevice.OutputType.USB_AUDIO) {
                        current = d;
                        break;
                    }
                }
            } else {
                for (AudioOutputDevice d : devices) {
                    if (d.getType() == AudioOutputDevice.OutputType.BUILT_IN_SPEAKER) {
                        current = d;
                        break;
                    }
                }
            }
        }

        if (current == null && !devices.isEmpty()) {
            current = devices.get(0);
        }

        // Update list with current state marked
        List<AudioOutputDevice> finalizedList = new ArrayList<>();
        for (AudioOutputDevice d : devices) {
            boolean isCur = current != null && d.getId().equals(current.getId());
            finalizedList.add(d.copyWithCurrent(isCur));
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            availableOutputs.setValue(finalizedList);
            currentOutput.setValue(current != null ? current.copyWithCurrent(true) : null);
            isMultiOutputSupported.setValue(bluetoothCount > 1);
        } else {
            availableOutputs.postValue(finalizedList);
            currentOutput.postValue(current != null ? current.copyWithCurrent(true) : null);
            isMultiOutputSupported.postValue(bluetoothCount > 1);
        }
    }

    /**
     * Requests routing to the specified audio output device.
     * Guaranteed never to crash playback or corrupt Media3 audio session.
     */
    public boolean requestRouting(@NonNull AudioOutputDevice targetDevice) {
        if (audioManager == null) return false;

        Log.d(TAG, "Requesting audio routing to: " + targetDevice.getName() + " (" + targetDevice.getType() + ")");

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (targetDevice.getType() == AudioOutputDevice.OutputType.BUILT_IN_SPEAKER) {
                    audioManager.clearCommunicationDevice();
                    refreshAudioDevices();
                    return true;
                }

                AudioDeviceInfo info = targetDevice.getAudioDeviceInfo();
                if (info != null) {
                    boolean success = audioManager.setCommunicationDevice(info);
                    if (success) {
                        refreshAudioDevices();
                        return true;
                    } else {
                        Log.w(TAG, "setCommunicationDevice returned false for " + targetDevice.getName());
                    }
                }
            } else {
                // Pre-Android 12 fallback
                if (targetDevice.getType() == AudioOutputDevice.OutputType.BUILT_IN_SPEAKER) {
                    audioManager.setSpeakerphoneOn(true);
                    refreshAudioDevices();
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error executing audio routing request", e);
        }

        return false;
    }

    public LiveData<AudioOutputDevice> getCurrentOutput() {
        return currentOutput;
    }

    public LiveData<List<AudioOutputDevice>> getAvailableOutputs() {
        return availableOutputs;
    }

    public LiveData<Boolean> getIsMultiOutputSupported() {
        return isMultiOutputSupported;
    }
}
