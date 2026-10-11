import Foundation

/// Core track model (mirrors Android `domain/model/Song`).
/// `id` is a stable hash of the file path relative to the library root, so
/// favorites, playlists and custom order survive relaunches and re-scans.
public struct Song: Identifiable, Hashable, Codable, Sendable {
    public let id: Int64
    public var title: String
    public var artist: String
    public var album: String
    public var albumId: Int64
    /// Duration in milliseconds.
    public var duration: Int64
    public var trackNumber: Int
    public var year: Int
    /// Seconds since epoch.
    public var dateAdded: Int64
    /// Path relative to the library root (the app's Documents folder).
    public var relativePath: String

    public init(id: Int64, title: String?, artist: String?, album: String?,
                albumId: Int64 = 0, duration: Int64 = 0, trackNumber: Int = 0, year: Int = 0,
                dateAdded: Int64 = 0, relativePath: String = "") {
        self.id = id
        self.title = Song.clean(title) ?? "Unknown Song"
        self.artist = Song.clean(artist) ?? "Unknown Artist"
        self.album = Song.clean(album) ?? "Unknown Album"
        self.albumId = albumId
        self.duration = duration
        self.trackNumber = trackNumber
        self.year = year
        self.dateAdded = dateAdded
        self.relativePath = relativePath
    }

    public var formattedDuration: String { TimeFormatter.formatMs(duration) }

    public static func == (lhs: Song, rhs: Song) -> Bool { lhs.id == rhs.id }
    public func hash(into hasher: inout Hasher) { hasher.combine(id) }

    private static func clean(_ s: String?) -> String? {
        guard let t = s?.trimmingCharacters(in: .whitespacesAndNewlines), !t.isEmpty,
              t.lowercased() != "<unknown>" else { return nil }
        return t
    }
}

public struct Album: Identifiable, Hashable, Sendable {
    public let id: Int64
    public let title: String
    public let artist: String
    public let year: Int
    public let songs: [Song]
    public var songCount: Int { songs.count }

    public init(id: Int64, title: String, artist: String, year: Int, songs: [Song]) {
        self.id = id; self.title = title; self.artist = artist; self.year = year; self.songs = songs
    }

    public static func == (l: Album, r: Album) -> Bool { l.id == r.id }
    public func hash(into h: inout Hasher) { h.combine(id) }

    /// Groups songs into albums (by albumId), tracks ordered by track number then title.
    public static func build(from songs: [Song]) -> [Album] {
        let groups = Dictionary(grouping: songs, by: \.albumId)
        return groups.map { id, list in
            let sorted = list.sorted {
                $0.trackNumber != $1.trackNumber ? $0.trackNumber < $1.trackNumber
                    : $0.title.lowercased() < $1.title.lowercased()
            }
            let first = sorted[0]
            return Album(id: id, title: first.album, artist: first.artist,
                         year: list.map(\.year).max() ?? 0, songs: sorted)
        }.sorted { $0.title.lowercased() < $1.title.lowercased() }
    }
}

public struct Artist: Identifiable, Hashable, Sendable {
    public var id: String { canonicalKey }
    public let name: String
    public let canonicalKey: String
    public let albumCount: Int
    public let representativeAlbumId: Int64
    public let songs: [Song]
    public var songCount: Int { songs.count }

    public init(name: String, canonicalKey: String, albumCount: Int,
                representativeAlbumId: Int64, songs: [Song]) {
        self.name = name; self.canonicalKey = canonicalKey; self.albumCount = albumCount
        self.representativeAlbumId = representativeAlbumId; self.songs = songs
    }

    public static func == (l: Artist, r: Artist) -> Bool { l.canonicalKey == r.canonicalKey }
    public func hash(into h: inout Hasher) { h.combine(canonicalKey) }
}

/// Stable 63-bit FNV-1a hash (Swift's `Hasher` is randomly seeded per process).
public enum StableID {
    public static func of(_ s: String) -> Int64 {
        var h: UInt64 = 0xcbf29ce484222325
        for b in s.utf8 {
            h ^= UInt64(b)
            h = h &* 0x100000001b3
        }
        return Int64(bitPattern: h & 0x7fff_ffff_ffff_ffff)
    }
}
