package com.psthetech.swara.vibe;

import com.psthetech.swara.vibe.dsp.AudioFeatures;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Maps low-level audio features to raw (un-normalized) energy and valence in [0, 1]. */
public final class RawMoodModel {
    public static final double E_W_RMS = 0.5;
    public static final double E_W_ONSET = 0.3;
    public static final double E_W_TEMPO = 0.2;

    public static final double V_W_MAJOR = 0.5;
    public static final double V_W_CENTROID = 0.3;
    public static final double V_W_TEMPO = 0.2;

    public static final double RMS_MIN = 1e-3, RMS_MAX = 0.5;
    public static final double ONSET_MIN = 0, ONSET_MAX = 8;
    public static final double TEMPO_MIN = 60, TEMPO_MAX = 180;
    public static final double CENTROID_MIN = 300, CENTROID_MAX = 5000;

    private static final int PACK_BYTES = 24;

    private RawMoodModel() {}

    public static double rawEnergy(AudioFeatures f) {
        return E_W_RMS * logUnit(f.rms, RMS_MIN, RMS_MAX)
                + E_W_ONSET * linUnit(f.onsetRate, ONSET_MIN, ONSET_MAX)
                + E_W_TEMPO * linUnit(f.tempoBpm, TEMPO_MIN, TEMPO_MAX);
    }

    public static double rawValence(AudioFeatures f) {
        return V_W_MAJOR * linUnit(f.majorness, -1, 1)
                + V_W_CENTROID * logUnit(f.centroidHz, CENTROID_MIN, CENTROID_MAX)
                + V_W_TEMPO * linUnit(f.tempoBpm, TEMPO_MIN, TEMPO_MAX);
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
