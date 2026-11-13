package com.psthetech.swara.voice.audio;

/** Energy voice-activity detector: RMS against an adaptive noise floor. Not thread-safe. */
public final class EnergyVad {
    private static final float INITIAL_FLOOR = 0.005f;
    private static final float MIN_FLOOR = 0.002f;
    private static final float FLOOR_FACTOR = 3f;
    private static final float MIN_RMS = 0.01f;
    private static final float ALPHA_FALL = 0.3f;    // quieter than floor: follow quickly
    private static final float ALPHA_NOISE = 0.05f;  // non-speech: the spec's EMA
    private static final float ALPHA_SPEECH = 0.005f; // speech: slow creep to learn loud backgrounds

    private final int hopSamples;
    private float floor = INITIAL_FLOOR;

    public EnergyVad(int hopSamples) {
        this.hopSamples = hopSamples;
    }

    public int hopSamples() {
        return hopSamples;
    }

    public boolean isSpeech(float[] frame) {
        double sum = 0;
        for (float v : frame) sum += (double) v * v;
        float rms = frame.length == 0 ? 0f : (float) Math.sqrt(sum / frame.length);

        boolean speech = rms > floor * FLOOR_FACTOR && rms > MIN_RMS;
        if (rms < floor) floor += ALPHA_FALL * (rms - floor);
        else if (!speech) floor += ALPHA_NOISE * (rms - floor);
        else floor += ALPHA_SPEECH * (rms - floor);
        if (floor < MIN_FLOOR) floor = MIN_FLOOR;
        return speech;
    }
}
