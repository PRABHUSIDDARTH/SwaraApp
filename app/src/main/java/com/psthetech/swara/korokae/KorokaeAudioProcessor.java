package com.psthetech.swara.korokae;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;
import androidx.media3.common.util.UnstableApi;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Media3 AudioProcessor that removes vocals in real time using {@link KorokaeEngine}.
 *
 * Lives inside ExoPlayer's audio sink (playback thread). It is always configured for
 * 44.1 kHz / 48 kHz stereo 16-bit PCM so toggling Korokae never re-initialises the
 * AudioTrack; when disabled it crossfades to an exact (delayed) passthrough, and once the
 * fade completes it becomes a zero-cost passthrough again (isActive stays true only while
 * the processor is engaged, to avoid unnecessary latency).
 *
 * Other formats (mono, surround, float, unusual sample rates) are left untouched.
 */
@UnstableApi
public final class KorokaeAudioProcessor extends BaseAudioProcessor {

    private final KorokaeModelProvider modelProvider;

    private volatile boolean enabled;
    @Nullable private KorokaeEngine engine;
    private float[] scratch = new float[0];
    private boolean supportedFormat;

    public KorokaeAudioProcessor(@NonNull KorokaeModelProvider modelProvider) {
        this.modelProvider = modelProvider;
    }

    /** Thread-safe: called from the main thread; picked up on the next audio buffer. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat)
            throws UnhandledAudioFormatException {
        supportedFormat = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
                && inputAudioFormat.channelCount == 2
                && (inputAudioFormat.sampleRate == 44100 || inputAudioFormat.sampleRate == 48000);
        // Returning NOT_SET marks the processor inactive for unsupported formats.
        return supportedFormat ? inputAudioFormat : AudioFormat.NOT_SET;
    }

    @Override
    public void queueInput(ByteBuffer inputBuffer) {
        int bytes = inputBuffer.remaining();
        if (bytes == 0) return;
        ByteBuffer out = replaceOutputBuffer(bytes);

        KorokaeEngine e = obtainEngine();
        if (e == null) {
            out.put(inputBuffer);
            out.flip();
            return;
        }
        e.setMix(enabled ? 1f : 0f);

        int frames = bytes / 4;
        if (scratch.length < 2 * frames) scratch = new float[2 * frames];
        ByteBuffer in = inputBuffer.order(ByteOrder.nativeOrder());
        for (int i = 0; i < 2 * frames; i++) {
            scratch[i] = in.getShort() / 32768f;
        }
        e.process(scratch, 0, scratch, 0, frames);
        for (int i = 0; i < 2 * frames; i++) {
            float v = scratch[i] * 32768f;
            if (v > 32767f) v = 32767f;
            else if (v < -32768f) v = -32768f;
            out.putShort((short) Math.round(v));
        }
        out.flip();
    }

    @Nullable
    private KorokaeEngine obtainEngine() {
        if (engine == null) {
            KorokaeModel model = modelProvider.getIfLoaded();
            if (model == null) {
                if (enabled) modelProvider.loadAsync();
                return null;
            }
            engine = new KorokaeEngine(model);
            // Start from the dry signal so the first engagement fades in.
            engine.setMix(0f);
            engine.snapMix();
        }
        return engine;
    }

    @Override
    protected void onFlush() {
        if (engine != null) {
            engine.setMix(enabled ? 1f : 0f);
            engine.reset(); // seek/track change: drop stale overlap + recurrent state
        }
    }

    @Override
    protected void onReset() {
        engine = null;
        scratch = new float[0];
        supportedFormat = false;
    }
}
