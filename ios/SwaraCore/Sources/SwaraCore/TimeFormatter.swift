import Foundation

/// Mirrors Android `util/TimeFormatter`.
public enum TimeFormatter {
    /// Formats milliseconds as M:SS or H:MM:SS.
    public static func formatMs(_ ms: Int64) -> String {
        let total = max(0, ms) / 1000
        let h = total / 3600, m = (total % 3600) / 60, s = total % 60
        func pad(_ v: Int64) -> String { v < 10 ? "0\(v)" : "\(v)" }
        return h > 0 ? "\(h):\(pad(m)):\(pad(s))" : "\(m):\(pad(s))"
    }

    public static func formatSeconds(_ seconds: Double) -> String {
        guard seconds.isFinite, seconds > 0 else { return "0:00" }
        return formatMs(Int64(seconds * 1000))
    }

    /// Spoken form for VoiceOver, e.g. "3 minutes 42 seconds".
    public static func formatAccessible(_ ms: Int64) -> String {
        let total = max(0, ms) / 1000
        let m = total / 60, s = total % 60
        if m == 0 { return "\(s) seconds" }
        return "\(m) minute\(m == 1 ? "" : "s")" + (s > 0 ? " \(s) seconds" : "")
    }
}
