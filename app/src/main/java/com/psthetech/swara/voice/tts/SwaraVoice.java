package com.psthetech.swara.voice.tts;

/** Speaks short replies. {@code onDone} runs on the main thread exactly once per speak call. */
public interface SwaraVoice {
    boolean isAvailable();

    void speak(String text, Runnable onDone);

    void shutdown();
}
