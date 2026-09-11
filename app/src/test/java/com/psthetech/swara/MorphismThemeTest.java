package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.appcompat.app.AppCompatDelegate;

import com.psthetech.swara.domain.model.MorphismStyle;
import com.psthetech.swara.domain.model.ThemeMode;

import org.junit.Test;

/**
 * Unit tests for Theme System and Morphism Design Language System.
 */
public class MorphismThemeTest {

    @Test
    public void testAllMorphismStylesExist() {
        MorphismStyle[] styles = MorphismStyle.values();
        assertEquals(6, styles.length);

        assertNotNull(MorphismStyle.valueOf("CLAY"));
        assertNotNull(MorphismStyle.valueOf("NEO"));
        assertNotNull(MorphismStyle.valueOf("LIQUID_GLASS"));
        assertNotNull(MorphismStyle.valueOf("MONOLISM"));
        assertNotNull(MorphismStyle.valueOf("BRUTALISM"));
        assertNotNull(MorphismStyle.valueOf("MINIMALISM"));
    }

    @Test
    public void testDefaultMorphismFallback() {
        assertEquals(MorphismStyle.LIQUID_GLASS, MorphismStyle.fromKey(null));
        assertEquals(MorphismStyle.LIQUID_GLASS, MorphismStyle.fromKey("INVALID_KEY"));
        assertEquals(MorphismStyle.CLAY, MorphismStyle.fromKey("CLAY"));
        assertEquals(MorphismStyle.BRUTALISM, MorphismStyle.fromKey("brutalism"));
    }

    @Test
    public void testThemeModeMapping() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromId(2));
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemeMode.DARK.getNightMode());

        assertEquals(ThemeMode.LIGHT, ThemeMode.fromId(1));
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemeMode.LIGHT.getNightMode());

        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromId(0));
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeMode.SYSTEM.getNightMode());

        // Invalid fallback
        assertEquals(ThemeMode.DARK, ThemeMode.fromId(999));
    }

    @Test
    public void testMorphismStyleTokensProperties() {
        // Clay: soft inflated
        assertEquals(24, MorphismStyle.CLAY.getDefaultCornerRadiusDp());
        assertEquals(6, MorphismStyle.CLAY.getDefaultElevationDp());

        // Neo: dimensional outer/inner
        assertEquals(16, MorphismStyle.NEO.getDefaultCornerRadiusDp());
        assertEquals(4, MorphismStyle.NEO.getDefaultElevationDp());

        // Liquid Glass: Swara default
        assertEquals(20, MorphismStyle.LIQUID_GLASS.getDefaultCornerRadiusDp());
        assertEquals(2, MorphismStyle.LIQUID_GLASS.getDefaultElevationDp());

        // Monolism: solid clean
        assertEquals(12, MorphismStyle.MONOLISM.getDefaultCornerRadiusDp());

        // Brutalism: bold borders, hard edges
        assertEquals(2, MorphismStyle.BRUTALISM.getDefaultCornerRadiusDp());
        assertEquals(3, MorphismStyle.BRUTALISM.getDefaultStrokeWidthDp());
        assertEquals(0, MorphismStyle.BRUTALISM.getDefaultElevationDp());

        // Minimalism: flat clean
        assertEquals(8, MorphismStyle.MINIMALISM.getDefaultCornerRadiusDp());
        assertEquals(0, MorphismStyle.MINIMALISM.getDefaultElevationDp());
    }
}
