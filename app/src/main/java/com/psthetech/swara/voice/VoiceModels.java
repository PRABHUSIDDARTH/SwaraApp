package com.psthetech.swara.voice;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.psthetech.swara.voice.stt.MoonshineStt;
import com.psthetech.swara.voice.stt.SpeechToText;

import ai.onnxruntime.OrtEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Process-wide holder of the on-device voice models (speech-to-text today). Models load once on
 * a background thread; {@link #release()} frees them so a later {@link #loadAsync} reloads.
 */
public final class VoiceModels {

    private static final String TAG = "VoiceModels";
    static final String MOONSHINE_DIR = "voice/moonshine";

    private static volatile VoiceModels instance;

    public static VoiceModels get(Context context) {
        VoiceModels v = instance;
        if (v == null) {
            synchronized (VoiceModels.class) {
                v = instance;
                if (v == null) instance = v = new VoiceModels(context.getApplicationContext());
            }
        }
        return v;
    }

    private enum State { IDLE, LOADING, READY, FAILED }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "voice-models");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private final List<Runnable> waiting = new ArrayList<>();

    private State state = State.IDLE;
    private SpeechToText stt;
    /** Bumped by {@link #release()} so a load that was in flight is discarded. */
    private int generation;

    private VoiceModels(Context app) {
        this.app = app;
    }

    /**
     * Loads the models in the background (once) and posts {@code onReady} to the main thread when
     * loading has finished — successfully or not; check {@link #isReady()} / {@link #failed()}.
     * If loading already finished, {@code onReady} is posted straight away. May be null.
     */
    public void loadAsync(Runnable onReady) {
        synchronized (this) {
            if (state == State.READY || state == State.FAILED) {
                post(onReady);
                return;
            }
            if (onReady != null) waiting.add(onReady);
            if (state == State.LOADING) return;
            state = State.LOADING;
            final int gen = generation;
            loader.execute(() -> load(gen));
        }
    }

    private void load(int gen) {
        SpeechToText loaded = null;
        try {
            loaded = new MoonshineStt(OrtEnvironment.getEnvironment(), app.getAssets(), MOONSHINE_DIR);
        } catch (Exception | OutOfMemoryError e) {
            Log.e(TAG, "voice model load failed", e);
        }
        List<Runnable> callbacks;
        synchronized (this) {
            if (gen != generation) {  // released while loading
                closeQuietly(loaded);
                return;
            }
            stt = loaded;
            state = loaded != null ? State.READY : State.FAILED;
            callbacks = new ArrayList<>(waiting);
            waiting.clear();
        }
        for (Runnable r : callbacks) post(r);
    }

    public synchronized boolean isReady() {
        return state == State.READY;
    }

    public synchronized boolean failed() {
        return state == State.FAILED;
    }

    /** The speech-to-text model, or null until {@link #isReady()}. */
    public synchronized SpeechToText stt() {
        return stt;
    }

    /** Frees the models; a later {@link #loadAsync} loads them again. Pending callbacks are dropped. */
    public void release() {
        SpeechToText old;
        synchronized (this) {
            old = stt;
            stt = null;
            state = State.IDLE;
            generation++;
            waiting.clear();
        }
        if (old != null) loader.execute(() -> closeQuietly(old));
    }

    private static void closeQuietly(SpeechToText s) {
        if (s == null) return;
        try {
            s.close();
        } catch (Exception e) {
            Log.w(TAG, "closing voice models", e);
        }
    }

    private void post(Runnable r) {
        if (r != null) main.post(r);
    }
}
