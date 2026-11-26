package com.psthetech.swara.audio;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.Equalizer;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Swara's own equalizer. Holds the user's settings (persisted) and applies them to the
 * player's audio session through {@link Equalizer}. The playback service calls
 * {@link #attach(int)} / {@link #release()}; the UI only changes settings. The UI works with five
 * fixed bands; they are mapped onto the device's real bands when applied.
 */
public final class SwaraEqualizer {

    private static final String TAG = "SwaraEqualizer";
    private static final String PREFS = "swara_equalizer";
    private static final String K_ENABLED = "enabled";
    private static final String K_PRESET = "preset";
    private static final String K_CUSTOM = "custom_levels";

    public interface Listener { void onEqualizerChanged(); }

    private static volatile SwaraEqualizer instance;

    public static SwaraEqualizer get(@NonNull Context context) {
        if (instance == null) {
            synchronized (SwaraEqualizer.class) {
                if (instance == null) instance = new SwaraEqualizer(context.getApplicationContext());
            }
        }
        return instance;
    }

    private final SharedPreferences prefs;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private boolean enabled;
    private String presetId;
    private int[] custom;
    private int[] levels;

    @Nullable private Equalizer effect;

    private SwaraEqualizer(Context app) {
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        enabled = prefs.getBoolean(K_ENABLED, false);
        custom = EqPresets.decode(prefs.getString(K_CUSTOM, null));
        String id = prefs.getString(K_PRESET, "flat");
        presetId = (EqPresets.CUSTOM.equals(id) || EqPresets.find(id) != null) ? id : "flat";
        levels = resolveLevels();
    }

    // ---- state -------------------------------------------------------------------------

    public synchronized boolean isEnabled() { return enabled; }

    public synchronized String getPresetId() { return presetId; }

    /** Current levels of the five UI bands, in millibels. */
    public synchronized int[] getLevels() { return levels.clone(); }

    private int[] resolveLevels() {
        EqPresets.Preset p = EqPresets.find(presetId);
        return p != null ? p.levelsMb() : custom.clone();
    }

    public synchronized void setEnabled(boolean on) {
        enabled = on;
        prefs.edit().putBoolean(K_ENABLED, on).apply();
        apply();
        notifyChanged();
    }

    public synchronized void selectPreset(String id) {
        if (!EqPresets.CUSTOM.equals(id) && EqPresets.find(id) == null) return;
        presetId = id;
        levels = resolveLevels();
        enabled = true; // choosing a preset means you want to hear it
        prefs.edit().putString(K_PRESET, presetId).putBoolean(K_ENABLED, true).apply();
        apply();
        notifyChanged();
    }

    /** Move one band by hand: that always becomes the Custom curve. */
    public synchronized void setBandLevel(int band, int mb) {
        if (band < 0 || band >= EqPresets.BANDS) return;
        int[] next = levels.clone();
        next[band] = EqPresets.snap(mb);
        custom = next;
        levels = next.clone();
        presetId = EqPresets.CUSTOM;
        enabled = true;
        prefs.edit().putString(K_PRESET, presetId).putBoolean(K_ENABLED, true)
                .putString(K_CUSTOM, EqPresets.encode(custom)).apply();
        apply();
        notifyChanged();
    }

    public void addListener(Listener l) { listeners.addIfAbsent(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    private void notifyChanged() {
        for (Listener l : listeners) l.onEqualizerChanged();
    }

    // ---- effect ------------------------------------------------------------------------

    /** Bind to the player's audio session. Safe to call repeatedly and with a bad id. */
    public synchronized void attach(int audioSessionId) {
        releaseEffect();
        if (audioSessionId <= 0) return;
        try {
            effect = new Equalizer(0, audioSessionId);
            apply();
        } catch (Exception | Error e) {
            Log.w(TAG, "Equalizer unavailable for session " + audioSessionId, e);
            effect = null;
        }
    }

    public synchronized void release() { releaseEffect(); }

    private void releaseEffect() {
        if (effect != null) {
            try { effect.release(); } catch (Exception ignored) { }
            effect = null;
        }
    }

    private void apply() {
        Equalizer eq = effect;
        if (eq == null) return;
        try {
            short bands = eq.getNumberOfBands();
            short[] range = eq.getBandLevelRange();
            int[] centers = new int[bands];
            for (short i = 0; i < bands; i++) centers[i] = eq.getCenterFreq(i);
            int[] target = EqPresets.mapToDevice(levels, centers, range[0], range[1]);
            for (short i = 0; i < bands; i++) eq.setBandLevel(i, (short) target[i]);
            eq.setEnabled(enabled);
        } catch (Exception e) {
            Log.w(TAG, "Failed to apply equalizer settings", e);
        }
    }
}
