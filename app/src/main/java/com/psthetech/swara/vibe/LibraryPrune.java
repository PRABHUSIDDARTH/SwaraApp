package com.psthetech.swara.vibe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Pure logic: finds stored song ids that are no longer in the library. */
public final class LibraryPrune {

    /** Largest IN (...) list passed to one delete; well below SQLite's 999-variable limit. */
    public static final int CHUNK = 500;

    private LibraryPrune() {
    }

    /**
     * Distinct ids from {@code stored} that are not in {@code library}, ascending. Returns an
     * empty list when {@code library} is null or empty, so a failed or permission-less library
     * query never deletes anything.
     */
    public static List<Long> staleIds(Collection<Long> stored, Set<Long> library) {
        List<Long> out = new ArrayList<>();
        if (stored == null || library == null || library.isEmpty()) return out;
        Set<Long> stale = new TreeSet<>();
        for (Long id : stored) if (id != null && !library.contains(id)) stale.add(id);
        out.addAll(stale);
        return out;
    }

    /** Splits {@code ids} into consecutive chunks of at most {@code size}. */
    public static List<List<Long>> chunks(List<Long> ids, int size) {
        List<List<Long>> out = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += size) {
            out.add(new ArrayList<>(ids.subList(i, Math.min(i + size, ids.size()))));
        }
        return out;
    }
}
