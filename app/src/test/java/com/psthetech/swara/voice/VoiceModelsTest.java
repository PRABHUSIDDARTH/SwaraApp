package com.psthetech.swara.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.voice.stt.SpeechToText;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public class VoiceModelsTest {

    private static final Executor DIRECT = Runnable::run;

    private static final class FakeStt implements SpeechToText {
        boolean closed;
        @Override public String transcribe(float[] audio16k) { return "ok"; }
        @Override public void close() { closed = true; }
    }

    private static VoiceModels models(Callable<SpeechToText> factory) {
        return new VoiceModels(factory, DIRECT, DIRECT);
    }

    @Test public void loadErrorLandsInFailedAndStillCallsBack() {
        VoiceModels m = models(() -> { throw new UnsatisfiedLinkError("no onnxruntime"); });
        AtomicInteger called = new AtomicInteger();
        m.loadAsync(called::incrementAndGet);
        assertTrue(m.failed());
        assertFalse(m.isReady());
        assertNull(m.stt());
        assertEquals(1, called.get());
    }

    @Test public void initializerErrorAndExceptionsAlsoFail() {
        VoiceModels a = models(() -> { throw new ExceptionInInitializerError("ort"); });
        a.loadAsync(null);
        assertTrue(a.failed());
        VoiceModels b = models(() -> { throw new java.io.IOException("missing asset"); });
        b.loadAsync(null);
        assertTrue(b.failed());
    }

    @Test public void failureIsStickyUntilRelease() {
        AtomicInteger attempts = new AtomicInteger();
        FakeStt fake = new FakeStt();
        VoiceModels m = models(() -> {
            if (attempts.incrementAndGet() == 1) throw new NoClassDefFoundError("ai.onnxruntime.OrtEnvironment");
            return fake;
        });
        m.loadAsync(null);
        AtomicInteger called = new AtomicInteger();
        m.loadAsync(called::incrementAndGet);  // no retry, callback still runs
        assertTrue(m.failed());
        assertEquals(1, attempts.get());
        assertEquals(1, called.get());

        m.release();
        assertFalse(m.failed());
        m.loadAsync(null);
        assertTrue(m.isReady());
        assertEquals("ok", transcribeQuietly(m.stt()));
        assertEquals(2, attempts.get());
    }

    @Test public void loadsOnceAndReleaseCloses() {
        AtomicInteger attempts = new AtomicInteger();
        FakeStt fake = new FakeStt();
        VoiceModels m = models(() -> { attempts.incrementAndGet(); return fake; });
        List<String> calls = new ArrayList<>();
        m.loadAsync(() -> calls.add("a"));
        m.loadAsync(() -> calls.add("b"));
        assertTrue(m.isReady());
        assertEquals(1, attempts.get());
        assertEquals(List.of("a", "b"), calls);

        m.release();
        assertTrue(fake.closed);
        assertFalse(m.isReady());
        assertNull(m.stt());
    }

    @Test public void releaseDuringLoadDiscardsTheLoad() {
        List<Runnable> queued = new ArrayList<>();
        FakeStt fake = new FakeStt();
        VoiceModels m = new VoiceModels(() -> fake, queued::add, DIRECT);
        AtomicInteger called = new AtomicInteger();
        m.loadAsync(called::incrementAndGet);
        m.release();
        queued.remove(0).run();  // the in-flight load finishes after release
        assertTrue(fake.closed);
        assertFalse(m.isReady());
        assertFalse(m.failed());
        assertEquals(0, called.get());
    }

    private static String transcribeQuietly(SpeechToText stt) {
        try {
            return stt.transcribe(new float[0]);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /** transcribe blocks until {@code release} is counted down. */
    private static final class BlockingStt implements SpeechToText {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        volatile boolean inTranscribe;
        volatile boolean closedDuringTranscribe;
        volatile int closes;
        volatile int transcribes;

        @Override public String transcribe(float[] audio16k) throws Exception {
            transcribes++;
            inTranscribe = true;
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            inTranscribe = false;
            return "done";
        }

        @Override public void close() {
            if (inTranscribe) closedDuringTranscribe = true;
            closes++;
        }
    }

    @Test public void releaseWaitsForTranscriptionThroughTheHolder() throws Exception {
        BlockingStt fake = new BlockingStt();
        VoiceModels m = new VoiceModels(() -> fake, r -> new Thread(r, "loader").start(), DIRECT);
        m.loadAsync(null);
        long deadline = System.currentTimeMillis() + 5000;
        while (!m.isReady() && System.currentTimeMillis() < deadline) Thread.sleep(10);
        SpeechToText stt = m.stt();
        Thread stti = new Thread(() -> transcribeQuietly(stt), "stt");
        stti.start();
        assertTrue(fake.entered.await(5, TimeUnit.SECONDS));

        m.release();
        Thread.sleep(150);
        assertEquals(0, fake.closes);
        fake.release.countDown();
        stti.join(5000);
        deadline = System.currentTimeMillis() + 5000;
        while (fake.closes == 0 && System.currentTimeMillis() < deadline) Thread.sleep(10);
        assertEquals(1, fake.closes);
        assertFalse(fake.closedDuringTranscribe);
    }

    @Test public void transcribeAfterReleaseFailsWithoutTouchingTheModel() {
        BlockingStt fake = new BlockingStt();
        VoiceModels m = models(() -> fake);
        m.loadAsync(null);
        SpeechToText stale = m.stt();
        m.release();
        assertEquals(1, fake.closes);
        try {
            stale.transcribe(new float[16000]);
            fail("a released model must not transcribe");
        } catch (Exception expected) {
            // IllegalStateException
        }
        assertEquals(0, fake.transcribes);
    }
}
