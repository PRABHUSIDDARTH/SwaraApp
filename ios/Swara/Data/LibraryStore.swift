import AVFoundation
import Foundation
import SwaraCore
import UIKit

/// On-device music library.
///
/// iOS has no MediaStore equivalent for arbitrary audio files, so Swara owns a folder:
/// `Documents/` (visible in the Files app as "On My iPhone > Swara"). Files added there via
/// Files, Finder/iTunes File Sharing, AirDrop or the in-app importer are indexed here.
@Observable
@MainActor
final class LibraryStore {
    private(set) var songs: [Song] = []
    private(set) var albums: [Album] = []
    private(set) var artists: [Artist] = []
    private(set) var isScanning = false
    private(set) var lastError: String?
    /// Bumped on every playlist mutation so SwiftUI re-reads playlists from the database.
    private(set) var playlistsVersion = 0

    let db = SwaraDatabase.shared
    private var byId: [Int64: Song] = [:]

    nonisolated static let audioExtensions: Set<String> = ["mp3", "m4a", "aac", "wav", "aif", "aiff", "caf", "flac", "alac", "mp4"]

    nonisolated static var root: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    }

    func song(id: Int64) -> Song? { byId[id] }

    func url(for song: Song) -> URL { Self.root.appendingPathComponent(song.relativePath) }

    func refresh() async {
        isScanning = true
        defer { isScanning = false }
        let cache = db.metadataCache()
        let root = Self.root
        let scanned = await Task.detached(priority: .userInitiated) {
            await Self.scan(root: root, cache: cache)
        }.value
        db.saveMetadataCache(scanned.cache)
        apply(scanned.songs)
    }

    private func apply(_ list: [Song]) {
        songs = list
        byId = Dictionary(list.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        albums = Album.build(from: list)
        artists = ArtistIdentity.buildArtists(list)
    }

    /// Copies picked files into the library folder (security-scoped URLs from the picker).
    func importFiles(_ urls: [URL]) async {
        let fm = FileManager.default
        for url in urls {
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            guard Self.audioExtensions.contains(url.pathExtension.lowercased()) else { continue }
            var dest = Self.root.appendingPathComponent(url.lastPathComponent)
            var n = 1
            while fm.fileExists(atPath: dest.path) {
                let base = url.deletingPathExtension().lastPathComponent
                dest = Self.root.appendingPathComponent("\(base) (\(n)).\(url.pathExtension)")
                n += 1
            }
            do { try fm.copyItem(at: url, to: dest) } catch { lastError = error.localizedDescription }
        }
        await refresh()
    }

    /// Removes the file from disk and clears it from favorites/playlists/history.
    func delete(_ song: Song) async {
        try? FileManager.default.removeItem(at: url(for: song))
        db.purge(songId: song.id)
        playlistsVersion += 1
        await refresh()
    }

    /// Writes edited tags to Swara's metadata overrides (files are not re-encoded).
    func updateMetadata(_ song: Song, title: String, artist: String, album: String) {
        db.setOverride(MetadataOverride(title: title, artist: artist, album: album), for: song.id)
        var list = songs
        if let i = list.firstIndex(of: song) {
            list[i].title = title.isEmpty ? list[i].title : title
            list[i].artist = artist.isEmpty ? list[i].artist : artist
            list[i].album = album.isEmpty ? list[i].album : album
            list[i].albumId = StableID.of(list[i].album.lowercased() + "\u{1}" + list[i].artist.lowercased())
        }
        apply(list)
    }

    // MARK: - Playlists

    func playlists() -> [Playlist] {
        _ = playlistsVersion
        return db.playlists()
    }

    func playlist(_ id: Int64) -> Playlist? {
        _ = playlistsVersion
        return db.playlist(id)
    }

    func songs(in playlist: Playlist) -> [Song] { playlist.songIds.compactMap(song(id:)) }

    @discardableResult
    func createPlaylist(name: String, songs: [Song] = []) -> Playlist {
        let p = db.createPlaylist(name: name, songIds: songs.map(\.id))
        playlistsVersion += 1
        return p
    }

    func updatePlaylist(_ id: Int64, _ change: (inout Playlist) -> Void) {
        db.updatePlaylist(id, change)
        playlistsVersion += 1
    }

    func add(_ songs: [Song], toPlaylist id: Int64) {
        updatePlaylist(id) { $0.songIds += songs.map(\.id) }
    }

    func deletePlaylist(_ id: Int64) {
        db.deletePlaylist(id)
        playlistsVersion += 1
    }

    // MARK: - Scanning

    struct ScanResult: Sendable {
        var songs: [Song] = []
        var cache: [String: CachedMetadata] = [:]
    }

    nonisolated private static func scan(root: URL, cache: [String: CachedMetadata]) async -> ScanResult {
        let fm = FileManager.default
        let keys: [URLResourceKey] = [.isRegularFileKey, .creationDateKey, .contentModificationDateKey, .fileSizeKey]
        guard let walker = fm.enumerator(at: root, includingPropertiesForKeys: keys,
                                         options: [.skipsHiddenFiles, .skipsPackageDescendants]) else { return ScanResult() }
        let overrides = SwaraDatabase.shared.overrides()
        let rootPath = root.standardizedFileURL.path
        var result = ScanResult()
        var files: [URL] = []
        while let next = walker.nextObject() as? URL {
            if audioExtensions.contains(next.pathExtension.lowercased()) { files.append(next) }
        }
        for url in files {
            let values = try? url.resourceValues(forKeys: Set(keys))
            guard values?.isRegularFile == true else { continue }
            var rel = url.standardizedFileURL.path
            if rel.hasPrefix(rootPath) { rel = String(rel.dropFirst(rootPath.count + 1)) }
            let stamp = "\(values?.fileSize ?? 0)-\(values?.contentModificationDate?.timeIntervalSince1970 ?? 0)"
            let added = Int64((values?.creationDate ?? Date()).timeIntervalSince1970)

            let id = StableID.of(rel)
            var meta: CachedMetadata
            if let c = cache[rel], c.stamp == stamp {
                meta = c
            } else {
                meta = await readMetadata(url: url, stamp: stamp, songId: id)
            }
            result.cache[rel] = meta
            if let o = overrides[id] {
                if !o.title.isEmpty { meta.title = o.title }
                if !o.artist.isEmpty { meta.artist = o.artist }
                if !o.album.isEmpty { meta.album = o.album }
            }
            let title = meta.title ?? url.deletingPathExtension().lastPathComponent
            let album = meta.album ?? "Unknown Album"
            let artist = meta.artist ?? "Unknown Artist"
            result.songs.append(Song(id: id, title: title, artist: artist, album: album,
                               albumId: StableID.of(album.lowercased() + "\u{1}" + (meta.albumArtist ?? artist).lowercased()),
                               duration: meta.durationMs, trackNumber: meta.track, year: meta.year,
                               dateAdded: added, relativePath: rel))
        }
        return result
    }

    nonisolated private static func readMetadata(url: URL, stamp: String, songId: Int64) async -> CachedMetadata {
        let asset = AVURLAsset(url: url)
        var m = CachedMetadata(stamp: stamp)
        if let d = try? await asset.load(.duration), d.isNumeric { m.durationMs = Int64(d.seconds * 1000) }
        let items = ((try? await asset.load(.commonMetadata)) ?? [])
            + ((try? await asset.load(.metadata)) ?? [])
        for item in items {
            let key = item.commonKey?.rawValue ?? (item.identifier?.rawValue ?? "")
            switch key {
            case AVMetadataKey.commonKeyTitle.rawValue:
                if m.title == nil { m.title = try? await item.load(.stringValue) }
            case AVMetadataKey.commonKeyArtist.rawValue:
                if m.artist == nil { m.artist = try? await item.load(.stringValue) }
            case AVMetadataKey.commonKeyAlbumName.rawValue:
                if m.album == nil { m.album = try? await item.load(.stringValue) }
            case AVMetadataKey.commonKeyArtwork.rawValue:
                if !m.hasArtwork, let data = try? await item.load(.dataValue) {
                    m.hasArtwork = ArtworkCache.shared.storeEmbedded(data, songId: songId)
                }
            case AVMetadataIdentifier.id3MetadataTrackNumber.rawValue,
                 AVMetadataIdentifier.iTunesMetadataTrackNumber.rawValue:
                if let s = try? await item.load(.stringValue) {
                    m.track = Int(s.split(separator: "/").first ?? "") ?? 0
                }
            case AVMetadataIdentifier.id3MetadataYear.rawValue,
                 AVMetadataIdentifier.id3MetadataRecordingTime.rawValue,
                 AVMetadataIdentifier.iTunesMetadataReleaseDate.rawValue:
                if m.year == 0, let s = try? await item.load(.stringValue) { m.year = Int(s.prefix(4)) ?? 0 }
            case AVMetadataIdentifier.id3MetadataBand.rawValue,
                 AVMetadataIdentifier.iTunesMetadataAlbumArtist.rawValue:
                if m.albumArtist == nil { m.albumArtist = try? await item.load(.stringValue) }
            default: break
            }
        }
        return m
    }
}
