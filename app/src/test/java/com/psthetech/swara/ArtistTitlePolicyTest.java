package com.psthetech.swara;

import static org.junit.Assert.assertEquals;

import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.util.ArtistAliasResolver;
import com.psthetech.swara.util.ArtistTitlePolicy;

import org.junit.Test;

import java.util.List;

public class ArtistTitlePolicyTest {
    @Test
    public void aliasNavArgShowsMergedDisplayName() {
        List<Song> songs = ArtistAliasWiringTest.anirudhLibrary();
        ArtistAliasResolver r = ArtistAliasResolver.build(songs, null);
        assertEquals("Anirudh Ravichander", ArtistTitlePolicy.title("Anirudh", r));
        assertEquals("Anirudh Ravichander", ArtistTitlePolicy.title("anirudh", r));
        assertEquals("Anirudh Ravichander", ArtistTitlePolicy.title("Anirudh Ravichander", r));
    }

    @Test
    public void identityKeepsNavArg() {
        assertEquals("Anirudh", ArtistTitlePolicy.title("Anirudh", ArtistAliasResolver.identity()));
        assertEquals("Artist", ArtistTitlePolicy.title(null, ArtistAliasResolver.identity()));
    }
}
