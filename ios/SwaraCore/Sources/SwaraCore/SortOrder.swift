import Foundation

/// Mirrors Android `SortOrder`.
public enum SortOrder: String, CaseIterable, Codable, Sendable {
    case titleAsc = "TITLE_ASC"
    case titleDesc = "TITLE_DESC"
    case artistAsc = "ARTIST_ASC"
    case dateAdded = "DATE_ADDED"
    case durationAsc = "DURATION_ASC"
    case custom = "CUSTOM"

    /// Unknown or missing keys fall back to `.titleAsc`.
    public static func fromKey(_ key: String?) -> SortOrder {
        key.flatMap(SortOrder.init(rawValue:)) ?? .titleAsc
    }

    public var label: String {
        switch self {
        case .titleAsc: return "Title (A–Z)"
        case .titleDesc: return "Title (Z–A)"
        case .artistAsc: return "Artist (A–Z)"
        case .dateAdded: return "Recently Added"
        case .durationAsc: return "Duration (Shortest)"
        case .custom: return "Custom (drag to arrange)"
        }
    }

    public func apply(_ songs: [Song], customOrder: [Int64] = []) -> [Song] {
        let lt: (String, String) -> Bool = { $0.lowercased() < $1.lowercased() }
        switch self {
        case .titleAsc: return songs.sorted { lt($0.title, $1.title) }
        case .titleDesc: return songs.sorted { lt($1.title, $0.title) }
        case .artistAsc: return songs.sorted { lt($0.artist, $1.artist) }
        case .dateAdded: return songs.sorted { $0.dateAdded > $1.dateAdded }
        case .durationAsc: return songs.sorted { $0.duration < $1.duration }
        case .custom: return CustomSongOrder.apply(songs, order: customOrder)
        }
    }
}

/// Mirrors Android `CustomSongOrder`: comma-separated song IDs.
public enum CustomSongOrder {
    public static func encode(_ songs: [Song]) -> String {
        songs.map { String($0.id) }.joined(separator: ",")
    }

    /// Corrupt entries are skipped rather than discarding the whole order.
    public static func decode(_ raw: String?) -> [Int64] {
        guard let raw, !raw.isEmpty else { return [] }
        return raw.split(separator: ",").compactMap {
            Int64($0.trimmingCharacters(in: .whitespaces))
        }
    }

    /// Saved order first, then unknown songs (newest added first).
    public static func apply(_ songs: [Song], order: [Int64]) -> [Song] {
        var rank: [Int64: Int] = [:]
        for (i, id) in order.enumerated() where rank[id] == nil { rank[id] = i }
        return songs.sorted { a, b in
            switch (rank[a.id], rank[b.id]) {
            case let (ra?, rb?): return ra < rb
            case (.some, nil): return true
            case (nil, .some): return false
            default: return a.dateAdded > b.dateAdded
            }
        }
    }
}
