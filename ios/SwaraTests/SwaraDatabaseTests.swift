import XCTest
@testable import Swara

final class SwaraDatabaseTests: XCTestCase {
    private var dir: URL!

    override func setUpWithError() throws {
        dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
    }

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: dir)
    }

    /// Reads the persisted history back from disk (the file is the source of truth on relaunch).
    private func persistedHistory(_ db: SwaraDatabase) throws -> [PlayHistoryEntry] {
        let raw = try Data(contentsOf: dir.appendingPathComponent("userdata.json"))
        return try JSONDecoder().decode(UserData.self, from: raw).history
    }

    func testPlayIsRecordedOnceWithinDebounceWindow() throws {
        let db = SwaraDatabase(directory: dir)
        let t0 = Date(timeIntervalSince1970: 1_000_000)
        // 200 s track, 40 s in: past the min(30 s, 40 %) threshold.
        db.maybeRecordPlay(songId: 7, positionMs: 40_000, durationMs: 200_000, now: t0)
        db.maybeRecordPlay(songId: 7, positionMs: 40_000, durationMs: 200_000, now: t0.addingTimeInterval(5))
        XCTAssertEqual(try persistedHistory(db).count, 1)
    }

    func testPlayAfterDebounceIsRecordedAgain() throws {
        let db = SwaraDatabase(directory: dir)
        let t0 = Date(timeIntervalSince1970: 1_000_000)
        db.maybeRecordPlay(songId: 7, positionMs: 40_000, durationMs: 200_000, now: t0)
        db.maybeRecordPlay(songId: 7, positionMs: 40_000, durationMs: 200_000, now: t0.addingTimeInterval(60))
        XCTAssertEqual(try persistedHistory(db).count, 2)
    }

    func testShortListenIsNotRecorded() throws {
        let db = SwaraDatabase(directory: dir)
        db.maybeRecordPlay(songId: 9, positionMs: 5_000, durationMs: 200_000)
        XCTAssertTrue(db.recentlyPlayedIds().isEmpty)
    }

    func testFavoritesToggleAndPurge() {
        let db = SwaraDatabase(directory: dir)
        XCTAssertTrue(db.toggleFavorite(3))
        XCTAssertTrue(db.isFavorite(3))
        db.purge(songId: 3)
        XCTAssertFalse(db.isFavorite(3))
    }
}
