import XCTest
@testable import SwaraCore

final class TimeFormatterTests: XCTestCase {
    func testFormats() {
        XCTAssertEqual(TimeFormatter.formatMs(0), "0:00")
        XCTAssertEqual(TimeFormatter.formatMs(5_000), "0:05")
        XCTAssertEqual(TimeFormatter.formatMs(222_000), "3:42")
        XCTAssertEqual(TimeFormatter.formatMs(3_905_000), "1:05:05")
        XCTAssertEqual(TimeFormatter.formatMs(-10), "0:00")
        XCTAssertEqual(TimeFormatter.formatAccessible(222_000), "3 minutes 42 seconds")
        XCTAssertEqual(TimeFormatter.formatAccessible(60_000), "1 minute")
        XCTAssertEqual(TimeFormatter.formatAccessible(9_000), "9 seconds")
    }
}

final class ShuffleEngineTests: XCTestCase {
    func testShuffleKeepsEverySongExactlyOnce() {
        let songs = (1...50).map { Fixtures.song(Int64($0), "S\($0)", "A") }
        let out = ShuffleEngine.shuffleSongs(songs + [songs[0], songs[3]], previous: nil)
        XCTAssertEqual(out.count, 50)
        XCTAssertEqual(Set(out.map(\.id)), Set(songs.map(\.id)))
    }

    func testAvoidsRepeatingPreviousOrder() {
        // Bounded retries (as on Android): a 10-element list practically never repeats.
        let src = Array(1...10)
        for _ in 0..<50 {
            let out = ShuffleEngine.shuffleAvoidRepeat(src, previous: src)
            XCTAssertNotEqual(out, src)
            XCTAssertEqual(out.sorted(), src)
        }
        XCTAssertEqual(ShuffleEngine.shuffleAvoidRepeat([7], previous: [7]), [7])
        XCTAssertEqual(ShuffleEngine.shuffleAvoidRepeat([Int](), previous: nil), [])
    }
}

final class SortOrderTests: XCTestCase {
    func testFromKeyFallsBack() {
        XCTAssertEqual(SortOrder.fromKey("DATE_ADDED"), .dateAdded)
        XCTAssertEqual(SortOrder.fromKey("bogus"), .titleAsc)
        XCTAssertEqual(SortOrder.fromKey(nil), .titleAsc)
    }

    func testCustomOrderRoundTripAndUnknownsNewestFirst() {
        let a = Fixtures.song(1, "a", "x", dateAdded: 10)
        let b = Fixtures.song(2, "b", "x", dateAdded: 20)
        let c = Fixtures.song(3, "c", "x", dateAdded: 30)
        let d = Fixtures.song(4, "d", "x", dateAdded: 40)
        let ids = CustomSongOrder.decode(CustomSongOrder.encode([c, a]) + ",junk")
        XCTAssertEqual(ids, [3, 1])
        XCTAssertEqual(CustomSongOrder.apply([a, b, c, d], order: ids).map(\.id), [3, 1, 4, 2])
    }

    func testTitleSortIsCaseInsensitive() {
        let s = [Fixtures.song(1, "beta", "x"), Fixtures.song(2, "Alpha", "x")]
        XCTAssertEqual(SortOrder.titleAsc.apply(s).map(\.id), [2, 1])
        XCTAssertEqual(SortOrder.titleDesc.apply(s).map(\.id), [1, 2])
    }
}

final class ArtistIdentityTests: XCTestCase {
    func testCanonicalKey() {
        XCTAssertEqual(ArtistIdentity.canonicalKey(nil), "unknown artist")
        XCTAssertEqual(ArtistIdentity.canonicalKey("   "), "unknown artist")
        XCTAssertEqual(ArtistIdentity.canonicalKey("<UNKNOWN>"), "unknown artist")
        XCTAssertEqual(ArtistIdentity.canonicalKey("ANIRUDH RAVICHANDER"), "anirudh ravichander")
        XCTAssertEqual(ArtistIdentity.canonicalKey("  A.R. Rahman "), "a.r. rahman")
        XCTAssertEqual(ArtistIdentity.canonicalKey("A. R. Rahman"), "a.r. rahman")
        XCTAssertEqual(ArtistIdentity.canonicalKey("A.R.Rahman"), "a.r. rahman")
    }

    func testDisplayName() {
        XCTAssertEqual(ArtistIdentity.normalizeDisplayName(nil), "Unknown Artist")
        XCTAssertEqual(ArtistIdentity.normalizeDisplayName("<unknown>"), "Unknown Artist")
        XCTAssertEqual(ArtistIdentity.normalizeDisplayName("  A.R. Rahman  "), "A.R. Rahman")
        XCTAssertEqual(ArtistIdentity.normalizeDisplayName("A. R. Rahman"), "A.R. Rahman")
    }

