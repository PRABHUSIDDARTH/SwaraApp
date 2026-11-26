package com.psthetech.swara.audio.spectrum;

import androidx.annotation.NonNull;

/**
 * The meeting point between the playback service's audio processor and the Deck spectrum. Both
 * run in the app process.
 */
public final class SpectrumTap {

    /** About 6 s of 44.1 kHz stereo, and still more than one playback buffer at 192 kHz. */
    private static final int RING_BYTES = 1 << 20;
    /** Used until the service reports the real playback buffer. */
    public static final int DEFAULT_LAG_MS = 250;

    private static final SpectrumTap SHARED = new SpectrumTap();

    private final PcmRing ring = new PcmRing(RING_BYTES);
    private volatile boolean enabled;
    private volatile int lagMs = DEFAULT_LAG_MS;

    SpectrumTap() { }

    @NonNull
    public static SpectrumTap shared() {
        return SHARED;
    }

    @NonNull
    public PcmRing ring() {
        return ring;
    }

    /** Turns copying on the audio thread on or off. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Records the size of the 16-bit AudioTrack buffer. The player keeps it full, so audio is
     * heard about that long after the tap sees it. Bluetooth delay is not included.
     */
    public void setOutputBuffer(int sampleRate, int channelCount, int bufferBytes) {
        if (sampleRate <= 0 || channelCount <= 0 || bufferBytes <= 0) return;
        lagMs = (int) (bufferBytes / (2L * channelCount) * 1000L / sampleRate);
    }

    public int lagMs() {
        return lagMs;
    }

    public long lagFrames(int sampleRate) {
        return (long) lagMs * sampleRate / 1000L;
    }
}
