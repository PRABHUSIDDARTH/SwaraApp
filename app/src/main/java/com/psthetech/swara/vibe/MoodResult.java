package com.psthetech.swara.vibe;

/** Outcome of scoring one song. */
public final class MoodResult {
    public final boolean ok;
    public final double valence;
    public final double energy;
    public final double tempoBpm;
    /** Packed raw values, see {@link RawMoodModel#pack}. */
    public final byte[] raw;
    public final int scorerVersion;

    public MoodResult(boolean ok, double valence, double energy, double tempoBpm,
                      byte[] raw, int scorerVersion) {
        this.ok = ok;
        this.valence = valence;
        this.energy = energy;
        this.tempoBpm = tempoBpm;
        this.raw = raw;
        this.scorerVersion = scorerVersion;
    }

    public static MoodResult failed(int version) {
        return new MoodResult(false, 0.5, 0.5, 0, null, version);
    }
}
