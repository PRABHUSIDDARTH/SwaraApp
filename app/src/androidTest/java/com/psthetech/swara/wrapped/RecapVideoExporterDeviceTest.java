package com.psthetech.swara.wrapped;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import androidx.test.platform.app.InstrumentationRegistry;

import com.psthetech.swara.wrapped.export.RecapVideoExporter;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class RecapVideoExporterDeviceTest {

    @Test
    public void exportsSilentVideoOfExpectedLength() throws Exception {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        WrappedStats s = SlideRendererDeviceTest.sample();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Uri> result = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        RecapVideoExporter.export(ctx, s,
                Arrays.asList(SlideType.INTRO, SlideType.MINUTES, SlideType.SUMMARY),
                new HashMap<>(), /* publishToGallery */ false,
                new RecapVideoExporter.Listener() {
                    @Override public void onProgress(float p) { }
                    @Override public void onDone(Uri uri) { result.set(uri); done.countDown(); }
                    @Override public void onError(String msg) { error.set(msg); done.countDown(); }
                });
        assertTrue("timed out", done.await(120, TimeUnit.SECONDS));
        assertEquals(null, error.get());
        assertNotNull(result.get());

        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(ctx, result.get(), null);
        boolean video = false, audio = false;
        long durationUs = 0;
        int w = 0, h = 0;
        for (int i = 0; i < ex.getTrackCount(); i++) {
            MediaFormat f = ex.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                video = true;
                w = f.getInteger(MediaFormat.KEY_WIDTH);
                h = f.getInteger(MediaFormat.KEY_HEIGHT);
                if (f.containsKey(MediaFormat.KEY_DURATION)) durationUs = f.getLong(MediaFormat.KEY_DURATION);
            }
            if (mime.startsWith("audio/")) audio = true;
        }
        ex.release();
        assertTrue(video);
        assertFalse(audio);
        assertTrue("size " + w + "x" + h, (w == 1080 && h == 1920) || (w == 720 && h == 1280));
        // 270 frames at 30 fps; dropped frames would shorten the video
        assertEquals(9_000_000L, durationUs, 200_000L);
    }

    @Test
    public void cancelDeletesPartialFile() throws Exception {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();
        RecapVideoExporter.Cancellable job = RecapVideoExporter.export(ctx, SlideRendererDeviceTest.sample(),
                Arrays.asList(SlideType.values()), new HashMap<>(), false,
                new RecapVideoExporter.Listener() {
                    @Override public void onProgress(float p) { }
                    @Override public void onDone(Uri uri) { done.countDown(); }
                    @Override public void onError(String msg) { error.set(msg); done.countDown(); }
                });
        job.cancel();
        assertTrue(done.await(60, TimeUnit.SECONDS));
        assertNotNull(error.get());
        java.io.File[] left = new java.io.File(ctx.getCacheDir(), "share_cards").listFiles(
                (dir, name) -> name.endsWith(".mp4.tmp"));
        assertTrue(left == null || left.length == 0);
    }
}
