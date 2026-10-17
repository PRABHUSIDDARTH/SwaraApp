package com.psthetech.swara.wrapped.render;

import android.graphics.Color;

/** Rhythm Pop gradient pairs (the user-chosen Wrapped style), cycled by slide index. */
public final class RhythmPopPalette {

    static final int[][] PAIRS = {
            {0xFFFF3D7F, 0xFFFF8A00}, // hot pink → orange
            {0xFF00D2C3, 0xFF3A47D5}, // teal → indigo
            {0xFFC6FF00, 0xFF00C853}, // lime → green
            {0xFFFFC300, 0xFFFF5733}, // saffron → red
            {0xFF8E2DE2, 0xFF4A00E0}, // violet → indigo
            {0xFFFF5F6D, 0xFFFFC371}, // coral → peach
    };
    /** Ink on light gradients, white on dark ones. */
    static final int INK = 0xFF160A26;
    static final int PAPER = Color.WHITE;

    private RhythmPopPalette() {}

    static int[] pair(int index) {
        return PAIRS[Math.floorMod(index, PAIRS.length)];
    }

    /** Text colour with the better contrast against the gradient's average. */
    static int textOn(int[] pair) {
        double l = (luminance(pair[0]) + luminance(pair[1])) / 2;
        return l > 0.35 ? INK : PAPER;
    }

    private static double luminance(int c) {
        double r = lin(Color.red(c)), g = lin(Color.green(c)), b = lin(Color.blue(c));
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double lin(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }
}
