package com.psthetech.swara.ui.viewmodel;

/**
 * Sort orders available for the Songs library list.
 */
public enum SortOrder {
    TITLE_ASC,   // A → Z by title (default)
    TITLE_DESC,  // Z → A by title
    ARTIST_ASC,  // A → Z by artist
    DATE_ADDED,  // Newest added first
    DURATION_ASC // Shortest first
}
