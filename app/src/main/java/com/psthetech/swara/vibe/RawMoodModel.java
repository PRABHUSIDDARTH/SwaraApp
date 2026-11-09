package com.psthetech.swara.vibe;

import com.psthetech.swara.vibe.dsp.AudioFeatures;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Maps low-level audio features to raw (un-normalized) energy and valence in [0, 1].
 *
 * <p>Only the ordering matters: stored values are library percentiles of these raw values.
 * Weights were chosen on 30 labelled songs (dance / pleasant / sad): energy is onset density
 * plus loudness; valence adds tempo and major/minor so that dance songs read happiest, pleasant
 * songs next and sad songs lowest. Spectral centroid did not help and is not used.
 */
public final class RawMoodModel {
    public static final double E_W_RMS = 0.5;
    public static final double E_W_ONSET = 0.5;

    public static final double V_W_ONSET = 0.3;
    public static final double V_W_RMS = 0.3;
    public static final double V_W_TEMPO = 0.2;
    public static final double V_W_MAJOR = 0.2;

    public static final double RMS_MIN = 1e-3, RMS_MAX = 0.5;
    public static final double ONSET_MIN = 0, ONSET_MAX = 8;
    public static final double TEMPO_MIN = 60, TEMPO_MAX = 180;

    private static final int PACK_BYTES = 24;

    private RawMoodModel() {}

    public static double rawEnergy(AudioFeatures f) {
        return E_W_RMS * logUnit(f.rms, RMS_MIN, RMS_MAX)
                + E_W_ONSET * linUnit(f.onsetRate, ONSET_MIN, ONSET_MAX);
    }

    public static double rawValence(AudioFeatures f) {
        return V_W_ONSET * linUnit(f.onsetRate, ONSET_MIN, ONSET_MAX)
                + V_W_RMS * logUnit(f.rms, RMS_MIN, RMS_MAX)
                + V_W_TEMPO * linUnit(f.tempoBpm, TEMPO_MIN, TEMPO_MAX)
                + V_W_MAJOR * linUnit(f.majorness, -1, 1);
    }

    private static double linUnit(double x, double lo, double hi) {
        if (Double.isNaN(x)) return 0;
        double c = Math.max(lo, Math.min(hi, x));
        return (c - lo) / (hi - lo);
    }

    private static double logUnit(double x, double lo, double hi) {
        if (Double.isNaN(x)) return 0;
        double c = Math.max(lo, Math.min(hi, x));
        return (Math.log(c) - Math.log(lo)) / (Math.log(hi) - Math.log(lo));
    }

    public static byte[] pack(double rawValence, double rawEnergy, double tempo) {
        return ByteBuffer.allocate(PACK_BYTES).order(ByteOrder.LITTLE_ENDIAN)
                .putDouble(rawValence).putDouble(rawEnergy).putDouble(tempo).array();
    }

    /** Returns {rawValence, rawEnergy, tempo}, or null if the input is null or malformed. */
    public static double[] unpack(byte[] b) {
        if (b == null || b.length != PACK_BYTES) return null;
        ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        return new double[]{bb.getDouble(), bb.getDouble(), bb.getDouble()};
    }
}
