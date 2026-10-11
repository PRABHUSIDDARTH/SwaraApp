import Foundation
import SwaraCore

/// Cached tag data for one file; `stamp` (size + mtime) invalidates it.
struct CachedMetadata: Codable, Sendable {
    var stamp: String
    var title: String?
    var artist: String?
    var album: String?
    var albumArtist: String?
    var durationMs: Int64 = 0
    var track: Int = 0
    var year: Int = 0
    var hasArtwork = false
}

/// User edits from the metadata editor (Android writes ID3 in place; on iOS we keep overrides).
struct MetadataOverride: Codable, Sendable {
    var title: String
    var artist: String
    var album: String
}

struct Playlist: Codable, Identifiable, Hashable, Sendable {
    var id: Int64
    var name: String
    var createdAt: Date
    var modifiedAt: Date
    var songIds: [Int64]
    /// File name under Application Support/playlist_artwork, if the user picked one.
    var artworkFile: String?
}

struct PlayHistoryEntry: Codable, Sendable {
    var songId: Int64
    var playedAt: Date
}

struct FavoriteEntry: Codable, Sendable {
    var songId: Int64
    var favoritedAt: Date
}

/// Persisted user data (equivalent of Android's Room `AppDatabase`), stored as JSON.
/// Small, human-inspectable, and written atomically on every mutation.
struct UserData: Codable {
    var favorites: [FavoriteEntry] = []
    var playlists: [Playlist] = []
    var history: [PlayHistoryEntry] = []
    var overrides: [Int64: MetadataOverride] = [:]
    var nextPlaylistId: Int64 = 1
    var queueIds: [Int64] = []
    var queueIndex: Int = 0
    var queuePositionSec: Double = 0
}

final class SwaraDatabase: @unchecked Sendable {
    static let shared = SwaraDatabase()

    private let lock = NSLock()
    private var data: UserData
    private let dir: URL
    private var userURL: URL { dir.appendingPathComponent("userdata.json") }
    private var cacheURL: URL { dir.appendingPathComponent("metadata-cache.json") }

    init(directory: URL? = nil) {
        let base = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Swara", isDirectory: true)
        try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
        dir = base
        if let raw = try? Data(contentsOf: base.appendingPathComponent("userdata.json")),
           let decoded = try? JSONDecoder().decode(UserData.self, from: raw) {
            data = decoded
        } else {
            data = UserData()
        }
    }