    func testExtractArtists() {
        XCTAssertEqual(ArtistIdentity.extractArtists("A.R. Rahman, Shreya Ghoshal"), ["A.R. Rahman", "Shreya Ghoshal"])
        XCTAssertEqual(ArtistIdentity.extractArtists("A. R. Rahman,Shreya Ghoshal"), ["A.R. Rahman", "Shreya Ghoshal"])
        XCTAssertEqual(ArtistIdentity.extractArtists("Eminem feat. Rihanna"), ["Eminem", "Rihanna"])
        XCTAssertEqual(ArtistIdentity.extractArtists("Daft Punk featuring Pharrell Williams"), ["Daft Punk", "Pharrell Williams"])
        XCTAssertEqual(ArtistIdentity.extractArtists("Song Artist (feat. Guest)"), ["Song Artist", "Guest"])
        XCTAssertEqual(ArtistIdentity.extractArtists("Simon & Garfunkel"), ["Simon", "Garfunkel"])
        XCTAssertEqual(ArtistIdentity.extractArtists("AC/DC"), ["AC/DC"])
        XCTAssertEqual(ArtistIdentity.extractArtists("Anirudh - MassTamilan.com"), ["Anirudh"])
        XCTAssertEqual(ArtistIdentity.extractArtists(nil), ["Unknown Artist"])
        XCTAssertTrue(ArtistIdentity.containsArtist("A.R. Rahman, Shreya Ghoshal", "shreya ghoshal"))
        XCTAssertFalse(ArtistIdentity.containsArtist("A.R. Rahman, Shreya Ghoshal", "Anirudh"))
    }

    /// Mirrors ArtistDeduplicationTest.testBuildArtistsDeduplication.
    func testBuildArtistsDeduplication() {
        let songs = [
            Fixtures.song(1, "Naa Ready", "Anirudh", "Leo", albumId: 10),
            Fixtures.song(2, "Badass", "anirudh", "Leo", albumId: 10),
            Fixtures.song(3, "Hukum", " Anirudh  ", "Jailer", albumId: 20),
            Fixtures.song(4, "Vaseegara", "Harris Jayaraj", "Minnale", albumId: 30),
            Fixtures.song(5, "Track 5", "<unknown>", "Unknown Album", albumId: 40),
            Fixtures.song(6, "Track 6", nil, "Unknown Album", albumId: 40),
        ]
        let artists = ArtistIdentity.buildArtists(songs)
        XCTAssertEqual(artists.count, 3)
        let anirudh = artists.first { $0.canonicalKey == "anirudh" }
        XCTAssertEqual(anirudh?.name, "Anirudh")
        XCTAssertEqual(anirudh?.songCount, 3)
        XCTAssertEqual(anirudh?.albumCount, 2)
        XCTAssertEqual(artists.first { $0.canonicalKey == "unknown artist" }?.songCount, 2)
        XCTAssertTrue(ArtistIdentity.buildArtists([]).isEmpty)
    }
}

/// Mirrors KorokaeMatcherTest.
final class KaraokeMatcherTests: XCTestCase {
    private func s(_ id: Int64, _ t: String, _ a: String, _ al: String, _ d: Int64) -> Song {
        Fixtures.song(id, t, a, al, duration: d)
    }

    func testPositiveMatches() {
        let pairs: [(Song, Song)] = [
            (s(1, "Perfect", "Ed Sheeran", "Divide", 260_000), s(2, "Perfect Karaoke", "Ed Sheeran", "Divide", 260_000)),
            (s(1, "Shallow", "Lady Gaga", "A Star Is Born", 215_000), s(2, "Shallow (Karaoke)", "Lady Gaga", "A Star Is Born", 215_000)),
            (s(1, "Blinding Lights", "The Weeknd", "After Hours", 200_000), s(2, "Blinding Lights - Instrumental", "The Weeknd", "After Hours", 200_000)),
            (s(1, "Hotel California", "Eagles", "Hotel California", 390_000), s(2, "Hotel California [Karaoke Version]", "Eagles", "Hotel California", 390_000)),
            (s(1, "Fix You", "Coldplay", "X&Y", 295_000), s(2, "Fix You (Music Only)", "Coldplay", "X&Y", 295_000)),
            (s(1, "Anbil Avan", "A.R. Rahman", "VTV", 240_000), s(2, "Anbil Avan - Minus One", "A.R. Rahman", "VTV", 240_000)),
            (s(1, "believer", "imagine dragons", "evolve", 204_000), s(2, "BELIEVER (KARAOKE VERSION)", "IMAGINE DRAGONS", "EVOLVE", 204_000)),
            (s(1, "Rolling  in  the  Deep ", "Adele", "21", 228_000), s(2, "Rolling in the Deep   (Instrumental) ", "Adele", "21", 228_000)),
            (s(1, "Don't Stop Believin'", "Journey", "Escape", 250_000), s(2, "Dont Stop Believin (Karaoke)", "Journey", "Escape", 250_000)),
            (s(1, "晴天", "周杰伦", "叶惠美", 269_000), s(2, "晴天 (伴奏)", "周杰伦", "叶惠美", 269_000)),
        ]
        for (t, c) in pairs {
            XCTAssertEqual(KaraokeMatcher.findBestMatch(t, in: [c])?.id, 2, "\(t.title) vs \(c.title)")
        }
    }

