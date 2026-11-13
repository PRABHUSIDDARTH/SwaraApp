package com.psthetech.swara.voice.audio;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;

/** 16 kHz mono microphone capture delivering 1280-sample (80 ms) float frames on its own thread. */
public final class MicSource {
    public interface Listener {
        void onFrame(float[] frame1280);

        void onError(Exception e);
    }

    public static final int SAMPLE_RATE = 16000;
    public static final int FRAME = 1280;

    private final Context context;
    private final Object lock = new Object();
    private AudioRecord record;
    private AcousticEchoCanceler aec;
    private Thread thread;
    private volatile boolean running;

    public MicSource(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Returns false when permission is missing or the mic is busy/unavailable. */
    public boolean start(final Listener listener) {
        synchronized (lock) {
            if (running) return false;
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int bufBytes = Math.max(min, 4 * FRAME * 2);
            try {
                record = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufBytes);
                if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                    releaseLocked();
                    return false;
                }
                if (AcousticEchoCanceler.isAvailable()) {
                    try {
                        aec = AcousticEchoCanceler.create(record.getAudioSessionId());
                        if (aec != null) aec.setEnabled(true);
                    } catch (RuntimeException ignored) {
                        // Echo cancellation is best-effort.
                    }
                }
                record.startRecording();
                if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                    releaseLocked();
                    return false;
                }
            } catch (SecurityException | IllegalStateException | IllegalArgumentException e) {
                releaseLocked();
                return false;
            }
            running = true;
            final AudioRecord rec = record;
            thread = new Thread(new Runnable() {
                @Override public void run() {
                    readLoop(rec, listener);
                }
            }, "swara-mic");
            thread.start();
            return true;
        }
    }

    private void readLoop(AudioRecord rec, Listener listener) {
        short[] shorts = new short[FRAME];
        try {
            while (running) {
                int filled = 0;
                while (running && filled < FRAME) {
                    int n = rec.read(shorts, filled, FRAME - filled);
                    if (n < 0) throw new IllegalStateException("AudioRecord.read returned " + n);
                    filled += n;
                }
                if (!running) break;
                float[] frame = new float[FRAME];
                for (int i = 0; i < FRAME; i++) frame[i] = shorts[i] / 32768f;
                listener.onFrame(frame);
            }
        } catch (Exception e) {
            if (running) listener.onError(e);
        }
    }

    /** Idempotent; joins the reader thread. */
    public void stop() {
        Thread t;
        synchronized (lock) {
            running = false;
            t = thread;
            thread = null;
        }
        if (t != null && t != Thread.currentThread()) {
            try {
                t.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (lock) {
            releaseLocked();
        }
    }

    private void releaseLocked() {
        if (aec != null) {
            try { aec.release(); } catch (RuntimeException ignored) { }
            aec = null;
        }
        if (record != null) {
            try {
                if (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) record.stop();
            } catch (RuntimeException ignored) { }
            try { record.release(); } catch (RuntimeException ignored) { }
            record = null;
        }
    }
}
