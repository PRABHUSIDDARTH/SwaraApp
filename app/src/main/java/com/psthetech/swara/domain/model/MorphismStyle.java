package com.psthetech.swara.domain.model;

import com.psthetech.swara.R;

/**
 * UI Morphism & Design Language System options.
 */
public enum MorphismStyle {
    CLAY(
        "CLAY",
        R.string.morphism_clay_title,
        R.string.morphism_clay_desc,
        24, // corner radius dp
        6,  // elevation dp
        0   // stroke width dp
    ),
    NEO(
        "NEO",
        R.string.morphism_neo_title,
        R.string.morphism_neo_desc,
        16,
        4,
        1
    ),
    LIQUID_GLASS(
        "LIQUID_GLASS",
        R.string.morphism_liquid_glass_title,
        R.string.morphism_liquid_glass_desc,
        20,
        2,
        1
    ),
    MONOLISM(
        "MONOLISM",
        R.string.morphism_monolism_title,
        R.string.morphism_monolism_desc,
        12,
        1,
        1
    ),
    BRUTALISM(
        "BRUTALISM",
        R.string.morphism_brutalism_title,
        R.string.morphism_brutalism_desc,
        2,  // hard edges
        0,
        3   // bold border
    ),
    MINIMALISM(
        "MINIMALISM",
        R.string.morphism_minimalism_title,
        R.string.morphism_minimalism_desc,
        8,
        0,
        1
    );

    private final String key;
    private final int titleResId;
    private final int descResId;
    private final int defaultCornerRadiusDp;
    private final int defaultElevationDp;
    private final int defaultStrokeWidthDp;

    MorphismStyle(
        String key,
        int titleResId,
        int descResId,
        int defaultCornerRadiusDp,
        int defaultElevationDp,
        int defaultStrokeWidthDp
    ) {
        this.key = key;
        this.titleResId = titleResId;
        this.descResId = descResId;
        this.defaultCornerRadiusDp = defaultCornerRadiusDp;
        this.defaultElevationDp = defaultElevationDp;
        this.defaultStrokeWidthDp = defaultStrokeWidthDp;
    }

    public String getKey() {
        return key;
    }

    public int getTitleResId() {
        return titleResId;
    }

    public int getDescResId() {
        return descResId;
    }

    public int getDefaultCornerRadiusDp() {
        return defaultCornerRadiusDp;
    }

    public int getDefaultElevationDp() {
        return defaultElevationDp;
    }

    public int getDefaultStrokeWidthDp() {
        return defaultStrokeWidthDp;
    }

    public static MorphismStyle fromKey(String key) {
        if (key == null) return LIQUID_GLASS;
        for (MorphismStyle style : values()) {
            if (style.key.equalsIgnoreCase(key) || style.name().equalsIgnoreCase(key)) {
                return style;
            }
        }
        return LIQUID_GLASS; // Default Swara style
    }
}
