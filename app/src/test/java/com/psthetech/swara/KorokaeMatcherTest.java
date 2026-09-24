package com.psthetech.swara;

import static org.junit.Assert.*;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.util.KorokaeMatcher;
import org.junit.Test;
import java.util.Collections;

public class KorokaeMatcherTest {

    private Song createSong(long id, String title, String artist, String album, long duration) {
        return new Song(id, title, artist, album, 1L, duration, 1, 2024, 1000L);
    }

    @Test
    public void testExactKaraokeTitleMatch() {
        Song target = createSong(1, "Perfect", "Ed Sheeran", "Divide", 260_000);
        Song candidate = createSong(2, "Perfect Karaoke", "Ed Sheeran", "Divide", 260_000);
        Song match = KorokaeMatcher.findBestMatch(target, Collections.singletonList(candidate));
        assertNotNull("Should match exact karaoke title", match);
        assertEquals(2L, match.getId());
    }

    @Test
    public void testParenthesesKaraokeSuffix() {
        Song target = createSong(1, "Rolling in the Deep", "Adele", "21", 228_000);
        Song candidate = createSong(2, "Rolling in the Deep (Karaoke)", "Adele", "21", 228_000);
        Song match = KorokaeMatcher.findBestMatch(target, Collections.singletonList(candidate));
        assertNotNull("Should match parentheses suffix", match);
        assertEquals(2L, match.getId());
    }

    @Test
    public void testHyphenInstrumentalSuffix() {
        Song target = createSong(1, "Fix You", "Coldplay", "X&Y", 295_000);
        Song candidate = createSong(2, "Fix You - Instrumental", "Coldplay", "X&Y", 295_000);
        Song match = KorokaeMatcher.findBestMatch(target, Collections.singletonList(candidate));
        assertNotNull("Should match hyphen instrumental suffix", match);
        assertEquals(2L, match.getId());
    }
}
