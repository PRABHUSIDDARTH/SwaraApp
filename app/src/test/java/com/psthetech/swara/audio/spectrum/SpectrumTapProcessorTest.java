package com.psthetech.swara.audio.spectrum;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

public class SpectrumTapProcessorTest {

    private final SpectrumTap tap = new SpectrumTap();
    private final SpectrumTapProcessor processor = new SpectrumTapProcessor(tap);

    private void format(int rate, int channels, int encoding) throws Exception {
        processor.configure(new AudioFormat(rate, channels, encoding));
        processor.flush();
    }

    private byte[] push(byte[] bytes) {
        ByteBuffer in = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
        in.put(bytes).flip();
        processor.queueInput(in);
        assertEquals("input must be consumed", 0, in.remaining());
        ByteBuffer out = processor.getOutput();
        byte[] result = new byte[out.remaining()];
        out.get(result);
        return result;
    }

    private static byte[] random(int bytes, long seed) {
        byte[] b = new byte[bytes];
        new Random(seed).nextBytes(b);
        return b;
    }

    private static byte[] constant(int samples, int value) {
        ByteBuffer b = ByteBuffer.allocate(samples * 2).order(ByteOrder.nativeOrder());
        for (int i = 0; i < samples; i++) b.putShort((short) value);
        return b.array();
    }

    private int firstSample() {
        float[] out = new float[16];
        assertTrue(tap.ring().read(out, 0));
        return Math.round(out[0] * 32768f);
    }

    @Test
    public void audioPassesThroughByteForByteWithTheTapOffAndOn() throws Exception {
        format(44100, 2, C.ENCODING_PCM_16BIT);
        for (boolean on : new boolean[]{false, true, false, true}) {
            tap.setEnabled(on);
            for (int size : new int[]{4, 4096, 17_640, 8}) {
                byte[] in = random(size, size + (on ? 1 : 0));
                assertArrayEquals(in, push(in));
            }
        }
    }

    @Test
    public void otherEncodingsPassThroughAndLeaveTheBarsEmpty() throws Exception {
        tap.setEnabled(true);
        for (int encoding : new int[]{C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_8BIT}) {
            format(48000, 2, encoding);
            assertTrue("must stay in the chain", processor.isActive());
            byte[] in = random(4800, encoding);
            assertArrayEquals(in, push(in));
            assertFalse(tap.ring().read(new float[16], 0));
        }
    }

    @Test
    public void staysActiveWhetherOrNotTheTapIsEnabled() throws Exception {
        format(44100, 2, C.ENCODING_PCM_16BIT);
        assertTrue(processor.isActive());
        tap.setEnabled(true);
        assertTrue(processor.isActive());
    }

    @Test
    public void copiesNothingWhileDisabled() throws Exception {
        format(44100, 1, C.ENCODING_PCM_16BIT);
        push(constant(64, 7));
        assertFalse(tap.ring().read(new float[16], 0));
    }

    @Test
    public void followsMonoStereoAndSampleRateChanges() throws Exception {
        tap.setEnabled(true);
        format(44100, 1, C.ENCODING_PCM_16BIT);
        push(constant(32, 100));
        assertEquals(100, firstSample());
        assertEquals(44100, tap.ring().lastSampleRate());
        format(48000, 2, C.ENCODING_PCM_16BIT);
        push(constant(64, 200));
        assertEquals(200, firstSample());
        assertEquals(48000, tap.ring().lastSampleRate());
        format(96000, 1, C.ENCODING_PCM_16BIT);
        push(constant(32, 300));
        assertEquals(300, firstSample());
        assertEquals(96000, tap.ring().lastSampleRate());
    }

    @Test
    public void theNewFormatAppliesOnlyFromTheFlush() throws Exception {
        tap.setEnabled(true);
        format(44100, 1, C.ENCODING_PCM_16BIT);
        // The sink configures the next track while the old one is still draining.
        processor.configure(new AudioFormat(48000, 2, C.ENCODING_PCM_16BIT));
        push(constant(32, 11));
        assertEquals(11, firstSample());
        assertEquals(44100, tap.ring().lastSampleRate());
        processor.flush();
        push(constant(64, 22));
        assertEquals(22, firstSample());
        assertEquals(48000, tap.ring().lastSampleRate());
    }

    @Test
    public void flushHidesOldAudio() throws Exception {
        tap.setEnabled(true);
        format(44100, 1, C.ENCODING_PCM_16BIT);
        push(constant(64, 5));
        processor.flush();
        assertFalse(tap.ring().read(new float[16], 0));
    }

    @Test
    public void audioThatPassedWhileDisabledSeparatesOldFromNew() throws Exception {
        tap.setEnabled(true);
        format(44100, 1, C.ENCODING_PCM_16BIT);
        push(constant(64, 5));
        tap.setEnabled(false);
        push(constant(64, 6));
        tap.setEnabled(true);
        push(constant(8, 7)); // fewer than one window of new audio
        assertFalse("old audio must not fill the window", tap.ring().read(new float[16], 0));
        push(constant(8, 7));
        assertEquals(7, firstSample());
    }

    @Test
    public void aPauseWithNoAudioInBetweenKeepsTheQueuedAudio() throws Exception {
        tap.setEnabled(true);
        format(44100, 1, C.ENCODING_PCM_16BIT);
        push(constant(64, 5));
        tap.setEnabled(false);
        tap.setEnabled(true);
        assertEquals(5, firstSample());
    }

    @Test
    public void resetEmptiesTheBars() throws Exception {
        tap.setEnabled(true);
        format(44100, 1, C.ENCODING_PCM_16BIT);
        push(constant(64, 5));
        processor.reset();
        assertFalse(tap.ring().read(new float[16], 0));
    }

    @Test
    public void theWidgetKeepsCopyingWhenTheScreenBarsStop() throws Exception {
        format(44100, 1, C.ENCODING_PCM_16BIT);
        tap.setWidgetListening(true);
        tap.setEnabled(true);
        tap.setEnabled(false); // Now Playing closed
        assertTrue(tap.isEnabled());
        push(constant(64, 9));
        assertEquals(9, firstSample());
        tap.setWidgetListening(false);
        assertFalse(tap.isEnabled());
    }

    @Test
    public void theWidgetStoppingLeavesTheScreenBarsRunning() {
        tap.setEnabled(true);
        tap.setWidgetListening(true);
        tap.setWidgetListening(false);
        assertTrue(tap.isEnabled());
    }

    @Test
    public void lagComesFromTheAudioTrackBuffer() {
        assertEquals(SpectrumTap.DEFAULT_LAG_MS, tap.lagMs());
        tap.setOutputBuffer(48000, 2, 96_000); // half a second of 16-bit stereo
        assertEquals(500, tap.lagMs());
        assertEquals(22_050, tap.lagFrames(44100));
        tap.setOutputBuffer(0, 0, 0); // unusable values keep the last good lag
        assertEquals(500, tap.lagMs());
    }
}
