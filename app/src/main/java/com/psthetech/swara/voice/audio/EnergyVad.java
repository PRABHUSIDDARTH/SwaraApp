package com.psthetech.swara.voice.audio;

/** Energy voice-activity detector: RMS against an adaptive noise floor. Not thread-safe. */
public final class EnergyVad {
    private static final float ALPHA = 0.05f;
    private static final float FLOOR_FACTOR = 3f;
    private static final float MIN_RMS = 0.01f;
    /** A first frame this loud is assumed to be speech rather than background noise. */
    private static final float LOUD_FIRST_FRAME = 0.1f;
    private static final float DEFAULT_FLOOR = 0.005f;

    private final int hopSamples;
    private float floor = -1f;

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

        if (floor < 0f) {
            // Seed the floor from the first frame unless it is clearly speech.
            if (rms > LOUD_FIRST_FRAME) {
                floor = DEFAULT_FLOOR;
                return true;
            }
            floor = rms;
            return false;
        }
        boolean speech = rms > floor * FLOOR_FACTOR && rms > MIN_RMS;
        if (!speech) floor += ALPHA * (rms - floor);
        return speech;
    }
}
