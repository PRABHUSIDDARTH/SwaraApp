package com.psthetech.swara.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Overflow-menu entries for an artist page: one "Unmerge" per merged credit, then "Merge with...". */
public final class ArtistMergeMenuPolicy {

    public enum Kind { UNMERGE, MERGE_WITH }

    public static final class Item {
        public final Kind kind;
        @Nullable public final String creditKey;
        @Nullable public final String creditDisplay;

        Item(Kind kind, @Nullable String creditKey, @Nullable String creditDisplay) {
            this.kind = kind;
            this.creditKey = creditKey;
            this.creditDisplay = creditDisplay;
        }
    }

    private ArtistMergeMenuPolicy() {}

    @NonNull
    public static List<Item> items(@NonNull String mergedKey, @NonNull ArtistAliasResolver r) {
        List<Item> out = new ArrayList<>();
        for (String k : r.keysMergedInto(mergedKey)) {
            out.add(new Item(Kind.UNMERGE, k, r.creditDisplay(k)));
        }
        out.add(new Item(Kind.MERGE_WITH, null, null));
        return out;
    }
}
