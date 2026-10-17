package com.psthetech.swara.vibe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.test.platform.app.InstrumentationRegistry;

import com.psthetech.swara.vibe.android.AudioFeatureScorer;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AudioFeatureScorerDeviceTest {

    private static final int RATE = 44100;
    private static final int SECONDS = 20;

    /** 16-bit PCM stereo WAV: C major chord with a decaying pulse every 0.5 s (120 BPM). */
    private static File writeWav(Context ctx) throws IOException {
        int frames = RATE * SECONDS;
        ByteBuffer data = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN);
        double[] freqs = {261.63, 329.63, 392.0};
        int beat = RATE / 2;
        for (int i = 0; i < frames; i++) {
            double t = i / (double) RATE;
            double env = Math.exp(-6.0 * (i % beat) / RATE);
            double v = 0;
            for (double f : freqs) v += Math.sin(2 * Math.PI * f * t);
            v = v / 3.0 * (0.2 + 0.7 * env);
            short s = (short) Math.round(v * 30000);
            data.putShort(s).putShort(s);
        }
        File f = new File(ctx.getCacheDir(), "vibe_test.wav");
        try (FileOutputStream out = new FileOutputStream(f)) {
            ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
            h.put("RIFF".getBytes()).putInt(36 + data.capacity()).put("WAVE".getBytes());
            h.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 2)
                    .putInt(RATE).putInt(RATE * 4).putShort((short) 4).putShort((short) 16);
            h.put("data".getBytes()).putInt(data.capacity());
            out.write(h.array());
            out.write(data.array());
        }
        return f;
    }

    @Test
    public void scoresAGeneratedWav() throws Exception {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File wav = writeWav(ctx);
        AudioFeatureScorer scorer = new AudioFeatureScorer(ctx);
        assertEquals(3, scorer.version());
        MoodResult r = scorer.score(Uri.fromFile(wav));
        assertTrue("score should be ok", r.ok);
        assertEquals(120.0, r.tempoBpm, 5.0);
        double[] raw = RawMoodModel.unpack(r.raw);
        assertNotNull(raw);
        double[] v = PercentileNormalizer.normalize(new double[]{raw[0], 0.5});
        double[] e = PercentileNormalizer.normalize(new double[]{raw[1], 0.5});
        assertTrue(v[0] >= 0 && v[0] <= 1);
        assertTrue(e[0] >= 0 && e[0] <= 1);
        wav.delete();
    }
}
