package com.psthetech.swara.voice.debug;

import android.util.Log;

import androidx.annotation.Nullable;

import com.psthetech.swara.BuildConfig;
import com.psthetech.swara.voice.intent.IntentParser;
import com.psthetech.swara.voice.intent.VoiceIntent;
import com.psthetech.swara.voice.match.Candidate;
import com.psthetech.swara.voice.match.LibraryIndex;
import com.psthetech.swara.voice.match.LibraryVoiceMatcher;
import com.psthetech.swara.voice.match.MatchResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/**
 * DEBUG-ONLY diagnostics for the voice pipeline. Every entry point is guarded by
 * {@code BuildConfig.DEBUG}; release builds log and save nothing. Remove this package and its
 * call sites (grep VoiceDiag) to drop the instrumentation.
 */
public final class VoiceDiag {
    public static final String TAG = "SwaraVoiceDiag";
    private static final int KEEP_WAVS = 10;
    private static final int SAMPLE_RATE = 16000;
    private static volatile File wavDir;

    private VoiceDiag() { }

    public static boolean on() { return BuildConfig.DEBUG; }

    public static void d(String msg) {
        if (!BuildConfig.DEBUG) return;
        try { Log.d(TAG, msg); } catch (RuntimeException ignored) { }
    }

    /** Logs every automatic artist merge once per resolver build. Debug builds only. */
    public static void logMerges(@Nullable com.psthetech.swara.util.ArtistAliasResolver r) {
        if (!BuildConfig.DEBUG || r == null) return;
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, String> e : r.autoMerges().entrySet()) {
            lines.add("merge: \"" + r.creditDisplay(e.getKey()) + "\" -> \""
                    + r.displayName(e.getValue()) + "\"");
        }
        java.util.Collections.sort(lines);
        d("auto merges: " + lines.size());
        for (String l : lines) d(l);
    }

    /** Call once with context.getCacheDir(). */
    public static void init(@Nullable File cacheDir) {
        if (!BuildConfig.DEBUG || cacheDir == null) return;
        wavDir = new File(cacheDir, "voice-debug");
    }

    /** Per-voice-session accumulator, fed from the mic thread. */
    public static final class Session {
        private float[] rms = new float[128];
        private int frames;
        private int speechFrames;

        /** @param state recorder state after push; speech/floor from the VAD. */
        public void onFrame(float frameRms, float floor, boolean speech, String state) {
            if (!BuildConfig.DEBUG) return;
            if (frames == rms.length) rms = Arrays.copyOf(rms, frames * 2);
            rms[frames] = frameRms;
            frames++;
            if (speech) speechFrames++;
            if (frames % 10 == 0 && ("WAITING".equals(state) || "RECORDING".equals(state))) {
                d(String.format(Locale.ROOT, "frame#%d rms=%.4f floor=%.4f isSpeech=%b state=%s",
                        frames, frameRms, floor, speech, state));
            }
        }

        public void summary(String finalState, int framesBeforeSpeech, int recordedSamples, float floor) {
            if (!BuildConfig.DEBUG) return;
            float[] s = Arrays.copyOf(rms, frames);
            Arrays.sort(s);
            float min = frames == 0 ? 0 : s[0];
            float med = frames == 0 ? 0 : s[frames / 2];
            float max = frames == 0 ? 0 : s[frames - 1];
            d(String.format(Locale.ROOT,
                    "session summary: state=%s frames=%d beforeSpeech=%d speechFrames=%d recorded=%.2fs "
                            + "rms[min/median/max]=%.4f/%.4f/%.4f noiseFloorEnd=%.4f",
                    finalState, frames, framesBeforeSpeech, speechFrames,
                    recordedSamples / (float) SAMPLE_RATE, min, med, max, floor));
        }
    }

    /** STT worker thread, before transcribe(): saves the exact audio and logs its duration. */
    public static void saveWav(float[] audio) {
        if (!BuildConfig.DEBUG) return;
        File dir = wavDir;
        d(String.format(Locale.ROOT, "stt input: %d samples = %.2fs", audio.length, audio.length / (float) SAMPLE_RATE));
        if (dir == null) return;
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) {
                d("wav save failed: cannot create " + dir);
                return;
            }
            File f = new File(dir, "cmd-" + System.currentTimeMillis() + ".wav");
            byte[] pcm = new byte[audio.length * 2];
            ByteBuffer pb = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
            for (float v : audio) {
                int i = Math.round(v * 32768f);
                pb.putShort((short) Math.max(-32768, Math.min(32767, i)));
            }
            ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
            h.put("RIFF".getBytes("US-ASCII")).putInt(36 + pcm.length).put("WAVE".getBytes("US-ASCII"));
            h.put("fmt ".getBytes("US-ASCII")).putInt(16).putShort((short) 1).putShort((short) 1)
                    .putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort((short) 2).putShort((short) 16);
            h.put("data".getBytes("US-ASCII")).putInt(pcm.length);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(h.array());
                out.write(pcm);
            }
            d("saved wav: " + f.getAbsolutePath());
            File[] all = dir.listFiles((d, n) -> n.startsWith("cmd-") && n.endsWith(".wav"));
            if (all != null && all.length > KEEP_WAVS) {
                Arrays.sort(all, Comparator.comparing(File::getName));
                for (int i = 0; i < all.length - KEEP_WAVS; i++) //noinspection ResultOfMethodCallIgnored
                    all[i].delete();
            }
        } catch (IOException | RuntimeException e) {
            d("wav save failed: " + e);
        }
    }

    public static void logTranscript(@Nullable String text, float audioSeconds, long sttMs) {
        if (!BuildConfig.DEBUG) return;
        d(String.format(Locale.ROOT, "stt: \"%s\" audio=%.2fs sttMs=%d", text, audioSeconds, sttMs));
    }

    /** Re-parses and re-matches (debug only, read-only) to log the intent and top 3 candidates. */
    public static void logMatch(String transcript, @Nullable LibraryIndex idx) {
        if (!BuildConfig.DEBUG) return;
        try {
            VoiceIntent intent = IntentParser.parse(transcript);
            d("intent: " + intent.getType() + " query=\"" + intent.getQuery() + "\"");
            if (idx == null) {
                d("match: library not loaded");
                return;
            }
            MatchResult m = LibraryVoiceMatcher.match(intent, idx);
            StringBuilder sb = new StringBuilder("match: confidence=").append(m.confidence);
            for (int i = 0; i < Math.min(3, m.ranked.size()); i++) {
                Candidate c = m.ranked.get(i);
                sb.append(String.format(Locale.ROOT, " | %d. %s %.3f \"%s\"", i + 1, c.kind, c.score, c.display));
            }
            d(sb.toString());
        } catch (Throwable t) {
            d("match diag failed: " + t);
        }
    }
}
