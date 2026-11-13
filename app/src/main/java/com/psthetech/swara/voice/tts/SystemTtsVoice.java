package com.psthetech.swara.voice.tts;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Offline replies via the system TextToSpeech engine, using an installed local English voice.
 * Construct on the main thread.
 */
public final class SystemTtsVoice implements SwaraVoice {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ConcurrentHashMap<String, Runnable> pending = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger();
    private volatile boolean available;
    private volatile boolean shut;
    private volatile TextToSpeech tts;

    public SystemTtsVoice(Context context) {
        tts = new TextToSpeech(context.getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override public void onInit(int status) {
                TextToSpeech engine = tts;
                if (status != TextToSpeech.SUCCESS || shut || engine == null) {
                    closeEngine();
                    return;
                }
                available = selectVoice(engine);
                if (!available) closeEngine();
            }
        });
        tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }
            @Override public void onDone(String utteranceId) { finish(utteranceId); }
            @Override public void onError(String utteranceId) { finish(utteranceId); }
            @Override public void onStop(String utteranceId, boolean interrupted) { finish(utteranceId); }
        });
    }

    private boolean selectVoice(TextToSpeech tts) {
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null) return false;
            Voice best = null;
            for (Voice v : voices) {
                if (v.getLocale() == null || !"en".equals(v.getLocale().getLanguage())) continue;
                if (v.isNetworkConnectionRequired()) continue;
                boolean installed = v.getFeatures() == null
                        || !v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED);
                if (installed) {
                    best = v;
                    break;
                }
            }
            if (best == null) return false;
            return tts.setVoice(best) == TextToSpeech.SUCCESS;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override public boolean isAvailable() {
        return available && !shut;
    }

    @Override public void speak(String text, final Runnable onDone) {
        if (!isAvailable() || text == null || text.isEmpty()) {
            if (onDone != null) main.post(onDone);
            return;
        }
        String id = "swara-" + counter.incrementAndGet();
        pending.put(id, onDone != null ? onDone : new Runnable() { @Override public void run() { } });
        int r;
        try {
            r = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
        } catch (RuntimeException e) {
            r = TextToSpeech.ERROR;
        }
        if (r != TextToSpeech.SUCCESS) finish(id);
    }

    private void finish(String id) {
        final Runnable r = pending.remove(id);
        if (r != null) main.post(r);
    }

    private void closeEngine() {
        TextToSpeech engine = tts;
        tts = null;
        if (engine != null) {
            try {
                engine.stop();
                engine.shutdown();
            } catch (RuntimeException ignored) { }
        }
    }

    @Override public void shutdown() {
        shut = true;
        available = false;
        closeEngine();
        for (String id : pending.keySet()) finish(id);
    }
}
