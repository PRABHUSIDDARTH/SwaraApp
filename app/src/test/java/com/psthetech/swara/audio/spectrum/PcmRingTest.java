package com.psthetech.swara.audio.spectrum;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class PcmRingTest {

    private static ByteBuffer pcm(short... samples) {
        ByteBuffer b = ByteBuffer.allocateDirect(samples.length * 2).order(ByteOrder.nativeOrder());
        for (short s : samples) b.putShort(s);
        b.flip();
        return b;
    }

    /** Mono frames whose values count up from {@code first}. */
    private static ByteBuffer counting(int first, int frames) {
        short[] s = new short[frames];
        for (int i = 0; i < frames; i++) s[i] = (short) (first + i);
        return pcm(s);
    }

    private static int value(float sample) {
        return Math.round(sample * 32768f);
    }

    @Test
    public void nothingToReadBeforeAnEpochOrBeforeAWindowIsWritten() {
        PcmRing ring = new PcmRing(256);
        float[] out = new float[8];
        assertFalse(ring.read(out, 0));
        ring.startEpoch(44100, 1, true);
        ring.write(counting(0, 7));
        assertFalse(ring.read(out, 0));
        ring.write(counting(7, 1));
        assertTrue(ring.read(out, 0));
        assertEquals(44100, ring.lastSampleRate());
    }

    @Test
    public void writeDoesNotConsumeTheSource() {
        PcmRing ring = new PcmRing(256);
        ring.startEpoch(44100, 1, true);
        ByteBuffer source = counting(0, 8);
        source.position(4);
        ring.write(source);
        assertEquals(4, source.position());
        assertEquals(16, source.limit());
    }

    @Test
    public void readsBehindTheNewestAudioByTheLag() {
        PcmRing ring = new PcmRing(256);
        ring.startEpoch(44100, 1, true);
        ring.write(counting(0, 40));
        float[] out = new float[8];
        assertTrue(ring.read(out, 10));
        assertEquals(22, value(out[0]));
        assertEquals(29, value(out[7]));
    }

    @Test
    public void readsCorrectlyAcrossTheWrap() {
        PcmRing ring = new PcmRing(64); // 32 mono frames
        ring.startEpoch(44100, 1, true);
        float[] out = new float[8];
        for (int block = 0; block < 20; block++) {
            ring.write(counting(block * 5, 5));
            if (ring.read(out, 0)) {
                for (int i = 0; i < 8; i++) assertEquals((block + 1) * 5 - 8 + i, value(out[i]));
            }
        }
        assertTrue(ring.read(out, 0));
    }

    @Test
    public void aWindowTheWriterHasLappedIsRejected() {
        PcmRing ring = new PcmRing(64);
        ring.startEpoch(44100, 1, true);
        ring.write(counting(0, 100));
        float[] out = new float[8];
        assertFalse(ring.read(out, 40));
        assertTrue(ring.read(out, 0));
        assertEquals(99, value(out[7]));
    }

    @Test
    public void aNewEpochHidesEverythingWrittenBeforeIt() {
        PcmRing ring = new PcmRing(256);
        ring.startEpoch(44100, 1, true);
        ring.write(counting(0, 40));
        ring.startEpoch(44100, 1, true);
        float[] out = new float[8];
        assertFalse(ring.read(out, 0));
        ring.write(counting(1000, 8));
        assertTrue(ring.read(out, 0));
        assertEquals(1000, value(out[0]));
    }

    @Test
    public void afterAFlushTheStartOfTheNewAudioShowsWhileTheBufferFills() {
        PcmRing ring = new PcmRing(256);
        ring.startEpoch(44100, 1, true);
        ring.write(counting(500, 12));
        float[] out = new float[8];
        assertTrue(ring.read(out, 30)); // lag is longer than everything written
        assertEquals(500, value(out[0]));
    }

    @Test
    public void afterAGapTheBarsWaitForTheLagInsteadOfRunningEarly() {
        PcmRing ring = new PcmRing(256);
        ring.startEpoch(44100, 1, false);
        ring.write(counting(500, 12));
        float[] out = new float[8];
        assertFalse(ring.read(out, 30));
        ring.write(counting(512, 26)); // 38 frames: lag 30 + window 8
        assertTrue(ring.read(out, 30));
        assertEquals(500, value(out[0]));
    }

    @Test
    public void discardWrittenHidesQueuedAudioAtOnce() {
        PcmRing ring = new PcmRing(256);
        ring.startEpoch(44100, 1, true);
        ring.write(counting(0, 40));
        ring.discardWritten();
        float[] out = new float[8];
        assertFalse(ring.read(out, 10));
        // The flush that follows a seek starts a new epoch, which reads straight away.
        ring.startEpoch(44100, 1, true);
        ring.write(counting(900, 8));
        assertTrue(ring.read(out, 10));
        assertEquals(900, value(out[0]));
    }

    @Test
    public void discardShownKeepsQueuedAudioButNeverRepeatsWhatWasShown() {
        PcmRing ring = new PcmRing(256);
        ring.startEpoch(44100, 1, true);
        ring.write(counting(0, 40));
        float[] out = new float[8];
        assertTrue(ring.read(out, 20)); // shows frames 12..19
        ring.discardShown();
        assertFalse(ring.read(out, 20));
        ring.write(counting(40, 8));    // newest is 47, audible window is 20..27
        assertTrue(ring.read(out, 20));
        assertEquals(20, value(out[0]));
    }

    @Test
    public void stereoIsDownmixedToTheAverageOfBothChannels() {
        PcmRing ring = new PcmRing(256);
        ring.startEpoch(48000, 2, true);
        ring.write(pcm((short) 100, (short) 300, (short) -200, (short) -400,
                (short) 32767, (short) -32767, (short) 8, (short) 8));
        float[] out = new float[4];
        assertTrue(ring.read(out, 0));
        assertEquals(200, value(out[0]));
        assertEquals(-300, value(out[1]));
        assertEquals(0, value(out[2]));
        assertEquals(8, value(out[3]));
        assertEquals(48000, ring.lastSampleRate());
    }

    @Test
    public void channelCountAndRateCanChangeBetweenTracks() {
        PcmRing ring = new PcmRing(256);
        float[] out = new float[4];
        ring.startEpoch(44100, 2, true);
        ring.write(pcm((short) 10, (short) 20, (short) 10, (short) 20,
                (short) 10, (short) 20, (short) 10, (short) 20));
        assertTrue(ring.read(out, 0));
        assertEquals(15, value(out[3]));
        ring.startEpoch(96000, 1, true);
        ring.write(counting(70, 4));
        assertTrue(ring.read(out, 0));
        assertEquals(70, value(out[0]));
        assertEquals(96000, ring.lastSampleRate());
        ring.startEpoch(22050, 2, true);
        ring.write(pcm((short) 2, (short) 4, (short) 2, (short) 4,
                (short) 2, (short) 4, (short) 2, (short) 4));
        assertTrue(ring.read(out, 0));
        assertEquals(3, value(out[0]));
        assertEquals(22050, ring.lastSampleRate());
    }

    @Test
    public void unsupportedFormatsReadAsNothing() {
        PcmRing ring = new PcmRing(256);
        float[] out = new float[4];
        ring.startEpoch(44100, 1, true);
        ring.write(counting(0, 8));
        ring.startEpoch(0, 0, true);
        assertFalse(ring.read(out, 0));
        ring.startEpoch(44100, PcmRing.MAX_CHANNELS + 1, true);
        assertFalse(ring.read(out, 0));
    }

    /**
     * One thread writes a counter while another reads. Every window the reader accepts must be
     * unbroken: any torn read would show a jump.
     */
    @Test(timeout = 30_000)
    public void acceptedWindowsAreNeverTornWhileTheWriterRuns() throws Exception {
        final PcmRing ring = new PcmRing(1 << 12); // small, so the writer laps often
        ring.startEpoch(44100, 1, true);
        final AtomicBoolean done = new AtomicBoolean();
        final AtomicReference<Throwable> writerFailure = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            try {
                ByteBuffer block = ByteBuffer.allocateDirect(2 * 600).order(ByteOrder.nativeOrder());
                int next = 0;
                for (int round = 0; round < 200_000; round++) {
                    int frames = 1 + round % 600;
                    block.clear();
                    for (int i = 0; i < frames; i++) block.putShort((short) next++);
                    block.flip();
                    ring.write(block);
                }
            } catch (Throwable t) {
                writerFailure.set(t);
            } finally {
                done.set(true);
            }
        });
        float[] out = new float[512];
        long accepted = 0;
        writer.start();
        int lag = 0;
        while (!done.get()) {
            lag = (lag + 97) % 1500;
            if (!ring.read(out, lag)) continue;
            accepted++;
            for (int i = 1; i < out.length; i++) {
                short step = (short) (value(out[i]) - value(out[i - 1]));
                assertEquals("torn window at " + i, 1, step);
            }
        }
        writer.join();
        assertEquals(null, writerFailure.get());
        assertTrue("reader never succeeded", accepted > 0);
    }
}
