package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.util.ArtistAliasResolver;
import com.psthetech.swara.util.ArtistIdentityHelper;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArtistAliasResolverTest {

    private long nextId = 1;

    private Song song(String artist, long albumId) {
        return new Song(nextId++, "T" + nextId, artist, "Album" + albumId, albumId, 1000L, 1, 2020, 0L);
    }

    private static String key(String name) {
        return ArtistIdentityHelper.getCanonicalKey(name);
    }

    private ArtistAliasResolver build(Map<String, String> overrides, Song... songs) {
        List<Song> list = new ArrayList<>();
        Collections.addAll(list, songs);
        return ArtistAliasResolver.build(list, overrides);
    }

    @Test
    public void anirudhMergesIntoAnirudhRavichander() {
        ArtistAliasResolver r = build(null,
                song("Anirudh", 10), song("Anirudh Ravichander", 10), song("Anirudh Ravichander", 11));
        assertEquals(key("Anirudh Ravichander"), r.resolve(key("Anirudh")));
        assertEquals("Anirudh Ravichander", r.displayName(r.resolve(key("Anirudh"))));
        assertEquals(key("Anirudh Ravichander"), r.resolve(key("Anirudh Ravichander")));
    }

    @Test
    public void noSharedAlbumNoMerge() {
        ArtistAliasResolver r = build(null,
                song("Anirudh", 10), song("Anirudh Ravichander", 11));
        assertEquals(key("Anirudh"), r.resolve(key("Anirudh")));
        assertTrue(r.autoMerges().isEmpty());
    }

    @Test
    public void ambiguousPrefixNoMerge() {
        ArtistAliasResolver r = build(null,
                song("Karthik", 10), song("Karthik Netha", 10), song("Karthik Raja", 10));
        assertEquals(key("Karthik"), r.resolve(key("Karthik")));
    }

    @Test
    public void initialsAware() {
        ArtistAliasResolver r = build(null,
                song("A.R.", 10), song("A.R. Rahman", 10), song("AR Rahman", 10),
                song("A. R. Rahman", 10));
        assertEquals(key("A.R."), r.resolve(key("A.R.")));
        String root = r.resolve(key("A.R. Rahman"));
        assertEquals(root, r.resolve(key("AR Rahman")));
        assertEquals(root, r.resolve(key("A. R. Rahman")));
        assertEquals(ArtistAliasResolver.tokensKey("A.R. Rahman"), ArtistAliasResolver.tokensKey("AR Rahman"));
        assertEquals(ArtistAliasResolver.tokensKey("A.R. Rahman"), ArtistAliasResolver.tokensKey("A. R. Rahman"));
        assertEquals("anirudh ravichander", ArtistAliasResolver.tokensKey("Anirudh  Ravichander"));
    }

    @Test
    public void chainsResolveToLongest() {
        ArtistAliasResolver r = build(null,
                song("Sid", 10), song("Sid Sriram", 10), song("Sid Sriram", 11),
                song("Sid Sriram Live", 11));
        assertEquals(key("Sid Sriram Live"), r.resolve(key("Sid")));
        assertEquals(key("Sid Sriram Live"), r.resolve(key("Sid Sriram")));
        assertEquals("Sid Sriram Live", r.displayName(r.resolve(key("Sid"))));
    }

    @Test
    public void mergeOverrideWins() {
        Map<String, String> o = new HashMap<>();
        o.put("karthik", key("Karthik Raja"));
        ArtistAliasResolver r = build(o,
                song("Karthik", 10), song("Karthik Netha", 10), song("Karthik Raja", 10));
        assertEquals(key("Karthik Raja"), r.resolve("karthik"));
    }

    @Test
    public void neverMergeOverrideWins() {
        Map<String, String> o = new HashMap<>();
        o.put(key("Anirudh"), null);
        ArtistAliasResolver r = build(o,
                song("Anirudh", 10), song("Anirudh Ravichander", 10));
        assertEquals(key("Anirudh"), r.resolve(key("Anirudh")));
    }

    @Test
    public void multiArtistCreditCountsForEach() {
        ArtistAliasResolver r = build(null,
                song("Anirudh, Arivu", 10), song("Anirudh Ravichander", 10));
        assertEquals(key("Anirudh Ravichander"), r.resolve(key("Anirudh")));
    }

    @Test
    public void cycleOverridesAreSafe() {
        Map<String, String> o = new HashMap<>();
        o.put("a", "b");
        o.put("b", "a");
        ArtistAliasResolver r = build(o, song("A", 1));
        assertEquals("a", r.resolve("a"));
    }

    @Test
    public void identityResolvesToSelf() {
        ArtistAliasResolver r = ArtistAliasResolver.identity();
        assertEquals("x", r.resolve("x"));
        assertTrue(r.autoMerges().isEmpty());
    }
}
