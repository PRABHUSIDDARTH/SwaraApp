package com.psthetech.swara.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Title for an artist page: nav arg -> canonical key -> resolved key -> display name. */
public final class ArtistTitlePolicy {

    private ArtistTitlePolicy() {}

    @NonNull
    public static String title(@Nullable String navArg, @NonNull ArtistAliasResolver resolver) {
        if (navArg == null || navArg.trim().isEmpty()) return "Artist";
        String resolved = resolver.resolve(ArtistIdentityHelper.getCanonicalKey(navArg));
        String display = resolver.displayName(resolved);
        // displayName falls back to the raw key when the resolver does not know it.
        return display.equals(resolved) ? navArg : display;
    }
}
