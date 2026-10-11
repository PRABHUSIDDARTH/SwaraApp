package com.psthetech.swara.widget;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WidgetArtTest {

    @Test
    public void levelBarsStayInVisibleRange() {
        for (float seed = 0f; seed < 50f; seed += 0.37f) {
            float[] l = WidgetArt.levelsFor(seed);
            assertEquals(3, l.length);
            for (float v : l) {
                assertTrue(v >= 0.35f - 1e-6f && v <= 1f + 1e-6f);
            }
        }
    }

    @Test
    public void levelBarsAreDeterministic() {
        assertArrayEquals(WidgetArt.levelsFor(4.2f), WidgetArt.levelsFor(4.2f), 0f);
    }
}
