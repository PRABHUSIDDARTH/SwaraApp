package com.psthetech.swara.audio.spectrum;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A ring of 16-bit PCM shared by exactly one writer (the audio thread) and one reader (the UI
 * thread). Neither side locks or waits, and the writer never allocates.
 *
 * <p><b>Audio data.</b> Positions are byte counts that only ever grow. Before copying, the writer
 * raises {@code claimed} to where the copy will end; after copying it sets {@code committed}. The
 * reader takes {@code committed}, copies its window out, then looks at {@code claimed} again. If
 * the writer has claimed more than one ring length past the window's start, the copy may be torn
 * and is thrown away.
 *
 * <p>Both looks at {@code claimed} that matter are atomic read-modify-writes
 * ({@code getAndSet} by the writer, {@code getAndAdd(0)} by the reader), so Java orders them
 * against each other. If the reader's comes first, the writer's synchronizes with it: the
 * reader's copy happens-before the writer's data writes, so it cannot have seen any of them. If
 * the writer's comes first, the reader sees the new claim and rejects the window. A plain
 * volatile read would not do: earlier loads of the audio could be reordered after it.
 *
 * <p><b>Format and epochs.</b> Rate, channels and where the current epoch starts change together
 * under {@code sequence} (odd while they are being written), checked by the same rule. A new
 * epoch makes everything written before it unreadable, which is how old audio is "cleared":
 * the reader never has to erase memory the writer might be using.
 */
public final class PcmRing {

    public static final int MAX_CHANNELS = 8;
    /** Longest window, in frames, that {@link #read} accepts. */
    public static final int MAX_WINDOW_FRAMES = 2048;

    private final byte[] data;
    private final int mask;

    private final AtomicLong claimed = new AtomicLong();
    private final AtomicLong committed = new AtomicLong();
    private final AtomicLong sequence = new AtomicLong();

    // Guarded by sequence.
    private long epochStart;
    private int sampleRate;
    private int channels;
    private boolean alignedWithPlayback;

    // Writer thread only.
    private long writePosition;

    // Reader thread only.
    private final byte[] window = new byte[MAX_WINDOW_FRAMES * MAX_CHANNELS * 2];
    private final ByteBuffer windowView = ByteBuffer.wrap(window).order(ByteOrder.nativeOrder());
    private long floor;
    private long lastEnd;
    private int lastSampleRate;

    public PcmRing(int capacityBytes) {
        if (Integer.bitCount(capacityBytes) != 1) {
            throw new IllegalArgumentException("capacity must be a power of two");
        }
        data = new byte[capacityBytes];
        mask = capacityBytes - 1;
    }

    // ── Writer (audio thread) ────────────────────────────────────────────────────

    /**
     * Starts a new epoch; everything written before it can no longer be read.
     *
     * @param rate    sample rate, or 0 for "no readable audio"
     * @param aligned true when the first audio written next is also the next to be heard (after a
     *                flush); false after a gap in copying, when it is a playback buffer ahead
     */
    public void startEpoch(int rate, int channelCount, boolean aligned) {
        sequence.getAndIncrement();
        epochStart = writePosition;
        sampleRate = rate;
        channels = channelCount;
        alignedWithPlayback = aligned;
        sequence.getAndIncrement();
    }

    /** Copies the remaining bytes of {@code source}, leaving its position where it was. */
    public void write(@NonNull ByteBuffer source) {
        int total = source.remaining();
        if (total == 0) return;
        int position = source.position();
        long end = writePosition + total;
        claimed.getAndSet(end);

        int length = Math.min(total, data.length);
        if (length < total) source.position(position + total - length);
        int at = (int) ((end - length) & mask);
        int first = Math.min(length, data.length - at);
        source.get(data, at, first);
        if (length > first) source.get(data, 0, length - first);
        source.position(position);

        writePosition = end;
        committed.set(end);
    }

    // ── Reader (UI thread) ───────────────────────────────────────────────────────

    /**
     * Copies the {@code out.length} frames that end {@code lagFrames} behind the newest audio,
     * mixed down to mono in -1..1. Returns false, leaving {@code out} unusable, if no complete,
     * current and untorn window is available.
     */
    public boolean read(@NonNull float[] out, long lagFrames) {
        long before = sequence.get();
        if ((before & 1) != 0) return false;
        long start = epochStart;
        int rate = sampleRate;
        int channelCount = channels;
        boolean aligned = alignedWithPlayback;
        if (rate <= 0 || channelCount <= 0 || channelCount > MAX_CHANNELS
                || out.length > MAX_WINDOW_FRAMES) {
            return false;
        }

        int frameBytes = 2 * channelCount;
        int length = out.length * frameBytes;
        long newest = committed.get();
        long end = newest - lagFrames * frameBytes;
        // Just after a flush less than a buffer has been written, and what is audible is the
        // start of the epoch.
        if (aligned) end = Math.max(end, start + length);
        long begin = end - length;
        if (end > newest || begin < start || begin < floor) return false;

        int at = (int) (begin & mask);
        int first = Math.min(length, data.length - at);
        System.arraycopy(data, at, window, 0, first);
        if (length > first) System.arraycopy(data, 0, window, first, length - first);

        if (claimed.getAndAdd(0) - begin > data.length) return false; // lapped while copying
        if (sequence.getAndAdd(0) != before) return false;            // epoch changed

        float scale = 1f / (32768f * channelCount);
        for (int frame = 0; frame < out.length; frame++) {
            int sum = 0;
            for (int channel = 0; channel < channelCount; channel++) {
                sum += windowView.getShort(frame * frameBytes + 2 * channel);
            }
            out[frame] = sum * scale;
        }
        lastEnd = end;
        lastSampleRate = rate;
        return true;
    }

    /** Sample rate of the last window {@link #read} returned. */
    public int lastSampleRate() {
        return lastSampleRate;
    }

    /** Hides everything written so far, including audio still queued: used on a seek or skip. */
    public void discardWritten() {
        floor = Math.max(floor, committed.get());
    }

    /**
     * Hides only what has already been shown, keeping queued audio: used on a track change, when
     * the ring may already hold the start of the new track.
     */
    public void discardShown() {
        floor = Math.max(floor, lastEnd);
    }
}
