package com.psthetech.swara.ui.deck;

/** A 5x7 dot-matrix font with just the characters the time readout needs. */
public final class DeckDotFont {

    public static final int COLUMNS = 5;
    public static final int ROWS = 7;

    private static final String CHARS = "0123456789:/-";

    /** One int per row; bit 4 is the left-most column. */
    private static final int[][] GLYPHS = {
            {14, 17, 19, 21, 25, 17, 14}, // 0
            {4, 12, 4, 4, 4, 4, 14},      // 1
            {14, 17, 1, 2, 4, 8, 31},     // 2
            {31, 2, 4, 2, 1, 17, 14},     // 3
            {2, 6, 10, 18, 31, 2, 2},     // 4
            {31, 16, 30, 1, 1, 17, 14},   // 5
            {6, 8, 16, 30, 17, 17, 14},   // 6
            {31, 1, 2, 4, 8, 8, 8},       // 7
            {14, 17, 17, 14, 17, 17, 14}, // 8
            {14, 17, 17, 15, 1, 2, 12},   // 9
            {0, 4, 4, 0, 4, 4, 0},        // :
            {0, 1, 2, 4, 8, 16, 0},       // /
            {0, 0, 0, 31, 0, 0, 0},       // -
    };

    private DeckDotFont() { }

    public static boolean isLit(char c, int column, int row) {
        int glyph = CHARS.indexOf(c);
        if (glyph < 0) return false;
        return (GLYPHS[glyph][row] & (1 << (COLUMNS - 1 - column))) != 0;
    }
}
