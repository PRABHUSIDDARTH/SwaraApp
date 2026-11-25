package com.psthetech.swara.voice;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.psthetech.swara.BuildConfig;
import com.psthetech.swara.voice.stt.MoonshineStt;
import com.psthetech.swara.voice.stt.SpeechToText;

import ai.onnxruntime.OrtEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Process-wide holder of the on-device voice models (speech-to-text today). Models load once on
 * a background thread; {@link #release()} frees them so a later {@link #loadAsync} reloads.
 *
 * <p>A failed load is sticky: {@link #failed()} stays true and {@link #loadAsync} does not retry
 * until {@link #release()} resets the holder. {@link #stt()} hands out a guarded model: release
 * waits for a running transcription before closing it, and a stale reference used after
 * {@link #release()} throws IllegalStateException instead of touching freed native memory.
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
                if (v == null) {
                    Context app = context.getApplicationContext();
                    Handler main = new Handler(Looper.getMainLooper());
                    instance = v = new VoiceModels(
                            () -> new MoonshineStt(OrtEnvironment.getEnvironment(), app.getAssets(), MOONSHINE_DIR),
                            Executors.newSingleThreadExecutor(r -> {
                                Thread t = new Thread(r, "voice-models");
                                t.setPriority(Thread.MIN_PRIORITY);
                                return t;
                            }),
                            main::post);
                }
            }
        }
        return v;
    }

    private enum State { IDLE, LOADING, READY, FAILED }

    private final Callable<SpeechToText> sttFactory;
    private final Executor loader;
    private final Executor mainThread;
    private final List<Runnable> waiting = new ArrayList<>();

    private State state = State.IDLE;
    private SpeechToText stt;
    /** Bumped by {@link #release()} so a load that was in flight is discarded. */
    private int generation;

    /** For tests: {@code loader} runs loads and closes, {@code mainThread} runs callbacks. */
    VoiceModels(Callable<SpeechToText> sttFactory, Executor loader, Executor mainThread) {
        this.sttFactory = sttFactory;
        this.loader = loader;
        this.mainThread = mainThread;
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
            loaded = sttFactory.call();
        } catch (Throwable t) {
            // Includes UnsatisfiedLinkError / ExceptionInInitializerError from the ORT natives:
            // a failed load must land in FAILED, never crash the process or stay LOADING.
            if (BuildConfig.DEBUG) Log.e(TAG, "voice model load failed", t);
        }
        List<Runnable> callbacks;
        synchronized (this) {
            if (gen != generation) {  // released while loading
                closeQuietly(loaded);
                return;
            }
            stt = loaded != null ? new GuardedStt(loaded) : null;
            state = loaded != null ? State.READY : State.FAILED;
            callbacks = new ArrayList<>(waiting);
            waiting.clear();
        }
        for (Runnable r : callbacks) post(r);
    }

    public synchronized boolean isReady() {
        return state == State.READY;
    }

    /** True after a load failed; stays true until {@link #release()}. */
    public synchronized boolean failed() {
        return state == State.FAILED;
    }

    /** The speech-to-text model, or null until {@link #isReady()}. Invalid after {@link #release()}. */
    public synchronized SpeechToText stt() {
        return stt;
    }

    /**
     * Frees the models and clears a sticky failure; a later {@link #loadAsync} loads them again.
     * Pending callbacks are dropped and any {@link #stt()} reference handed out becomes invalid.
     */
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

    /**
     * Serialises transcribe and close on one lock, so the native model is never closed while a
     * transcription is running (close waits for it), and refuses to transcribe once closed.
     */
    static final class GuardedStt implements SpeechToText {
        private final SpeechToText delegate;
        private boolean closed; // guarded by this

        GuardedStt(SpeechToText delegate) {
            this.delegate = delegate;
        }

        @Override public synchronized String transcribe(float[] audio16k) throws Exception {
            if (closed) throw new IllegalStateException("voice model released");
            return delegate.transcribe(audio16k);
        }

        @Override public synchronized void close() throws Exception {
            if (closed) return;
            closed = true;
            delegate.close();
        }
    }

    private static void closeQuietly(SpeechToText s) {
        if (s == null) return;
        try {
            s.close();
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.w(TAG, "closing voice models", e);
        }
    }

    private void post(Runnable r) {
        if (r != null) mainThread.execute(r);
    }
}
