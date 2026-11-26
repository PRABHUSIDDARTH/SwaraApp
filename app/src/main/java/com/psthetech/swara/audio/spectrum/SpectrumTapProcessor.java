package com.psthetech.swara.audio.spectrum;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;

import java.nio.ByteBuffer;

/**
 * Passes audio through unchanged and, while the tap is enabled, copies 16-bit PCM into its ring.
 *
 * <p>It is always active, for every format: switching a processor in or out of the chain only
 * takes effect by reconfiguring the audio output, which would interrupt playback. On the audio
 * thread it does nothing heavier than copying the buffer (once for output, once for the ring).
 */
@UnstableApi
public final class SpectrumTapProcessor extends BaseAudioProcessor {

    private final SpectrumTap tap;

    // Audio thread only.
    private int sampleRate;
    private int channelCount;
    private boolean gap;

    public SpectrumTapProcessor(@NonNull SpectrumTap tap) {
        this.tap = tap;
    }

    @NonNull
    @Override
    protected AudioFormat onConfigure(@NonNull AudioFormat inputAudioFormat) {
        // Only a pending format: it applies at the next flush, after the old audio has drained.
        return inputAudioFormat;
    }

    @Override
    protected void onFlush() {
        boolean readable = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
                && inputAudioFormat.channelCount <= PcmRing.MAX_CHANNELS;
        sampleRate = readable ? inputAudioFormat.sampleRate : 0;
        channelCount = readable ? inputAudioFormat.channelCount : 0;
        gap = false;
        tap.ring().startEpoch(sampleRate, channelCount, true);
    }

    @Override
    public void queueInput(@NonNull ByteBuffer inputBuffer) {
        int bytes = inputBuffer.remaining();
        if (bytes == 0) return;
        if (sampleRate > 0) {
            if (!tap.isEnabled()) {
                gap = true; // audio is passing uncopied; the ring is going stale
            } else {
                if (gap) {
                    tap.ring().startEpoch(sampleRate, channelCount, false);
                    gap = false;
                }
                tap.ring().write(inputBuffer);
            }
        }
        replaceOutputBuffer(bytes).put(inputBuffer).flip();
    }

    @Override
    protected void onReset() {
        sampleRate = 0;
        channelCount = 0;
        gap = false;
        tap.ring().startEpoch(0, 0, true);
    }
}
