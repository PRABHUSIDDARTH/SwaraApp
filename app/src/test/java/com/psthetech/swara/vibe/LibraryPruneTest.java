package com.psthetech.swara.vibe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class LibraryPruneTest {

    @Test
    public void staleIds_areStoredMinusLibrary_distinctSorted() {
        List<Long> stored = Arrays.asList(5L, 1L, 9L, 5L, 3L);
        assertEquals(Arrays.asList(5L, 9L),
                LibraryPrune.staleIds(stored, new HashSet<>(Arrays.asList(1L, 3L, 4L))));
    }

    @Test
    public void staleIds_emptyOrNullLibrary_prunesNothing() {
        List<Long> stored = Arrays.asList(1L, 2L);
        assertTrue(LibraryPrune.staleIds(stored, new HashSet<>()).isEmpty());
        assertTrue(LibraryPrune.staleIds(stored, null).isEmpty());
        assertTrue(LibraryPrune.staleIds(null, new HashSet<>(Collections.singletonList(1L)))
                .isEmpty());
    }

    @Test
    public void chunks_respectLimit() {
        List<Long> ids = new ArrayList<>();
        for (long i = 0; i < 1201; i++) ids.add(i);
        List<List<Long>> c = LibraryPrune.chunks(ids, LibraryPrune.CHUNK);
        assertEquals(3, c.size());
        assertEquals(500, c.get(0).size());
        assertEquals(500, c.get(1).size());
        assertEquals(201, c.get(2).size());
        assertEquals(Long.valueOf(1200), c.get(2).get(200));
        assertTrue(LibraryPrune.chunks(new ArrayList<>(), 500).isEmpty());
    }
}
