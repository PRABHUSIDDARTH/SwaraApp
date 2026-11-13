package com.psthetech.swara.voice.audio;

import java.util.Arrays;

/**
 * Collects one spoken command from a stream of 80 ms frames. Frames before the first speech
 * frame are discarded; recording ends after 0.8 s of trailing silence or a 6 s cap.
 */
public final class CommandRecorder {
    public enum State { WAITING, RECORDING, DONE, TIMEOUT }

    private static final int MAX_FRAMES = 75;      // 6 s at 80 ms
    private static final int SILENCE_FRAMES = 10;  // 0.8 s

    private final EnergyVad vad;
    private final int sampleRate;
    private float[] buf = new float[0];
    private int len;
    private int framesRecorded;
    private int waitedFrames;
    private int silentRun;
    private State state = State.WAITING;

    public CommandRecorder(EnergyVad vad, int sampleRate) {
        this.vad = vad;
        this.sampleRate = sampleRate;
    }

    public int sampleRate() {
        return sampleRate;
    }

    public State push(float[] frame) {
        if (state == State.DONE || state == State.TIMEOUT) return state;
        boolean speech = vad.isSpeech(frame);
        if (state == State.WAITING) {
            if (!speech) {
                if (++waitedFrames >= MAX_FRAMES) state = State.TIMEOUT;
                return state;
            }
            state = State.RECORDING;
        }
        append(frame);
        framesRecorded++;
        silentRun = speech ? 0 : silentRun + 1;
        if (silentRun >= SILENCE_FRAMES || framesRecorded >= MAX_FRAMES) state = State.DONE;
        return state;
    }

    /** Read-only diagnostics (public so the session controller can log them). */
    public float debugFloor() { return vad.debugFloor(); }
    public boolean debugLastSpeech() { return vad.debugLastSpeech(); }
    public int debugWaitedFrames() { return waitedFrames; }
    public int debugRecordedSamples() { return len; }

    public float[] audio() {
        return Arrays.copyOf(buf, len);
    }

    private void append(float[] frame) {
        if (len + frame.length > buf.length) {
            buf = Arrays.copyOf(buf, Math.max(buf.length * 2, len + frame.length));
        }
        System.arraycopy(frame, 0, buf, len, frame.length);
        len += frame.length;
    }
}
