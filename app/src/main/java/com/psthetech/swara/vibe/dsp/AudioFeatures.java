package com.psthetech.swara.vibe.dsp;

/** Low-level descriptors of a clip, produced by {@link FeatureExtractor}. */
public final class AudioFeatures {
    public final double rms;
    /** Onsets per second. */
    public final double onsetRate;
    public final double tempoBpm;
    public final double centroidHz;
    /** -1 (minor) to +1 (major). */
    public final double majorness;

    public AudioFeatures(double rms, double onsetRate, double tempoBpm, double centroidHz, double majorness) {
        this.rms = rms;
        this.onsetRate = onsetRate;
        this.tempoBpm = tempoBpm;
        this.centroidHz = centroidHz;
        this.majorness = majorness;
    }
}
