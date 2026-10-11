import Foundation

/// Artist credit parsing, canonical grouping and deduplication.
/// Mirrors Android `util/ArtistIdentityHelper` so both apps group artists identically.
public enum ArtistIdentity {
    private static let parenFeat = Rx(#"[\(\[]\s*(?:feat\.?|featuring|ft\.?)\s+([^\]\)]+)[\)\]]"#, caseInsensitive: true)
    private static let inlineFeat = Rx(#"\s+(?:featuring|feat\.?|ft\.?)\s+"#, caseInsensitive: true)
    private static let comma = Rx(#"\s*,\s*"#)
    private static let semicolonOrSlash = Rx(#"\s*[;]\s*|\s+[/\\]\s+"#)
    private static let ampersand = Rx(#"\s+&\s+"#)
    private static let junkTags = Rx(#"\s*-\s*(?:masstamilan|massta|sensongs|starmusiq|isaimini|tamiltunes|songspk|www\.[^,]+|[^,]+\.com|[^,]+\.net|[^,]+\.org)[^,]*"#, caseInsensitive: true)

    private static let keyInitialSpace = Rx(#"(?<=\b[a-z])\.\s+(?=[a-z]\b)"#)
    private static let keyInitialWord = Rx(#"(?<=\b[a-z]\.)(?=[a-z]{2,}\b)"#)
    private static let displayInitialSpace = Rx(#"(?<=\b[A-Za-z])\.\s+(?=[A-Za-z]\b)"#)
    private static let displayInitialWord = Rx(#"(?<=\b[A-Za-z]\.)(?=[A-Za-z]{2,}\b)"#)
    private static let multiSpace = Rx(#"\s+"#)
    private static let trailingPunct = Rx(#"[\.\-_,;]+$"#)
    private static let trailingDash = Rx(#"\s*-\s*$"#)

    public static let unknown = "Unknown Artist"

    /// "A.R. Rahman, Shreya Ghoshal" -> ["A.R. Rahman", "Shreya Ghoshal"]
    public static func extractArtists(_ raw: String?) -> [String] {
        guard let raw, !raw.trimmed.isEmpty else { return [unknown] }
        var cleaned = raw.trimmed
        if ["<unknown>", "unknown"].contains(cleaned.lowercased()) { return [unknown] }

        cleaned = junkTags.replaceAll(cleaned, with: "")
        let fromParens = parenFeat.firstGroups(cleaned).map(\.trimmed).filter { !$0.isEmpty }
        cleaned = parenFeat.replaceAll(cleaned, with: "")

        var tokens: [String] = []
        for fp in inlineFeat.split(cleaned) {
            for cp in comma.split(fp) {
                for ap in ampersand.split(cp) {
                    for sp in semicolonOrSlash.split(ap) where !sp.trimmed.isEmpty {
                        tokens.append(sp.trimmed)
                    }
                }
            }
        }
        for p in fromParens {
            tokens += comma.split(p).map(\.trimmed).filter { !$0.isEmpty }
        }

        var seen = Set<String>()
        var result: [String] = []
        for t in tokens {
            let display = normalizeDisplayName(t)
            if display.isEmpty || display.lowercased() == "unknown" { continue }
            if seen.insert(canonicalKey(display)).inserted { result.append(display) }
        }
        return result.isEmpty ? [unknown] : result
    }

    /// "A. R. Rahman", "A.R. Rahman" and "A.R.Rahman" all map to "a.r. rahman".
    public static func canonicalKey(_ name: String?) -> String {
        guard let name else { return "unknown artist" }
        var s = name.trimmed.lowercased()
        if s.isEmpty || s == "<unknown>" { return "unknown artist" }
        s = keyInitialSpace.replaceAll(s, with: ".")
        s = keyInitialWord.replaceAll(s, with: " ")
        s = multiSpace.replaceAll(s, with: " ").trimmed
        s = trailingPunct.replaceAll(s, with: "").trimmed
        return s.isEmpty ? "unknown artist" : s
    }

    /// Fixes initial spacing ("A. R. Rahman" -> "A.R. Rahman") while preserving case.
    public static func normalizeDisplayName(_ raw: String?) -> String {
        guard let raw else { return unknown }
        var s = raw.trimmed
        if s.isEmpty || s.lowercased() == "<unknown>" { return unknown }
        s = displayInitialSpace.replaceAll(s, with: ".")
        s = displayInitialWord.replaceAll(s, with: " ")
        s = trailingDash.replaceAll(s, with: "")
        s = multiSpace.replaceAll(s, with: " ").trimmed
        return s.isEmpty ? unknown : s
    }

    public static func containsArtist(_ credit: String?, _ target: String?) -> Bool {
        guard let credit, let target else { return false }
        let key = canonicalKey(target)
        return extractArtists(credit).contains { canonicalKey($0) == key }
    }

    public static func songs(forArtist artistOrKey: String?, in songs: [Song]) -> [Song] {
        guard let artistOrKey else { return [] }
        let key = canonicalKey(artistOrKey)
        return ShuffleEngine.deduplicate(songs.filter { containsArtist($0.artist, key) })
            .sorted(by: titleLess)
    }

    /// Builds deduplicated artists; a multi-artist song contributes to each artist.
    public static func buildArtists(_ songs: [Song]) -> [Artist] {
        var order: [String] = []
        var bucket: [String: [Song]] = [:]
        var seenIds: [String: Set<Int64>] = [:]
        var display: [String: String] = [:]

        for song in songs {
            for name in extractArtists(song.artist) {
                let key = canonicalKey(name)
                if bucket[key] == nil { order.append(key); bucket[key] = []; seenIds[key] = [] }
                if seenIds[key]!.insert(song.id).inserted { bucket[key]!.append(song) }
                if let current = display[key] {
                    if name.lowercased() != unknown.lowercased(),
                       current.lowercased() == unknown.lowercased() || prefer(name, over: current) {
                        display[key] = name
                    }
                } else {
                    display[key] = name
                }
            }
        }

        return order.map { key in
            let list = bucket[key]!.sorted(by: titleLess)
            return Artist(name: display[key] ?? unknown, canonicalKey: key,
                          albumCount: Set(list.map(\.albumId)).count,
                          representativeAlbumId: list.first?.albumId ?? -1, songs: list)
        }.sorted { $0.name.lowercased() < $1.name.lowercased() }
    }

    private static func titleLess(_ a: Song, _ b: Song) -> Bool {
        a.title.lowercased() < b.title.lowercased()
    }

    private static func prefer(_ candidate: String, over current: String) -> Bool {
        let cu = candidate.filter(\.isUppercase).count
        let ku = current.filter(\.isUppercase).count
        return cu != ku ? cu > ku : candidate.count > current.count
    }
}
