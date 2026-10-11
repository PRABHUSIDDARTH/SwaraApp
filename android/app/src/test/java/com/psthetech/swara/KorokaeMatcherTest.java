package com.psthetech.swara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.util.KorokaeMatcher;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Comprehensive test suite verifying KorokaeMatcher deterministic matching,
 * indicator recognition, false positive rejection, artist validation, and scoring.
 */
public class KorokaeMatcherTest {

    private Song createSong(long id, String title, String artist, String album, long duration) {
        return new Song(id, title, artist, album, 1L, duration, 1, 2024, 1000L);
    }

    // 1. Exact karaoke title match
    @Test
    public void testExactKaraokeTitleMatch() {
        Song target = createSong(1, "Perfect", "Ed Sheeran", "Divide", 260_000);
        Song candidate = createSong(2, "Perfect Karaoke", "Ed Sheeran", "Divide", 260_000);

        Song match = KorokaeMatcher.findBestMatch(target, Collections.singletonList(candidate));
        assertNotNull(match);
        assertEquals(2, match.getId());
    }

    // 2. "(Karaoke)" suffix
    @Test
    public void testParenthesesKaraokeSuffix() {
        Song target = createSong(1, "Shallow", "Lady Gaga", "A Star Is Born", 215_000);
        Song candidate = createSong(2, "Shallow (Karaoke)", "Lady Gaga", "A Star Is Born", 215_000);

        Song match = KorokaeMatcher.findBestMatch(target, Collections.singletonList(candidate));
        assertNotNull(match);
        assertEquals(2, match.getId());
    }

    // 3. "- Instrumental" suffix
    @Test
    public void testHyphenInstrumentalSuffix() {
        Song target = createSong(1, "Blinding Lights", "The Weeknd", "After Hours", 200_000);
        Song candidate = createSong(2, "Blinding Lights - Instrumental", "The Weeknd", "After Hours", 200_000);

        Song match = KorokaeMatcher.findBestMatch(target, Collections.singletonList(candidate));
        assertNotNull(match);
        assertEquals(2, match.getId());
    }

    // 4. "[Karaoke Version]"
    @Test
    public void testBracketKaraokeVersion() {
        Song target = createSong(1, "Hotel California", "Eagles", "Hotel California", 390_000);
        Song candidate = createSong(2, "Hotel California [Karaoke Version]", "Eagles", "Hotel California", 390_000);

        Song match = KorokaeMatcher.findBestMatch(target, Collections.singletonList(candidate));
        assertNotNull(match);
        assertEquals(2, match.getId());
    }

    // 5. "Music Only"
    @Test
    public void testMusicOnlyIndicator() {
        Song target = createSong(1, "Fix You", "Coldplay", "X&Y", 295_000);
        Song candidate = createSong(2, "Fix You (Music Only)", "Coldplay", "X&Y", 295_000);

        Song match = KorokaeMatcher.findBestMatch(target, Collections.singletonList(candidate));
        assertNotNull(match);
        assertEquals(2, match.getId());
    }

    // 6. "Minus One"
    @Test
    public void testMinusOneIndicator() {
        Song target = createSong(1, "Anbil Avan", "A.R. Rahman", "Vinnaithaandi Varuvaayaa", 240_000);
        Song candidate = createSong(2, "Anbil Avan - Minus One", "A.R. Rahman", "Vinnaithaandi Varuvaayaa", 240_000);

        Song match = KorokaeMatcher.findBestMatch(target, Collections.singletonList(candidate));
        assertNotNull(match);
        assertEquals(2, match.getId());
    }

    // 7. Case differences
    @Test
    public void testCaseDifferences() {
        Song target = createSong(1, "believer", "imagine dragons", "evolve", 204_000);
        Song candidate = createSong(2, "BELIEVER (KARAOKE VERSION)", "IMAGINE DRAGONS", "EVOLVE", 204_000);

        Song match = KorokaeMatcher.findBestMatch(target, Collections.singletonList(candidate));
        assertNotNull(match);
        assertEquals(2, match.getId());
    }

    // 8. Whitespace differences
    @Test
    public void testWhitespaceDifferences() {
        Song target = createSong(1, "Rolling  in  the  Deep ", "Adele", "21", 228_000);
        Song candidate = createSong(2, "Rolling in the Deep   (Instrumental) ", "Adele", "21", 228_000);

        Song match = KorokaeMatcher.findBestMatch(target, Collections.singletonList(candidate));
        assertNotNull(match);
        assertEquals(2, match.getId());
    }

