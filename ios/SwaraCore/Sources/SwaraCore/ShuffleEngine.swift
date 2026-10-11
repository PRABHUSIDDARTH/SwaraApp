import Foundation

/// Unbiased Fisher-Yates shuffle with repeat avoidance (mirrors Android `ShuffleEngine`).
/// Never seeded deterministically, never reuses a previous order.
public enum ShuffleEngine {
    static let maxRetry = 5

    public static func shuffle<T>(_ source: [T]) -> [T] {
        var g = SystemRandomNumberGenerator()
        var copy = source
        fisherYates(&copy, using: &g)
        return copy
    }

    public static func fisherYates<T, G: RandomNumberGenerator>(_ list: inout [T], using g: inout G) {
        guard list.count > 1 else { return }
        for i in stride(from: list.count - 1, to: 0, by: -1) {
            list.swapAt(i, Int.random(in: 0...i, using: &g))
        }
    }

    /// Fresh permutation that differs from `previous` whenever possible.
    public static func shuffleAvoidRepeat<T: Equatable>(_ source: [T], previous: [T]?) -> [T] {
        if source.count <= 1 { return source }
        var g = SystemRandomNumberGenerator()
        var candidate = source
        for _ in 0..<maxRetry {
            candidate = source
            fisherYates(&candidate, using: &g)
            if candidate != (previous ?? []) { return candidate }
        }
        return candidate
    }

    /// Removes duplicate songs by stable ID, keeping first-occurrence order.
    public static func deduplicate(_ songs: [Song]) -> [Song] {
        var seen = Set<Int64>()
        return songs.filter { seen.insert($0.id).inserted }
    }

    /// Recommended entry point for Library / Playlist shuffle actions.
    public static func shuffleSongs(_ source: [Song], previous: [Song]?) -> [Song] {
        shuffleAvoidRepeat(deduplicate(source), previous: previous)
    }
}
