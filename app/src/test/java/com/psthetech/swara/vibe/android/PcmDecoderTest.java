package com.psthetech.swara.vibe.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.AudioFormat;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class PcmDecoderTest {

    @Test
    public void partialDecodeOfAtLeastTenSecondsIsKept() {
        assertTrue(PcmDecoder.keepPartial(10 * 44100, 44100));
        assertTrue(PcmDecoder.keepPartial(29 * 48000, 48000));
    }

    @Test
    public void partialDecodeUnderTenSecondsIsDropped() {
        assertFalse(PcmDecoder.keepPartial(10 * 44100 - 1, 44100));
        assertFalse(PcmDecoder.keepPartial(0, 44100));
    }

    @Test
    public void unknownRateIsDropped() {
        assertFalse(PcmDecoder.keepPartial(1_000_000, 0));
    }

    @Test
    public void appendMonoAveragesStereo16BitFrames() {
        ByteBuffer b = ByteBuffer.allocate(12).order(ByteOrder.nativeOrder());
        b.putShort((short) 16384).putShort((short) 0)
                .putShort((short) -32768).putShort((short) -32768)
                .putShort((short) 8192).putShort((short) 8192);
        b.flip();
        float[] mono = new float[10];
        int n = PcmDecoder.appendMono(b, AudioFormat.ENCODING_PCM_16BIT, 2, mono, 1);
        assertEquals(4, n);
        assertEquals(0.25f, mono[1], 1e-6f);
        assertEquals(-1f, mono[2], 1e-6f);
        assertEquals(0.25f, mono[3], 1e-6f);
    }

    @Test
    public void appendMonoStopsAtCapacity() {
        ByteBuffer b = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder());
        b.putShort((short) 100).putShort((short) 200).putShort((short) 300).putShort((short) 400);
        b.flip();
        float[] mono = new float[2];
        int n = PcmDecoder.appendMono(b, AudioFormat.ENCODING_PCM_16BIT, 1, mono, 0);
        assertEquals(2, n);
        assertEquals(200 / 32768f, mono[1], 1e-7f);
    }

    @Test
    public void appendMonoReadsFloatFramesFromAnOffset() {
        ByteBuffer b = ByteBuffer.allocate(4 * 5).order(ByteOrder.nativeOrder());
        b.putFloat(9f).putFloat(0.5f).putFloat(-0.5f).putFloat(1f).putFloat(0f);
        b.position(4);
        b.limit(20);
        float[] mono = new float[4];
        int n = PcmDecoder.appendMono(b.slice().order(ByteOrder.nativeOrder()) , AudioFormat.ENCODING_PCM_FLOAT, 2, mono, 0);
        assertEquals(2, n);
        assertEquals(0f, mono[0], 1e-7f);
        assertEquals(0.5f, mono[1], 1e-7f);
    }

    @Test
    public void appendMonoKeepsBufferPositionMeaningful() {
        ByteBuffer b = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder());
        b.putShort((short) 0).putShort((short) 0).putShort((short) 32767).putShort((short) 32767);
        b.position(4);
        float[] mono = new float[4];
        int n = PcmDecoder.appendMono(b, AudioFormat.ENCODING_PCM_16BIT, 2, mono, 0);
        assertEquals(1, n);
        assertEquals(32767 / 32768f, mono[0], 1e-7f);
    }
}
