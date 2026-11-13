package com.psthetech.swara.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.psthetech.swara.voice.stt.MoonshineStt;
import com.psthetech.swara.voice.stt.SpeechToText;

import ai.onnxruntime.OrtEnvironment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Moonshine tiny on the device's CPU over 5 LibriSpeech clips (CC BY 4.0, credits and keyword
 * table in ml/voice_assets/README.md). Per-clip timings are logged under tag "MoonshineStt".
 */
@RunWith(AndroidJUnit4.class)
public class MoonshineSttInstrumentedTest {

    private static final String TAG = "MoonshineStt";
    private static final String CLIPS = "voice/clips/";

    /** clip id, keyword 1, keyword 2 */
    private static final String[][] EXPECTED = {
            {"1272-135031-0011", "exactly", "returned"},
            {"1272-135031-0008", "hope", "hard"},
            {"1272-135031-0004", "king", "friends"},
            {"1272-141231-0000", "universe", "exist"},
            {"1272-128104-0008", "british", "foreign"},
    };
    /** The ~3 s clip used for the latency budget. */
    private static final String THREE_SECOND_CLIP = "1272-135031-0011";

    private static SpeechToText stt;

    @BeforeClass
    public static void load() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long t0 = SystemClock.elapsedRealtime();
        stt = new MoonshineStt(OrtEnvironment.getEnvironment(), app.getAssets(), VoiceModels.MOONSHINE_DIR);
        Log.i(TAG, "load ms=" + (SystemClock.elapsedRealtime() - t0));
    }

    @AfterClass
    public static void close() throws Exception {
        if (stt != null) stt.close();
    }

    @Test
    public void transcribesLibriSpeechClips() throws Exception {
        StringBuilder misses = new StringBuilder();
        for (String[] e : EXPECTED) {
            float[] audio = readWav(e[0]);
            long t0 = SystemClock.elapsedRealtime();
            String text = stt.transcribe(audio);
            long ms = SystemClock.elapsedRealtime() - t0;
            String lower = text.toLowerCase(Locale.ROOT);
            boolean ok = lower.contains(e[1]) && lower.contains(e[2]);
            Log.i(TAG, String.format(Locale.ROOT, "clip=%s audio=%.2fs ms=%d %s \"%s\"",
                    e[0], audio.length / 16000f, ms, ok ? "OK" : "MISS", text));
            if (!ok) misses.append(e[0]).append(": \"").append(text).append("\"\n");
        }
        assertEquals("transcripts missing keywords:\n" + misses, "", misses.toString());
    }

    @Test
    public void durationUnderBudget() throws Exception {
        float[] audio = readWav(THREE_SECOND_CLIP);
        stt.transcribe(audio);  // warm-up
        long t0 = SystemClock.elapsedRealtime();
        stt.transcribe(audio);
        long ms = SystemClock.elapsedRealtime() - t0;
        Log.i(TAG, String.format(Locale.ROOT, "budget clip=%s audio=%.2fs ms=%d (target < 800, fail > 2000)",
                THREE_SECOND_CLIP, audio.length / 16000f, ms));
        assertTrue("3 s clip took " + ms + " ms", ms <= 2000);
    }

    @Test
    public void tooShortAudioIsEmpty() throws Exception {
        assertEquals("", stt.transcribe(new float[100]));
    }

    /** 16 kHz mono 16-bit PCM WAV from the test APK's assets, as floats in [-1, 1]. */
    private static float[] readWav(String id) throws IOException {
        AssetManager assets = InstrumentationRegistry.getInstrumentation().getContext().getAssets();
        byte[] bytes;
        try (InputStream in = assets.open(CLIPS + id + ".wav")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            bytes = out.toByteArray();
        }
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        b.position(12);  // after "RIFF" <size> "WAVE"
        while (b.remaining() >= 8) {
            String chunk = new String(bytes, b.position(), 4, java.nio.charset.StandardCharsets.US_ASCII);
            b.position(b.position() + 4);
            int size = b.getInt();
            if (chunk.equals("fmt ")) {
                int format = b.getShort(b.position()), channels = b.getShort(b.position() + 2);
                int rate = b.getInt(b.position() + 4), bits = b.getShort(b.position() + 14);
                if (format != 1 || channels != 1 || rate != 16000 || bits != 16) {
                    throw new IOException(id + ": need 16 kHz mono PCM16");
                }
            } else if (chunk.equals("data")) {
                float[] audio = new float[size / 2];
                for (int i = 0; i < audio.length; i++) audio[i] = b.getShort() / 32768f;
                return audio;
            }
            b.position(b.position() + size + (size & 1));
        }
        throw new IOException(id + ": no data chunk");
    }
}
