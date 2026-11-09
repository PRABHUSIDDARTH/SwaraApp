package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.psthetech.swara.korokae.KorokaeEngine;
import com.psthetech.swara.korokae.KorokaeModel;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

/**
 * Verifies the pure-Java streaming Korokae engine against the PyTorch reference
 * produced by ml/korokae/export.py, plus latency / bypass / robustness contracts.
 */
public class KorokaeEngineParityTest {

    private KorokaeModel model;

    private static File asset(String rel) {
        File f = new File("src/main/assets/" + rel);
        return f.exists() ? f : new File("app/src/main/assets/" + rel);
    }

    @Before
    public void loadModel() throws IOException {
        try (InputStream in = new FileInputStream(asset(KorokaeModel.ASSET_PATH))) {
            model = KorokaeModel.load(in);
        }
        assertNotNull(model);
    }

    @Test
    public void streamingOutputMatchesPyTorchReference() throws IOException {
        InputStream res = getClass().getClassLoader().getResourceAsStream("korokae_parity.bin");
        assertNotNull("korokae_parity.bin test resource missing", res);
        byte[] raw;
        try (InputStream in = res) {
            raw = readAll(in);
        }
        ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        int frames = bb.getInt();
        int refFrames = bb.getInt();
        float[] input = new float[2 * frames];
        float[] ref = new float[2 * refFrames];
        bb.asFloatBuffer().get(input);
        bb.position(8 + 8 * frames);
        bb.asFloatBuffer().get(ref);

        KorokaeEngine engine = new KorokaeEngine(model);
        engine.setMix(1f);
        engine.snapMix();
        float[] out = new float[input.length];
        // Feed in odd-sized chunks to exercise buffering across calls.
        int pos = 0;
        int chunk = 37;
        while (pos < frames) {
            int c = Math.min(chunk, frames - pos);
            engine.process(input, 2 * pos, out, 2 * pos, c);
            pos += c;
            chunk = chunk * 7 % 997 + 1;
        }

        int hop = model.getHop();
        int n = model.getNFft();
        double maxErr = 0;
        double refPeak = 0;
        // Output frame f corresponds to padded reference position f - hop + 1. Compare only
        // fully overlapped region (skip first window and the tail beyond the reference).
        for (int f = n + hop; f < frames; f++) {
            int p = f - hop + 1;
            if (p >= refFrames - n) break;
            for (int ch = 0; ch < 2; ch++) {
                double e = Math.abs(out[2 * f + ch] - ref[2 * p + ch]);
                maxErr = Math.max(maxErr, e);
                refPeak = Math.max(refPeak, Math.abs(ref[2 * p + ch]));
            }
        }
        assertTrue("reference is silent", refPeak > 0.01);
        assertTrue("max abs error vs PyTorch = " + maxErr, maxErr < 2e-3);
    }

    @Test
    public void bypassIsExactDelayedPassthrough() {
        KorokaeEngine engine = new KorokaeEngine(model);
        engine.setMix(0f);
        engine.snapMix();
        int frames = 8192;
        float[] in = new float[2 * frames];
        for (int i = 0; i < frames; i++) {
            in[2 * i] = (float) Math.sin(i * 0.01);
            in[2 * i + 1] = (float) Math.cos(i * 0.013);
        }
        float[] out = new float[in.length];
        engine.process(in, 0, out, 0, frames);
        int lat = engine.getLatencyFrames();
        for (int f = lat; f < frames; f++) {
            assertEquals(in[2 * (f - lat)], out[2 * f], 1e-6f);
            assertEquals(in[2 * (f - lat) + 1], out[2 * f + 1], 1e-6f);
        }
    }

    @Test
    public void silenceStaysSilentAndResetClearsState() {
        KorokaeEngine engine = new KorokaeEngine(model);
        float[] buf = new float[2 * 4096];
        engine.process(buf, 0, buf, 0, 4096);
        for (float v : buf) assertEquals(0f, v, 1e-7f);
        engine.reset();
        assertEquals(1f, engine.getMix(), 0f);
    }

    @Test
    public void realtimeFactorIsComfortablyFasterThanPlayback() {
        KorokaeEngine engine = new KorokaeEngine(model);
        int frames = 44100 * 5;
        float[] buf = new float[2 * frames];
        for (int i = 0; i < buf.length; i++) buf[i] = (float) Math.sin(i * 0.0371) * 0.3f;
        engine.process(buf, 0, buf, 0, 44100); // warm up JIT
        long t0 = System.nanoTime();
        engine.process(buf, 0, buf, 0, frames);
        double seconds = (System.nanoTime() - t0) / 1e9;
        // 5 s of audio must process well under real time on the JVM host.
        assertTrue("took " + seconds + "s for 5s of audio", seconds < 2.5);
    }

    @Test
    public void rejectsCorruptModelFiles() throws IOException {
        byte[] good = Files.readAllBytes(asset(KorokaeModel.ASSET_PATH).toPath());
        byte[][] bad = {
                new byte[0],
                "NOPE".getBytes(),
                java.util.Arrays.copyOf(good, good.length - 4),
                java.util.Arrays.copyOf(good, good.length + 4),
        };
        for (byte[] b : bad) {
            try {
                KorokaeModel.load(new ByteArrayInputStream(b));
                fail("corrupt model accepted (len=" + b.length + ")");
            } catch (IOException expected) {
                // ok
            }
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int r;
        while ((r = in.read(buf)) != -1) bos.write(buf, 0, r);
        return bos.toByteArray();
    }
}