    // 9. Punctuation differences
    @Test
    public void testPunctuationDifferences() {
        Song target = createSong(1, "Don't Stop Believin'", "Journey", "Escape", 250_000);
        Song candidate = createSong(2, "Dont Stop Believin (Karaoke)", "Journey", "Escape", 250_000);

        Song match = KorokaeMatcher.findBestMatch(target, Collections.singletonList(candidate));
        assertNotNull(match);
        assertEquals(2, match.getId());
    }

    // 10. False positive: live version
    @Test
    public void testFalsePositiveLiveVersion() {
        Song target = createSong(1, "Comfortably Numb", "Pink Floyd", "The Wall", 380_000);
        Song liveCandidate = createSong(2, "Comfortably Numb - Live", "Pink Floyd", "Pulse", 380_000);

        Song match = KorokaeMatcher.findBestMatch(target, Collections.singletonList(liveCandidate));
        assertNull(match);
    }

    // 11. False positive: acoustic version
    @Test
    public void testFalsePositiveAcousticVersion() {
        Song target = createSong(1, "Layla", "Eric Clapton", "Layla", 420_000);
        Song acousticCandidate = createSong(2, "Layla (Acoustic)", "Eric Clapton", "Unplugged", 280_000);

        Song match = KorokaeMatcher.findBestMatch(target, Collections.singletonList(acousticCandidate));
        assertNull(match);
    }

    // 12. False positive: remix
    @Test
    public void testFalsePositiveRemix() {
        Song target = createSong(1, "Levitating", "Dua Lipa", "Future Nostalgia", 203_000);
        Song remixCandidate = createSong(2, "Levitating (Club Future Nostalgia Remix)", "Dua Lipa", "Future Nostalgia", 220_000);

        Song match = KorokaeMatcher.findBestMatch(target, Collections.singletonList(remixCandidate));
        assertNull(match);
    }

    // 13. No karaoke candidate
    @Test
    public void testNoKaraokeCandidate() {
        Song target = createSong(1, "Yesterday", "The Beatles", "Help!", 125_000);
        List<Song> candidates = Arrays.asList(
                createSong(2, "Hey Jude", "The Beatles", "Hey Jude", 430_000),
                createSong(3, "Let It Be", "The Beatles", "Let It Be", 243_000),
                createSong(4, "Yesterday - Remastered", "The Beatles", "Help!", 125_000)
        );

        Song match = KorokaeMatcher.findBestMatch(target, candidates);
        assertNull(match);
    }

    // 14. Multiple candidates (picks best match by title + duration proximity)
    @Test
    public void testMultipleCandidatesPicksBestMatch() {
        Song target = createSong(1, "Shape of You", "Ed Sheeran", "Divide", 233_000);
        Song distantCandidate = createSong(2, "Shape of You (Instrumental)", "Ed Sheeran", "Divide", 320_000); // 87s diff
        Song closeCandidate = createSong(3, "Shape of You (Karaoke Version)", "Ed Sheeran", "Divide", 234_000); // 1s diff

        Song match = KorokaeMatcher.findBestMatch(target, Arrays.asList(distantCandidate, closeCandidate));
        assertNotNull(match);
        assertEquals(3, match.getId());
    }

    // 15. Artist mismatch
    @Test
    public void testArtistMismatchRejection() {
        Song target = createSong(1, "Hello", "Adele", "25", 295_000);
        Song candidate = createSong(2, "Hello (Karaoke)", "Lionel Richie", "Can't Slow Down", 250_000);

        Song match = KorokaeMatcher.findBestMatch(target, Collections.singletonList(candidate));
        assertNull(match);
    }

    // 16. Asian / Multi-lingual support (伴奏)
    @Test
    public void testAsianIndicatorBanZou() {
        Song target = createSong(1, "晴天", "周杰伦", "叶惠美", 269_000);
        Song candidate = createSong(2, "晴天 (伴奏)", "周杰伦", "叶惠美", 269_000);

        Song match = KorokaeMatcher.findBestMatch(target, Collections.singletonList(candidate));
        assertNotNull(match);
        assertEquals(2, match.getId());
    }
}
