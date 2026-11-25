package com.psthetech.swara.voice.audio;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.os.Build;

import com.psthetech.swara.BuildConfig;
import com.psthetech.swara.voice.debug.VoiceDiag;

/** 16 kHz mono microphone capture delivering 1280-sample (80 ms) float frames on its own thread. */
public final class MicSource {
    public interface Listener {
        /**
         * Runs on the "swara-mic" reader thread. Must be cheap: hand real work off to another
         * thread, or capture falls behind.
         */
        void onFrame(float[] frame1280);

        /** Called at most once per session, on the reader thread, never after stop(). */
        void onError(Exception e);
    }

    public static final int SAMPLE_RATE = 16000;
    public static final int FRAME = 1280;
    private static final long JOIN_MS = 200;

    /** One start()..stop() session. Its thread only ever touches its own state. */
    private static final class Session {
        volatile boolean active = true;
        AudioRecord record;
        AcousticEchoCanceler aec;
        Thread thread;

        synchronized void release() {
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

    private final Context context;
    private final Object lock = new Object();
    private Session current;

    public MicSource(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Returns false when permission is missing, already running, or the mic is busy. */
    public boolean start(final Listener listener) {
        synchronized (lock) {
            if (current != null) return false;
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                if (BuildConfig.DEBUG) VoiceDiag.d("mic start failed: RECORD_AUDIO not granted");
                return false;
            }
            final Session s = new Session();
            int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int bufBytes = Math.max(min, 4 * FRAME * 2);
            try {
                s.record = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufBytes);
                if (s.record.getState() != AudioRecord.STATE_INITIALIZED) {
                    s.release();
                    if (BuildConfig.DEBUG) VoiceDiag.d("mic start failed: AudioRecord state != INITIALIZED");
                    return false;
                }
                if (AcousticEchoCanceler.isAvailable()) {
                    try {
                        s.aec = AcousticEchoCanceler.create(s.record.getAudioSessionId());
                        if (s.aec != null) s.aec.setEnabled(true);
                    } catch (RuntimeException ignored) {
                        // Echo cancellation is best-effort.
                    }
                }
                s.record.startRecording();
                if (s.record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                    s.release();
                    if (BuildConfig.DEBUG) VoiceDiag.d("mic start failed: recordingState != RECORDING after startRecording");
                    return false;
                }
                if (BuildConfig.DEBUG) logStart(s, bufBytes, min);
            } catch (SecurityException | IllegalStateException | IllegalArgumentException e) {
                s.release();
                if (BuildConfig.DEBUG) VoiceDiag.d("mic start failed: " + e);
                return false;
            }
            s.thread = new Thread(new Runnable() {
                @Override public void run() {
                    readLoop(s, listener);
                }
            }, "swara-mic");
            current = s;
            s.thread.start();
            return true;
        }
    }

    private static void logStart(Session s, int bufBytes, int minBuf) {
        try {
            AudioRecord r = s.record;
            String dev = "n/a";
            if (Build.VERSION.SDK_INT >= 24) {
                android.media.AudioDeviceInfo d = r.getRoutedDevice();
                dev = d == null ? "null" : "type=" + d.getType() + " name=" + d.getProductName();
            }
            VoiceDiag.d("mic started: requestedSource=VOICE_RECOGNITION(" + MediaRecorder.AudioSource.VOICE_RECOGNITION
                    + ") requestedRate=" + SAMPLE_RATE + " actualRate=" + r.getSampleRate()
                    + " channels=" + r.getChannelCount() + " format=" + r.getAudioFormat()
                    + " actualSource=" + r.getAudioSource() + " bufBytes=" + bufBytes + " (min=" + minBuf + ")"
                    + " aecAvailable=" + AcousticEchoCanceler.isAvailable()
                    + " aecEnabled=" + (s.aec != null && s.aec.getEnabled())
                    + " routedDevice=" + dev + " sdk=" + Build.VERSION.SDK_INT);
        } catch (RuntimeException e) {
            VoiceDiag.d("mic start log failed: " + e);
        }
    }

    private void readLoop(Session s, Listener listener) {
        AudioRecord rec = s.record;
        short[] shorts = new short[FRAME];
        try {
            while (s.active) {
                int filled = 0;
                while (s.active && filled < FRAME) {
                    int n = rec.read(shorts, filled, FRAME - filled);
                    if (n < 0) throw new IllegalStateException("AudioRecord.read returned " + n);
                    if (n == 0) {
                        Thread.sleep(2);
                        continue;
                    }
                    filled += n;
                }
                if (!s.active) return;
                float[] frame = new float[FRAME];
                for (int i = 0; i < FRAME; i++) frame[i] = shorts[i] / 32768f;
                listener.onFrame(frame);
            }
        } catch (InterruptedException e) {
            // stopped
        } catch (Exception e) {
            if (s.active) {
                s.active = false;
                synchronized (lock) {
                    if (current == s) current = null;
                }
                try {
                    listener.onError(e);
                } finally {
                    s.release();
                }
            }
        }
    }

    /** Idempotent; waits briefly for the reader thread. */
    public void stop() {
        Session s;
        synchronized (lock) {
            s = current;
            current = null;
        }
        if (s == null) return;
        s.active = false;
        Thread t = s.thread;
        if (t != null && t != Thread.currentThread()) {
            t.interrupt();
            try {
                t.join(JOIN_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        s.release();
    }
}