    var playlistArtworkDir: URL {
        let d = dir.appendingPathComponent("playlist_artwork", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }

    private func mutate<T>(_ body: (inout UserData) -> T) -> T {
        lock.lock()
        let result = body(&data)
        let snapshot = data
        lock.unlock()
        if let raw = try? JSONEncoder().encode(snapshot) {
            try? raw.write(to: userURL, options: .atomic)
        }
        return result
    }

    private func read<T>(_ body: (UserData) -> T) -> T {
        lock.lock(); defer { lock.unlock() }
        return body(data)
    }

    // MARK: Metadata cache / overrides

    func metadataCache() -> [String: CachedMetadata] {
        guard let raw = try? Data(contentsOf: cacheURL) else { return [:] }
        return (try? JSONDecoder().decode([String: CachedMetadata].self, from: raw)) ?? [:]
    }

    func saveMetadataCache(_ cache: [String: CachedMetadata]) {
        if let raw = try? JSONEncoder().encode(cache) { try? raw.write(to: cacheURL, options: .atomic) }
    }

    func overrides() -> [Int64: MetadataOverride] { read { $0.overrides } }
    func setOverride(_ o: MetadataOverride, for id: Int64) { mutate { $0.overrides[id] = o } }

    // MARK: Favorites

    func favoriteIds() -> [Int64] {
        read { $0.favorites.sorted { $0.favoritedAt > $1.favoritedAt }.map(\.songId) }
    }

    func isFavorite(_ id: Int64) -> Bool { read { $0.favorites.contains { $0.songId == id } } }

    @discardableResult
    func toggleFavorite(_ id: Int64) -> Bool {
        mutate { d in
            if let i = d.favorites.firstIndex(where: { $0.songId == id }) {
                d.favorites.remove(at: i)
                return false
            }
            d.favorites.append(FavoriteEntry(songId: id, favoritedAt: Date()))
            return true
        }
    }

    // MARK: Playlists

    func playlists() -> [Playlist] { read { $0.playlists.sorted { $0.modifiedAt > $1.modifiedAt } } }
    func playlist(_ id: Int64) -> Playlist? { read { $0.playlists.first { $0.id == id } } }

    @discardableResult
    func createPlaylist(name: String, songIds: [Int64] = []) -> Playlist {
        mutate { d in
            let p = Playlist(id: d.nextPlaylistId, name: name, createdAt: Date(), modifiedAt: Date(),
                             songIds: ShuffleEngineIds.dedupe(songIds), artworkFile: nil)
            d.nextPlaylistId += 1
            d.playlists.append(p)
            return p
        }
    }

    func updatePlaylist(_ id: Int64, _ change: (inout Playlist) -> Void) {
        mutate { d in
            guard let i = d.playlists.firstIndex(where: { $0.id == id }) else { return }
            change(&d.playlists[i])
            d.playlists[i].songIds = ShuffleEngineIds.dedupe(d.playlists[i].songIds)
            d.playlists[i].modifiedAt = Date()
        }
    }

    func deletePlaylist(_ id: Int64) {
        let file = playlist(id)?.artworkFile
        mutate { $0.playlists.removeAll { $0.id == id } }
        if let file { try? FileManager.default.removeItem(at: playlistArtworkDir.appendingPathComponent(file)) }
    }

    // MARK: History

    /// Records a play if it passes the Android threshold + 30 s debounce; capped at 200 rows.
    func maybeRecordPlay(songId: Int64, positionMs: Int64, durationMs: Int64, now: Date = Date()) {
        guard PlayHistoryRule.qualifies(positionMs: positionMs, durationMs: durationMs) else { return }
        mutate { d in
            let cutoff = now.addingTimeInterval(-Double(PlayHistoryRule.debounceMs) / 1000)
            if d.history.contains(where: { $0.songId == songId && $0.playedAt > cutoff }) { return }
            d.history.insert(PlayHistoryEntry(songId: songId, playedAt: now), at: 0)
            if d.history.count > PlayHistoryRule.maxEntries {
                d.history.removeLast(d.history.count - PlayHistoryRule.maxEntries)
            }
        }
    }

    func recentlyPlayedIds(limit: Int = 20) -> [Int64] {
        read { d in
            var seen = Set<Int64>()
            return d.history.map(\.songId).filter { seen.insert($0).inserted }.prefix(limit).map { $0 }
        }
    }

    func mostPlayedIds(limit: Int = 20) -> [Int64] {
        read { d in
            let counts = Dictionary(d.history.map { ($0.songId, 1) }, uniquingKeysWith: +)
            return counts.sorted { $0.value != $1.value ? $0.value > $1.value : $0.key < $1.key }
                .prefix(limit).map(\.key)
        }
    }

    func clearHistory() { mutate { $0.history.removeAll() } }

    // MARK: Queue session

    func saveQueue(ids: [Int64], index: Int, positionSec: Double) {
        mutate { d in
            d.queueIds = ids; d.queueIndex = index; d.queuePositionSec = positionSec
        }
    }

    func savedQueue() -> (ids: [Int64], index: Int, positionSec: Double) {
        read { ($0.queueIds, $0.queueIndex, $0.queuePositionSec) }
    }

    /// Deleted file: drop it everywhere (mirrors Android `SongDeletionManager`).
    func purge(songId: Int64) {
        mutate { d in
            d.favorites.removeAll { $0.songId == songId }
            d.history.removeAll { $0.songId == songId }
            d.overrides[songId] = nil
            d.queueIds.removeAll { $0 == songId }
            for i in d.playlists.indices where d.playlists[i].songIds.contains(songId) {
                d.playlists[i].songIds.removeAll { $0 == songId }
                d.playlists[i].modifiedAt = Date()
            }
        }
        ArtworkCache.shared.removeEmbedded(songId: songId)
    }
}

enum ShuffleEngineIds {
    static func dedupe(_ ids: [Int64]) -> [Int64] {
        var seen = Set<Int64>()
        return ids.filter { seen.insert($0).inserted }
    }
}
