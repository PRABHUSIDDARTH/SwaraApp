package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.util.ArtistAliasResolver;
import com.psthetech.swara.util.ArtistIdentityHelper;
import com.psthetech.swara.util.ArtistMergeMenuPolicy;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ArtistMergeMenuPolicyTest {

    @Test
    public void mergedArtistShowsUnmergeForShortCredit() {
        ArtistAliasResolver r = ArtistAliasResolver.build(ArtistAliasWiringTest.anirudhLibrary(), null);
        String key = r.resolve(ArtistIdentityHelper.getCanonicalKey("Anirudh"));
        List<ArtistMergeMenuPolicy.Item> items = ArtistMergeMenuPolicy.items(key, r);
        assertEquals(2, items.size());
        assertEquals(ArtistMergeMenuPolicy.Kind.UNMERGE, items.get(0).kind);
        assertEquals("Anirudh", items.get(0).creditDisplay);
        assertEquals(ArtistIdentityHelper.getCanonicalKey("Anirudh"), items.get(0).creditKey);
        assertEquals(ArtistMergeMenuPolicy.Kind.MERGE_WITH, items.get(1).kind);
    }

    @Test
    public void plainArtistShowsOnlyMergeWith() {
        List<Song> songs = new ArrayList<>();
        songs.add(new Song(1, "S", "Solo Person", "A", 1L, 1000L, 1, 2020, 0L));
        ArtistAliasResolver r = ArtistAliasResolver.build(songs, null);
        List<ArtistMergeMenuPolicy.Item> items =
                ArtistMergeMenuPolicy.items(ArtistIdentityHelper.getCanonicalKey("Solo Person"), r);
        assertEquals(1, items.size());
        assertEquals(ArtistMergeMenuPolicy.Kind.MERGE_WITH, items.get(0).kind);
    }

    @Test
    public void identityResolverStillOffersMergeWith() {
        List<ArtistMergeMenuPolicy.Item> items =
                ArtistMergeMenuPolicy.items("whoever", ArtistAliasResolver.identity());
        assertEquals(1, items.size());
        assertTrue(items.get(0).kind == ArtistMergeMenuPolicy.Kind.MERGE_WITH);
    }
}
