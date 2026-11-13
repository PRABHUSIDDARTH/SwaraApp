package com.psthetech.swara.voice.audio;

/**
 * Scales audio to a speech level the STT model was trained on. Hot or clipped mic input (e.g.
 * Samsung VOICE_RECOGNITION + AEC) makes Moonshine emit an empty transcript. Pure function.
 */
public final class LevelNormalizer {
    static final float TARGET_RMS = 0.05f;
    static final float MIN_GAIN = 0.1f;
    static final float MAX_GAIN = 8.0f;
    static final float MAX_PEAK = 0.9f;
    static final int FRAME = 1280;
    static final float ACTIVE_RMS = 0.01f;

    private LevelNormalizer() {}

    /** Returns a new array; the input is never modified. */
    public static float[] normalize(float[] audio) {
        float[] out = audio.clone();
        int n = audio.length;
        float peak = 0f;
        for (float v : audio) peak = Math.max(peak, Math.abs(v));
        if (n == 0 || peak == 0f) return out;

        double activeSum = 0;
        long activeCount = 0;
        for (int s = 0; s < n; s += FRAME) {
            int e = Math.min(n, s + FRAME);
            double sum = 0;
            for (int i = s; i < e; i++) sum += (double) audio[i] * audio[i];
            if (Math.sqrt(sum / (e - s)) >= ACTIVE_RMS) {
                activeSum += sum;
                activeCount += e - s;
            }
        }
        if (activeCount == 0) {
            for (float v : audio) activeSum += (double) v * v;
            activeCount = n;
        }
        double rms = Math.sqrt(activeSum / activeCount);
        float gain = rms > 0 ? (float) (TARGET_RMS / rms) : MAX_GAIN;
        gain = Math.max(MIN_GAIN, Math.min(MAX_GAIN, gain));
        gain = Math.min(gain, MAX_PEAK / peak);
        for (int i = 0; i < n; i++) out[i] = audio[i] * gain;
        return out;
    }
}
