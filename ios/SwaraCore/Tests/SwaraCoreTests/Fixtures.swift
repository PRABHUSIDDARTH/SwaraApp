import Foundation
import SwaraCore

/// Shared fixtures live in the Android tree; tests reference them in place instead of copying.
enum Fixtures {
    static let repoRoot: URL = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent() // SwaraCoreTests
        .deletingLastPathComponent() // Tests
        .deletingLastPathComponent() // SwaraCore
        .deletingLastPathComponent() // ios
        .deletingLastPathComponent() // repo root

    static let modelURL = repoRoot.appendingPathComponent("android/app/src/main/assets/models/korokae_v1.bin")
    static let parityURL = repoRoot.appendingPathComponent("android/app/src/test/resources/korokae_parity.bin")

    static func song(_ id: Int64, _ title: String?, _ artist: String?, _ album: String? = "A",
                     albumId: Int64 = 1, duration: Int64 = 200_000, dateAdded: Int64 = 1000) -> Song {
        Song(id: id, title: title, artist: artist, album: album, albumId: albumId,
             duration: duration, trackNumber: 1, year: 2024, dateAdded: dateAdded)
    }
}
