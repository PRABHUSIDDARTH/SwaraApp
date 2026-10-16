package com.psthetech.swara.vibe;

/**
 * Vibe Mode ramp state machine. Starts at the current song's mood (valence, energy),
 * holds for two picks, then lifts valence gently toward a goal along a smoothstep curve.
 * Pure Java, not thread-safe (callers synchronize).
 */
public final class VibeSession {

    private static final int HOLD_PICKS = 2;
    private static final int RAMP_STEPS = 8;
    private static final double V_CAP = 0.08;
    private static final double V_GOAL_MIN = 0.70;
    private static final double E_GOAL_MIN = 0.55;
    private static final double E_GOAL_MAX = 0.65;
    private static final double SNAP = 1e-9;
    private static final String TAG = "v1";

    private final double v0;
    private final double e0;
    private final double vGoal;
    private final double eGoal;
    private int k;
    private int picks;
    private double curV;
    private double curE;
    private boolean halve;
    private int skips;

    public VibeSession(double v0, double e0) {
        this.v0 = clamp01(v0);
        this.e0 = clamp01(e0);
        this.vGoal = Math.max(this.v0, V_GOAL_MIN);
        this.eGoal = Math.min(E_GOAL_MAX, Math.max(E_GOAL_MIN, this.e0));
        this.curV = this.v0;
        this.curE = this.e0;
    }

    private static double clamp01(double x) {
        if (Double.isNaN(x)) return 0.5;
        return Math.min(1.0, Math.max(0.0, x));
    }

    private static double smoothstep(double x) {
        return x * x * (3 - 2 * x);
    }

    private double rampV(int step) {
        return v0 + (vGoal - v0) * smoothstep(step / (double) RAMP_STEPS);
    }

    private double rampE(int step) {
        return e0 + (eGoal - e0) * smoothstep(step / (double) RAMP_STEPS);
    }

    /** Returns the target {valence, energy} for the next pick and advances the state. */
    public double[] nextTarget() {
        if (picks < HOLD_PICKS) {
            picks++;
            return new double[]{curV, curE};
        }
        picks++;
        boolean halved = halve;
        halve = false;
        if (!halved) k = Math.min(k + 1, RAMP_STEPS);

        double pv = rampV(k);
        double pe = rampE(k);

        double eDelta = pe - curE;
        curE = curE + (halved ? 0.5 * eDelta : eDelta);

        double d = Math.max(0.0, pv - curV); // valence never decreases here (v0 <= vGoal)
        d = Math.min(d, halved ? V_CAP / 2 : V_CAP);
        curV += d;
        if (Math.abs(vGoal - curV) < SNAP) curV = vGoal;
        return new double[]{curV, curE};
    }

    public void onEarlySkip() {
        halve = true;
        skips++;
        if (skips >= 2) {
            k = Math.max(0, k - 1);
            curV = rampV(k);
            curE = rampE(k);
            skips = 0;
        }
    }

    public void onPickAccepted() {
        skips = 0;
    }

    public double v0() { return v0; }

    public double e0() { return e0; }

    public int step() { return k; }

    public String serialize() {
        return TAG + ";" + Double.toString(v0) + ";" + Double.toString(e0) + ";" + k + ";"
                + picks + ";" + Double.toString(curV) + ";" + Double.toString(curE) + ";"
                + (halve ? 1 : 0) + ";" + skips;
    }

    public static VibeSession deserialize(String s) {
        if (s == null) return null;
        try {
            String[] p = s.split(";", -1);
            if (p.length != 9 || !TAG.equals(p[0])) return null;
            double v0 = Double.parseDouble(p[1]);
            double e0 = Double.parseDouble(p[2]);
            int k = Integer.parseInt(p[3]);
            int picks = Integer.parseInt(p[4]);
            double cv = Double.parseDouble(p[5]);
            double ce = Double.parseDouble(p[6]);
            int h = Integer.parseInt(p[7]);
            int skips = Integer.parseInt(p[8]);
            if (!finite01(v0) || !finite01(e0) || !finite01(cv) || !finite01(ce)) return null;
            if (k < 0 || k > RAMP_STEPS || picks < 0 || skips < 0 || skips > 1) return null;
            if (h != 0 && h != 1) return null;
            VibeSession r = new VibeSession(v0, e0);
            r.k = k;
            r.picks = picks;
            r.curV = cv;
            r.curE = ce;
            r.halve = h == 1;
            r.skips = skips;
            return r;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean finite01(double x) {
        return !Double.isNaN(x) && x >= 0.0 && x <= 1.0;
    }
}
