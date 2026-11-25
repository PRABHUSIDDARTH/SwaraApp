package com.psthetech.swara.voice.stt;

/** Offline speech-to-text. Call {@link #transcribe} off the main thread. */
public interface SpeechToText extends AutoCloseable {

    /** Transcribes 16 kHz mono float PCM in [-1, 1]; returns "" when nothing was recognised. */
    String transcribe(float[] audio16k) throws Exception;
}
