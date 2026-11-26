package com.psthetech.swara.data.preference;

import android.content.Context;

import androidx.annotation.NonNull;

/** Which look Now Playing uses. Independent of the colour themes. */
public final class PlayerSkinPreferences {

    private static final String PREFS = "swara_player_skin";
    private static final String KEY_DECK = "deck";

    private PlayerSkinPreferences() { }

    /** The Deck player skin. Off by default. */
    public static boolean deckEnabled(@NonNull Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DECK, false);
    }

    public static void setDeckEnabled(@NonNull Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_DECK, enabled).apply();
    }
}