    func testRejections() {
        XCTAssertNil(KaraokeMatcher.findBestMatch(s(1, "Comfortably Numb", "Pink Floyd", "The Wall", 380_000),
                                                  in: [s(2, "Comfortably Numb - Live", "Pink Floyd", "Pulse", 380_000)]))
        XCTAssertNil(KaraokeMatcher.findBestMatch(s(1, "Layla", "Eric Clapton", "Layla", 420_000),
                                                  in: [s(2, "Layla (Acoustic)", "Eric Clapton", "Unplugged", 280_000)]))
        XCTAssertNil(KaraokeMatcher.findBestMatch(s(1, "Hello", "Adele", "25", 295_000),
                                                  in: [s(2, "Hello (Karaoke)", "Lionel Richie", "Can't Slow Down", 250_000)]))
        XCTAssertNil(KaraokeMatcher.findBestMatch(s(1, "Yesterday", "The Beatles", "Help!", 125_000),
                                                  in: [s(4, "Yesterday - Remastered", "The Beatles", "Help!", 125_000)]))
    }

    func testPrefersCloserDuration() {
        let t = s(1, "Shape of You", "Ed Sheeran", "Divide", 233_000)
        let far = s(2, "Shape of You (Instrumental)", "Ed Sheeran", "Divide", 320_000)
        let close = s(3, "Shape of You (Karaoke Version)", "Ed Sheeran", "Divide", 234_000)
        XCTAssertEqual(KaraokeMatcher.findBestMatch(t, in: [far, close])?.id, 3)
    }
}

final class ThemeAndRulesTests: XCTestCase {
    func testPlayHistoryThreshold() {
        XCTAssertEqual(PlayHistoryRule.threshold(durationMs: 200_000), 30_000)
        XCTAssertEqual(PlayHistoryRule.threshold(durationMs: 50_000), 20_000)
        XCTAssertFalse(PlayHistoryRule.qualifies(positionMs: 19_999, durationMs: 50_000))
        XCTAssertTrue(PlayHistoryRule.qualifies(positionMs: 20_000, durationMs: 50_000))
    }

    /// Light-mode accents are tuned for >= 4.5:1 on white (Android DesignTokens contract).
    func testLightAccentsMeetContrastOnWhite() {
        for theme in ColorTheme.allCases {
            let ratio = RGBA.contrast(theme.accent(dark: false), RGBA(hex: 0xFFFFFF))
            XCTAssertGreaterThanOrEqual(ratio, 4.5, theme.rawValue)
        }
    }

    func testPrimaryTextIsAAAOnBackgrounds() {
        for dark in [false, true] {
            let t = DesignTokens(theme: .swara, dark: dark)
            XCTAssertGreaterThanOrEqual(RGBA.contrast(t.textPrimary, t.background), 7.0)
            XCTAssertGreaterThanOrEqual(RGBA.contrast(t.textPrimary, t.surface), 7.0)
        }
    }

    func testThemeKeysAndRepeatCycle() {
        XCTAssertEqual(ColorTheme.fromKey("off_white"), .offWhite)
        XCTAssertEqual(ColorTheme.fromKey("nope"), .swara)
        XCTAssertEqual(RepeatMode.off.next, .one)
        XCTAssertEqual(RepeatMode.one.next, .all)
        XCTAssertEqual(RepeatMode.all.next, .off)
    }

    func testStableIDIsDeterministic() {
        XCTAssertEqual(StableID.of("Music/a.mp3"), StableID.of("Music/a.mp3"))
        XCTAssertNotEqual(StableID.of("Music/a.mp3"), StableID.of("Music/b.mp3"))
        XCTAssertGreaterThanOrEqual(StableID.of("x"), 0)
    }
}
