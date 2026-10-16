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

    private PcmDecoder() {}

    /**
     * Decodes {@code seconds} of audio starting at max(0, duration/2 - 15 s), downmixed to mono and
     * resampled to {@code targetRate}. Returns null on any failure or after 10 s of wall time.
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

            long wantMono = (long) seconds * rate;
            float[] mono = new float[(int) wantMono];
            int n = 0;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;

            while (!outputDone && n < mono.length) {
                if (SystemClock.elapsedRealtime() > deadline) return null;
                if (!inputDone) {
                    int in = codec.dequeueInputBuffer(TIMEOUT_US);
                    if (in >= 0) {
                        ByteBuffer ib = codec.getInputBuffer(in);
                        int size = extractor.readSampleData(ib, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(in, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                int out = codec.dequeueOutputBuffer(info, TIMEOUT_US);
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
            if (n == 0) return null;
            return resample(mono, n, rate, targetRate);
        } catch (Exception e) {
            return null;
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (Exception ignored) { }
                try { codec.release(); } catch (Exception ignored) { }
            }
            try { extractor.release(); } catch (Exception ignored) { }
        }
    }

    /** Downmixes interleaved frames in {@code buf} into mono[n..]; returns the new count. */
    static int appendMono(ByteBuffer buf, int encoding, int channels, float[] mono, int n) {
        int ch = Math.max(1, channels);
        int bytes = encoding == AudioFormat.ENCODING_PCM_FLOAT ? 4
                : encoding == AudioFormat.ENCODING_PCM_8BIT ? 1 : 2;
        int frames = buf.remaining() / (bytes * ch);
        for (int f = 0; f < frames && n < mono.length; f++) {
            float sum = 0;
            for (int c = 0; c < ch; c++) {
                switch (bytes) {
                    case 4: sum += buf.getFloat(); break;
                    case 1: sum += ((buf.get() & 0xFF) - 128) / 128f; break;
                    default: sum += buf.getShort() / 32768f; break;
                }
            }
            mono[n++] = sum / ch;
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
