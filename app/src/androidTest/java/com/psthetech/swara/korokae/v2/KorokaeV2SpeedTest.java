package com.psthetech.swara.korokae.v2;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;
import android.os.Debug;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Speed gate (spec 7.1): a 4-minute song must separate in <= 60 s with peak memory <= 500 MB.
 * Instrumentation args: korokaeModel (default korokae_v2_4_96.onnx), korokaeThreads (default 4).
 */
@RunWith(AndroidJUnit4.class)
public class KorokaeV2SpeedTest {

    private static final int SECONDS = 240;

    @Test
    public void fourMinuteSongUnder60s() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        String modelName = args.getString("korokaeModel", "korokae_v2_4_96.onnx");
        int threads = Integer.parseInt(args.getString("korokaeThreads", "4"));
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File model = TestAssets.copyToCache(app, modelName);

        long baseKb = rssKb("VmRSS:");
        int frames = KorokaeV2Engine.SAMPLE_RATE * SECONDS;
        float[] audio = new float[frames * 2];
        Random r = new Random(42);
        for (int i = 0; i < frames; i++) {
            double t = i / (double) KorokaeV2Engine.SAMPLE_RATE;
            double tone = 0.3 * Math.sin(2 * Math.PI * 220 * t) + 0.2 * Math.sin(2 * Math.PI * 659 * t);
            audio[2 * i] = (float) (tone + 0.05 * r.nextGaussian());
            audio[2 * i + 1] = (float) (tone * 0.8 + 0.05 * r.nextGaussian());
        }

        Log.i("KorokaeV2", "rss baseMB=" + baseKb / 1024 + " withAudioMB=" + rssKb("VmRSS:") / 1024);
        resetPeakRss();
        AtomicLong peak = new AtomicLong();
        AtomicBoolean running = new AtomicBoolean(true);
        Thread sampler = new Thread(() -> {
            Runtime rt = Runtime.getRuntime();
            while (running.get()) {
                long used = Debug.getNativeHeapAllocatedSize() + (rt.totalMemory() - rt.freeMemory());
                peak.accumulateAndGet(used, Math::max);
                SystemClock.sleep(250);
            }
        });
        sampler.start();

        long ms;
        try (OnnxMaskModel m = OnnxMaskModel.open(model, threads)) {
            long t0 = SystemClock.elapsedRealtime();
            new KorokaeV2Engine(m).separate(audio, frames, p -> {}, () -> false);
            ms = SystemClock.elapsedRealtime() - t0;
        } finally {
            running.set(false);
            sampler.join();
        }
        long sampledMb = peak.get() / (1024 * 1024);
        // VmHWM: the kernel's exact resident-set high-water mark for this process (no sampling
        // gaps; includes mmapped and non-malloc memory). The sampled heap figure is logged too.
        long peakMb = peakRssKb() / 1024;
        Log.i("KorokaeV2", "speed model=" + modelName + " threads=" + threads
                + " ms=" + ms + " peakMB=" + peakMb + " sampledHeapMB=" + sampledMb);
        assertTrue("took " + ms + " ms (limit 60000)", ms <= 60_000);
        assertTrue("peak " + peakMb + " MB (limit 500)", peakMb <= 500);
    }

    /** Resets VmHWM to the current RSS (writing 5 to clear_refs). */
    private static void resetPeakRss() throws java.io.IOException {
        try (java.io.FileWriter w = new java.io.FileWriter("/proc/self/clear_refs")) {
            w.write("5");
        }
    }

    private static long peakRssKb() throws java.io.IOException {
        return rssKb("VmHWM:");
    }

    private static long rssKb(String key) throws java.io.IOException {
        try (java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.FileReader("/proc/self/status"))) {
            for (String line; (line = r.readLine()) != null; ) {
                if (line.startsWith(key)) {
                    return Long.parseLong(line.replaceAll("[^0-9]", ""));
                }
            }
        }
        throw new IllegalStateException("no " + key + " in /proc/self/status");
    }
}
