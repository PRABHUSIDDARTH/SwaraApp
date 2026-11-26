package com.psthetech.swara.ui.deck;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.util.TimeFormatter;

import org.junit.Test;

public class DeckMathTest {

    @Test
    public void faderFractionIsClamped() {
        assertEquals(0f, DeckFaderMath.fraction(-5, 100), 0f);
        assertEquals(0.25f, DeckFaderMath.fraction(25, 100), 1e-6f);
        assertEquals(1f, DeckFaderMath.fraction(500, 100), 0f);
    }

    @Test
    public void faderFractionIsZeroWithoutADuration() {
        assertEquals(0f, DeckFaderMath.fraction(10, 0), 0f);
        assertEquals(0f, DeckFaderMath.fraction(10, -1), 0f);
    }

    @Test
    public void faderCapRunsFromLeftToRight() {
        assertEquals(20f, DeckFaderMath.capCentre(0f, 20f, 220f), 0f);
        assertEquals(120f, DeckFaderMath.capCentre(0.5f, 20f, 220f), 1e-4f);
        assertEquals(220f, DeckFaderMath.capCentre(1f, 20f, 220f), 0f);
        // A track narrower than nothing keeps the cap at the start.
        assertEquals(20f, DeckFaderMath.capCentre(1f, 20f, 10f), 0f);
    }

    @Test
    public void timeTextMatchesTheAppFormatter() {
        char[] out = new char[DeckTimeText.MAX_LENGTH];
        long[] samples = {-1, 0, 999, 1000, 59_999, 60_000, 84_000, 238_000, 599_000,
                3_599_999, 3_600_000, 3_905_000, 36_000_000, 360_000_000L};
        for (long ms : samples) {
            int n = DeckTimeText.write(ms, out, 0);
            assertEquals(TimeFormatter.formatMs(ms), new String(out, 0, n));
        }
    }

    @Test
    public void timeTextWritesAtAnOffsetAndNeverOverflows() {
        char[] out = new char[2 * DeckTimeText.MAX_LENGTH];
        int n = DeckTimeText.write(Long.MAX_VALUE, out, DeckTimeText.MAX_LENGTH);
        assertTrue(n <= DeckTimeText.MAX_LENGTH);
        assertEquals('\0', out[DeckTimeText.MAX_LENGTH - 1]);
    }

    @Test
    public void dotFontDrawsEveryTimeCharacterDistinctly() {
        String chars = "0123456789:/-";
        for (int i = 0; i < chars.length(); i++) {
            assertTrue("blank " + chars.charAt(i), lit(chars.charAt(i)).contains("#"));
            for (int j = i + 1; j < chars.length(); j++) {
                assertFalse(chars.charAt(i) + " = " + chars.charAt(j),
                        lit(chars.charAt(i)).equals(lit(chars.charAt(j))));
            }
        }
    }

    @Test
    public void dotFontLeavesSpaceAndUnknownCharactersBlank() {
        assertFalse(lit(' ').contains("#"));
        assertFalse(lit('x').contains("#"));
    }

    @Test
    public void dotFontOneHasTheExpectedShape() {
        assertEquals("..#..\n.##..\n..#..\n..#..\n..#..\n..#..\n.###.\n", lit('1'));
    }

    private static String lit(char c) {
        StringBuilder b = new StringBuilder();
        for (int row = 0; row < DeckDotFont.ROWS; row++) {
            for (int col = 0; col < DeckDotFont.COLUMNS; col++) {
                b.append(DeckDotFont.isLit(c, col, row) ? '#' : '.');
            }
            b.append('\n');
        }
        return b.toString();
    }
}
