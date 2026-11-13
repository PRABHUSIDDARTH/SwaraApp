package com.psthetech.swara.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.voice.stt.SpeechToText;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
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
        assertSame(fake, m.stt());
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
}
