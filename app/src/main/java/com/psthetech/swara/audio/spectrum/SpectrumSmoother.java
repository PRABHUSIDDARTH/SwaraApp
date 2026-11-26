package com.psthetech.swara.audio.spectrum;

import androidx.annotation.NonNull;

import com.psthetech.swara.ui.widget.DiscSpinPhysics;

/** Frame-rate independent bar movement: a quick rise and a slower fall. */
public final class SpectrumSmoother {

    private static final float ATTACK_TAU_MS = 20f;
    private static final float RELEASE_TAU_MS = 180f;
    private static final float SNAP = 0.005f;

    private SpectrumSmoother() { }

    /** Moves each bar toward its target by {@code dtMs} of time. */
    public static void step(@NonNull float[] bars, @NonNull float[] target, float dtMs) {
        for (int i = 0; i < bars.length; i++) {
            bars[i] = DiscSpinPhysics.approach(bars[i], target[i], dtMs,
                    target[i] > bars[i] ? ATTACK_TAU_MS : RELEASE_TAU_MS);
            if (Math.abs(bars[i] - target[i]) < SNAP) bars[i] = target[i];
        }
    }
}
