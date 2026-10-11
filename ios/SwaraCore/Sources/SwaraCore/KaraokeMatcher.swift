import Foundation

/// Finds a local karaoke / instrumental version of a song. Mirrors Android `KorokaeMatcher`.
public enum KaraokeMatcher {
    static let indicators = ["karaoke version", "instrumental version", "karaoke", "instrumental",
                             "minus one", "backing track", "music only", "伴奏"]
    static let falsePositives = ["live", "acoustic", "remix", "mix", "cover", "radio edit",
                                 "extended", "demo", "tribute", "unplugged", "acapella",
                                 "a cappella", "slowed", "reverb"]
    static let genericArtists = ["karaoke", "the karaoke channel", "karaoke version", "instrumental",
                                 "soundtrack", "backing track", "backing tracks", "unknown artist",
                                 "unknown", "various artists"]
    // Java's \p{Punct} is ASCII punctuation only.
    private static let punct = Rx(#"[!-/:-@\[-`{-~]"#)
    private static let multiSpace = Rx(#"\s+"#)

    /// Best candidate with score >= 50, or nil.
    public static func findBestMatch(_ target: Song?, in candidates: [Song]) -> Song? {
        guard let target else { return nil }
        var best: Song?
        var high = -1
        for c in candidates where c.id != target.id {
            let s = score(target, c)
            if s > high && s >= 50 { high = s; best = c }
        }
        return best
    }

    /// Confidence 0...200, or -1 when rejected.
    public static func score(_ target: Song, _ candidate: Song) -> Int {
        let nt = normalize(target.title), nc = normalize(candidate.title)
        guard hasIndicator(nc) || hasIndicator(normalize(candidate.album)) else { return -1 }
        if hasFalsePositive(nc, nt) { return -1 }
        guard isArtistCompatible(target, candidate) else { return -1 }

        let bt = baseTitle(nt), bc = baseTitle(nc)
        guard !bt.isEmpty, !bc.isEmpty else { return -1 }
        var score: Int
        if bt == bc { score = 100 }
        else if bc.hasPrefix(bt) || bt.hasPrefix(bc) { score = 70 }
        else { return -1 }

        let ta = normalize(target.artist), ca = normalize(candidate.artist)
        if !ta.isEmpty && ta == ca { score += 40 } else if isGeneric(ca) { score += 20 }
        let tal = normalize(target.album)
        if !tal.isEmpty && tal == normalize(candidate.album) { score += 20 }

        if target.duration > 0 && candidate.duration > 0 {
            let d = abs(target.duration - candidate.duration)
            if d <= 5_000 { score += 30 }
            else if d <= 15_000 { score += 20 }
            else if d <= 30_000 { score += 10 }
            else if d > 60_000 { score -= 30 }
        }
        return score
    }

    public static func hasIndicator(_ text: String) -> Bool {
        !text.isEmpty && indicators.contains { text.contains($0) }
    }

    static func hasFalsePositive(_ cand: String, _ target: String) -> Bool {
        falsePositives.contains { containsWord(cand, $0) && !containsWord(target, $0) }
    }

    static func isArtistCompatible(_ t: Song, _ c: Song) -> Bool {
        let ta = normalize(t.artist), ca = normalize(c.artist)
        if ta.isEmpty || ca.isEmpty || ta == ca || isGeneric(ca) { return true }
        if ca.contains(ta) || ta.contains(ca) { return true }
        let tal = normalize(t.album)
        return !tal.isEmpty && tal == normalize(c.album)
    }

    static func isGeneric(_ a: String) -> Bool { genericArtists.contains { a == $0 || a.contains($0) } }

    public static func baseTitle(_ normalized: String) -> String {
        var b = normalized
        for i in indicators { b = b.replacingOccurrences(of: i, with: "") }
        b = punct.replaceAll(b, with: " ")
        return multiSpace.replaceAll(b, with: " ").trimmed
    }

    /// NFKD, strip diacritics, lowercase, drop quotes, brackets/dashes -> spaces.
    public static func normalize(_ input: String?) -> String {
        guard let input else { return "" }
        var s = input.decomposedStringWithCompatibilityMapping
        s = String(String.UnicodeScalarView(s.unicodeScalars.filter {
            !$0.properties.generalCategory.isMark
        }))
        s = s.lowercased()
        for q in ["'", "`", "\"", "\u{2019}", "\u{2018}"] { s = s.replacingOccurrences(of: q, with: "") }
        for ch in ["(", ")", "[", "]", "{", "}", "-", "_", "/", "\\"] {
            s = s.replacingOccurrences(of: ch, with: " ")
        }
        return multiSpace.replaceAll(s, with: " ").trimmed
    }

    private static func containsWord(_ text: String, _ word: String) -> Bool {
        " \(text) ".contains(" \(word) ")
    }
}

private extension Unicode.GeneralCategory {
    var isMark: Bool {
        self == .nonspacingMark || self == .spacingMark || self == .enclosingMark
    }
}

/// "Meaningful play" rule from Android `PlayHistoryRepository`:
/// position >= min(30 s, 40% of duration).
public enum PlayHistoryRule {
    public static func threshold(durationMs: Int64) -> Int64 {
        min(30_000, Int64(Double(durationMs) * 0.4))
    }

    public static func qualifies(positionMs: Int64, durationMs: Int64) -> Bool {
        positionMs >= threshold(durationMs: durationMs)
    }

    /// Same song is not recorded twice within this window.
    public static let debounceMs: Int64 = 30_000
    /// History table cap.
    public static let maxEntries = 200
}
