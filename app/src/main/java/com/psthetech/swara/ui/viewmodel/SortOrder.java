package com.psthetech.swara.ui.viewmodel;

/**
 * Sort orders available for the Songs library list.
 */
public enum SortOrder {
    TITLE_ASC,   // A → Z by title (default)
    TITLE_DESC,  // Z → A by title
    ARTIST_ASC,  // A → Z by artist
    DATE_ADDED,  // Newest added first
    DURATION_ASC, // Shortest first
    CUSTOM;       // User's drag-arranged order

    /** Parses a persisted value; unknown or missing values fall back to TITLE_ASC. */
    public static SortOrder fromKey(String key) {
        if (key != null) {
            for (SortOrder o : values()) {
                if (o.name().equals(key)) return o;
            }
        }
        return TITLE_ASC;
    }
}
