import SwaraCore
import UIKit

/// Disk + memory cache for embedded song artwork and playlist artwork
/// (replaces Glide on Android). Images are downscaled to 600 px JPEGs on disk.
final class ArtworkCache: @unchecked Sendable {
    static let shared = ArtworkCache()

    private let memory = NSCache<NSString, UIImage>()
    private let dir: URL

    init() {
        dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("artwork", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        memory.countLimit = 300
    }

    private func file(_ songId: Int64) -> URL { dir.appendingPathComponent("\(songId).jpg") }

    /// Returns true when the image decoded and was stored.
    @discardableResult
    func storeEmbedded(_ data: Data, songId: Int64) -> Bool {
        guard let image = UIImage(data: data) else { return false }
        let scaled = image.downscaled(maxSide: 600)
        guard let jpeg = scaled.jpegData(compressionQuality: 0.85) else { return false }
        try? jpeg.write(to: file(songId), options: .atomic)
        memory.removeObject(forKey: "\(songId)" as NSString)
        return true
    }

    func removeEmbedded(songId: Int64) {
        try? FileManager.default.removeItem(at: file(songId))
        memory.removeObject(forKey: "\(songId)" as NSString)
    }

    func image(songId: Int64) -> UIImage? {
        let key = "\(songId)" as NSString
        if let hit = memory.object(forKey: key) { return hit }
        guard let img = UIImage(contentsOfFile: file(songId).path) else { return nil }
        memory.setObject(img, forKey: key)
        return img
    }

    /// First song in the list that has artwork (album art fallback).
    func image(forAnyOf songs: [Song]) -> UIImage? {
        for s in songs.prefix(25) { if let i = image(songId: s.id) { return i } }
        return nil
    }

    /// Playlist artwork priority (as on Android): custom image -> 2x2 collage -> nil (fallback icon).
    func playlistImage(_ playlist: Playlist, songs: [Song]) -> UIImage? {
        if let f = playlist.artworkFile {
            let url = SwaraDatabase.shared.playlistArtworkDir.appendingPathComponent(f)
            let key = "pl-\(f)-\(playlist.modifiedAt.timeIntervalSince1970)" as NSString
            if let hit = memory.object(forKey: key) { return hit }
            if let img = UIImage(contentsOfFile: url.path) { memory.setObject(img, forKey: key); return img }
        }
        var tiles: [UIImage] = []
        var seenAlbums = Set<Int64>()
        for s in songs where tiles.count < 4 {
            guard seenAlbums.insert(s.albumId).inserted, let i = image(songId: s.id) else { continue }
            tiles.append(i)
        }
        guard !tiles.isEmpty else { return nil }
        if tiles.count < 4 { return tiles[0] }
        let key = "collage-\(playlist.id)-\(playlist.modifiedAt.timeIntervalSince1970)" as NSString
        if let hit = memory.object(forKey: key) { return hit }
        let side: CGFloat = 400
        let img = UIGraphicsImageRenderer(size: CGSize(width: side, height: side)).image { _ in
            for (i, t) in tiles.enumerated() {
                let r = CGRect(x: CGFloat(i % 2) * side / 2, y: CGFloat(i / 2) * side / 2,
                               width: side / 2, height: side / 2)
                t.drawAspectFill(in: r)
            }
        }
        memory.setObject(img, forKey: key)
        return img
    }

    /// Saves a user-picked image as custom playlist artwork; returns the file name.
    func saveCustomPlaylistArtwork(_ data: Data, playlistId: Int64) -> String? {
        guard let img = UIImage(data: data)?.downscaled(maxSide: 1000),
              let jpeg = img.jpegData(compressionQuality: 0.9) else { return nil }
        let name = "\(playlistId)-\(Int(Date().timeIntervalSince1970)).jpg"
        let dir = SwaraDatabase.shared.playlistArtworkDir
        // Remove older artwork for this playlist so files don't accumulate.
        if let old = try? FileManager.default.contentsOfDirectory(atPath: dir.path) {
            for f in old where f.hasPrefix("\(playlistId)-") {
                try? FileManager.default.removeItem(at: dir.appendingPathComponent(f))
            }
        }
        do { try jpeg.write(to: dir.appendingPathComponent(name), options: .atomic) } catch { return nil }
        return name
    }
}

extension UIImage {
    func downscaled(maxSide: CGFloat) -> UIImage {
        let longest = max(size.width, size.height)
        guard longest > maxSide else { return self }
        let scale = maxSide / longest
        let target = CGSize(width: size.width * scale, height: size.height * scale)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: target, format: format).image { _ in
            draw(in: CGRect(origin: .zero, size: target))
        }
    }

    func drawAspectFill(in rect: CGRect) {
        let s = max(rect.width / size.width, rect.height / size.height)
        let w = size.width * s, h = size.height * s
        UIRectClip(rect)
        draw(in: CGRect(x: rect.midX - w / 2, y: rect.midY - h / 2, width: w, height: h))
    }
}
