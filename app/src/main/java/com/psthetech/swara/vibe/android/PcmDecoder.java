package com.psthetech.swara.vibe.android;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Decodes a window from the middle of a song to mono float PCM at a fixed sample rate. */
public final class PcmDecoder {
    private static final long TIMEOUT_US = 10_000;
    private static final long WALL_LIMIT_MS = 10_000;
    /** On hitting the wall limit, keep what was decoded if it covers at least this long. */
    private static final int MIN_PARTIAL_SECONDS = 10;
    /** Buffer sizing never assumes more than this rate, so hi-res files can't exhaust the heap. */
    static final int MAX_SIZING_RATE = 96_000;

    private PcmDecoder() {}

    /**
     * Decodes {@code seconds} of audio starting at max(0, duration/2 - 15 s), downmixed to mono and
     * resampled to {@code targetRate}. Returns null on any failure. After 10 s of wall time it returns
     * the audio decoded so far if that covers at least 10 s, otherwise null.
     */
    @Nullable
    public static float[] decodeMiddle(Context ctx, Uri uri, int seconds, int targetRate) {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            long deadline = SystemClock.elapsedRealtime() + WALL_LIMIT_MS;
            extractor.setDataSource(ctx, uri, null);
            int track = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i;
                    format = f;
                    break;
                }
            }
            if (track < 0) return null;
            extractor.selectTrack(track);

            long durationUs = format.containsKey(MediaFormat.KEY_DURATION)
                    ? format.getLong(MediaFormat.KEY_DURATION) : 0;
            long startUs = Math.max(0, durationUs / 2 - 15_000_000L);
            if (startUs > 0) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format, null, null, 0);
            codec.start();

            int rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int encoding = AudioFormat.ENCODING_PCM_16BIT;

            if (rate <= 0) return null;
            // Above 96 kHz the window is simply shorter (still well over MIN_PARTIAL_SECONDS).
            float[] mono = new float[bufferFrames(seconds, rate)];
            int n = 0;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;

            // Pipelined loop: feed every free input buffer and drain every ready output buffer
            // per pass, so a slow (throttled) codec isn't paced by one round trip per frame.
            while (!outputDone && n < mono.length) {
                if (SystemClock.elapsedRealtime() > deadline) {
                    return keepPartial(n, rate) ? resample(mono, n, rate, targetRate) : null;
                }
                boolean fed = false;
                while (!inputDone) {
                    int in = codec.dequeueInputBuffer(0);
                    if (in < 0) break;
                    ByteBuffer ib = codec.getInputBuffer(in);
                    int size = extractor.readSampleData(ib, 0);
                    if (size < 0) {
                        codec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        codec.queueInputBuffer(in, 0, size, extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                    fed = true;
                }
                // Block briefly only when this pass queued nothing, to avoid spinning.
                long wait = fed ? 0 : TIMEOUT_US;
                while (!outputDone && n < mono.length) {
                    int out = codec.dequeueOutputBuffer(info, wait);
                    wait = 0;
                    if (out == MediaCodec.INFO_TRY_AGAIN_LATER) break;
                    if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat of = codec.getOutputFormat();
                        if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        encoding = of.containsKey(MediaFormat.KEY_PCM_ENCODING)
                                ? of.getInteger(MediaFormat.KEY_PCM_ENCODING) : AudioFormat.ENCODING_PCM_16BIT;
                    } else if (out >= 0) {
                        ByteBuffer ob = codec.getOutputBuffer(out);
                        if (ob != null && info.size > 0) {
                            ob.position(info.offset);
                            ob.limit(info.offset + info.size);
                            n = appendMono(ob.order(ByteOrder.nativeOrder()), encoding, channels, mono, n);
                        }
                        codec.releaseOutputBuffer(out, false);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                    }
                }
            }
            if (n == 0) return null;
            return resample(mono, n, rate, targetRate);
        } catch (Exception | OutOfMemoryError e) {
            // Analysis shares the playback process; a failed song must never crash it.
            return null;
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (Exception ignored) { }
                try { codec.release(); } catch (Exception ignored) { }
            }
            try { extractor.release(); } catch (Exception ignored) { }
        }
    }

    /** Mono buffer length for {@code seconds} at {@code rate}, sized at no more than 96 kHz. */
    static int bufferFrames(int seconds, int rate) {
        long frames = (long) Math.max(0, seconds) * Math.min(Math.max(0, rate), MAX_SIZING_RATE);
        return (int) Math.min(frames, Integer.MAX_VALUE);
    }

    /** True when {@code monoFrames} decoded at {@code rate} are enough to keep after a timeout. */
    static boolean keepPartial(int monoFrames, int rate) {
        return rate > 0 && monoFrames >= (long) MIN_PARTIAL_SECONDS * rate;
    }

    /**
     * Downmixes interleaved frames in {@code buf} into mono[n..]; returns the new count. Reads in
     * bulk into a scratch array, since per-sample ByteBuffer gets are slow on a throttled core.
     */
    static int appendMono(ByteBuffer buf, int encoding, int channels, float[] mono, int n) {
        int ch = Math.max(1, channels);
        int bytes = encoding == AudioFormat.ENCODING_PCM_FLOAT ? 4
                : encoding == AudioFormat.ENCODING_PCM_8BIT ? 1 : 2;
        int frames = Math.min(buf.remaining() / (bytes * ch), mono.length - n);
        if (frames <= 0) return n;
        int samples = frames * ch;
        float inv = 1f / ch;
        switch (bytes) {
            case 4: {
                float[] s = new float[samples];
                buf.asFloatBuffer().get(s);
                for (int f = 0, i = 0; f < frames; f++) {
                    float sum = 0;
                    for (int c = 0; c < ch; c++) sum += s[i++];
                    mono[n++] = sum * inv;
                }
                break;
            }
            case 1: {
                byte[] s = new byte[samples];
                buf.get(s);
                for (int f = 0, i = 0; f < frames; f++) {
                    float sum = 0;
                    for (int c = 0; c < ch; c++) sum += ((s[i++] & 0xFF) - 128) / 128f;
                    mono[n++] = sum * inv;
                }
                break;
            }
            default: {
                short[] s = new short[samples];
                buf.asShortBuffer().get(s);
                for (int f = 0, i = 0; f < frames; f++) {
                    float sum = 0;
                    for (int c = 0; c < ch; c++) sum += s[i++];
                    mono[n++] = sum * inv / 32768f;
                }
                break;
            }
        }
        return n;
    }

    /** Linear-interpolation resampler. */
    static float[] resample(float[] in, int len, int srcRate, int dstRate) {
        if (srcRate == dstRate) {
            float[] o = new float[len];
            System.arraycopy(in, 0, o, 0, len);
            return o;
        }
        int outLen = (int) ((long) len * dstRate / srcRate);
        float[] out = new float[outLen];
        double step = (double) srcRate / dstRate;
        for (int i = 0; i < outLen; i++) {
            double pos = i * step;
            int i0 = (int) pos;
            int i1 = Math.min(i0 + 1, len - 1);
            double frac = pos - i0;
            out[i] = (float) (in[i0] * (1 - frac) + in[i1] * frac);
        }
        return out;
    }
}
