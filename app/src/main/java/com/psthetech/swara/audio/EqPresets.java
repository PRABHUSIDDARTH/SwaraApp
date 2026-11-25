package com.psthetech.swara.audio;

/**
 * Pure equalizer maths: the five fixed UI bands, built-in presets and the mapping from those
 * bands onto whatever bands the device's Equalizer effect really has. No Android dependencies.
 */
public final class EqPresets {

    private EqPresets() {}

    /** Centre frequencies (Hz) of the five bands shown in the UI. */
    public static final int[] REF_FREQS_HZ = {60, 230, 910, 3600, 14000};
    public static final int BANDS = REF_FREQS_HZ.length;
    /** UI range in millibels (±12 dB), snapped to 1 dB. */
    public static final int RANGE_MB = 1200;
    public static final int STEP_MB = 100;

    public static final String CUSTOM = "custom";

    public static final class Preset {
        public final String id;
        public final String label;
        /** Gain per UI band, in dB. */
        public final int[] gainsDb;
        Preset(String id, String label, int... gainsDb) {
            this.id = id;
            this.label = label;
            this.gainsDb = gainsDb;
        }
        public int[] levelsMb() {
            int[] mb = new int[gainsDb.length];
            for (int i = 0; i < mb.length; i++) mb[i] = gainsDb[i] * 100;
            return mb;
        }
    }

    public static final Preset[] PRESETS = {
            new Preset("flat", "Flat", 0, 0, 0, 0, 0),
            new Preset("bass", "Bass boost", 7, 5, 1, 0, 0),
            new Preset("treble", "Treble boost", 0, 0, 0, 4, 7),
            new Preset("vocal", "Vocal", -2, 0, 4, 4, 1),
            new Preset("rock", "Rock", 5, 3, -2, 3, 5),
            new Preset("pop", "Pop", -1, 3, 5, 2, -1),
            new Preset("jazz", "Jazz", 4, 2, -2, 2, 5),
            new Preset("classical", "Classical", 5, 3, -1, 3, 4),
            new Preset("dance", "Dance", 7, 4, 0, 3, 3),
    };

    public static Preset find(String id) {
        if (id == null) return null;
        for (Preset p : PRESETS) if (p.id.equals(id)) return p;
        return null;
    }

    /** Clamp to the UI range and snap to the step. */
    public static int snap(int mb) {
        int c = Math.max(-RANGE_MB, Math.min(RANGE_MB, mb));
        return Math.round(c / (float) STEP_MB) * STEP_MB;
    }

    /** The preset whose levels equal {@code levelsMb}, or null if none does. */
    public static Preset matching(int[] levelsMb) {
        for (Preset p : PRESETS) {
            if (java.util.Arrays.equals(p.levelsMb(), levelsMb)) return p;
        }
        return null;
    }

    /**
     * Map UI levels onto the device's bands by interpolating on a log-frequency axis.
     *
     * @param refMb          levels for {@link #REF_FREQS_HZ} in millibels
     * @param deviceCentersMilliHz centre frequency of each device band, in milli-Hertz
     *                       (as returned by Equalizer.getCenterFreq)
     * @param minMb          device minimum band level
     * @param maxMb          device maximum band level
     */
    public static int[] mapToDevice(int[] refMb, int[] deviceCentersMilliHz, int minMb, int maxMb) {
        int[] out = new int[deviceCentersMilliHz.length];
        for (int i = 0; i < out.length; i++) {
            double hz = Math.max(1.0, deviceCentersMilliHz[i] / 1000.0);
            double x = Math.log(hz);
            double v;
            if (hz <= REF_FREQS_HZ[0]) {
                v = refMb[0];
            } else if (hz >= REF_FREQS_HZ[BANDS - 1]) {
                v = refMb[BANDS - 1];
            } else {
                int k = 0;
                while (k < BANDS - 2 && hz > REF_FREQS_HZ[k + 1]) k++;
                double x0 = Math.log(REF_FREQS_HZ[k]);
                double x1 = Math.log(REF_FREQS_HZ[k + 1]);
                double t = (x - x0) / (x1 - x0);
                v = refMb[k] + t * (refMb[k + 1] - refMb[k]);
            }
            out[i] = Math.max(minMb, Math.min(maxMb, (int) Math.round(v)));
        }
        return out;
    }

    public static String encode(int[] levels) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < levels.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(levels[i]);
        }
        return sb.toString();
    }

    /** Parse a stored level list; anything malformed yields all zeros. */
    public static int[] decode(String s) {
        int[] out = new int[BANDS];
        if (s == null) return out;
        String[] parts = s.split(",");
        if (parts.length != BANDS) return out;
        try {
            for (int i = 0; i < BANDS; i++) out[i] = snap(Integer.parseInt(parts[i].trim()));
        } catch (NumberFormatException e) {
            return new int[BANDS];
        }
        return out;
    }
}
