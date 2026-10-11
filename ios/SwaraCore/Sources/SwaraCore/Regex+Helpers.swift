import Foundation

/// Thin wrapper around NSRegularExpression (ICU syntax, same as java.util.regex for
/// the patterns used here), so the Android regexes port verbatim.
struct Rx: @unchecked Sendable {
    let re: NSRegularExpression

    init(_ pattern: String, caseInsensitive: Bool = false) {
        // Patterns are compile-time constants; a failure is a programming error.
        re = try! NSRegularExpression(pattern: pattern,
                                      options: caseInsensitive ? [.caseInsensitive] : [])
    }

    private func range(_ s: String) -> NSRange { NSRange(s.startIndex..., in: s) }

    func replaceAll(_ s: String, with template: String) -> String {
        re.stringByReplacingMatches(in: s, range: range(s), withTemplate: template)
    }

    func split(_ s: String) -> [String] {
        var parts: [String] = []
        var last = s.startIndex
        for m in re.matches(in: s, range: range(s)) {
            guard let r = Range(m.range, in: s) else { continue }
            parts.append(String(s[last..<r.lowerBound]))
            last = r.upperBound
        }
        parts.append(String(s[last...]))
        return parts
    }

    func firstGroups(_ s: String, group: Int = 1) -> [String] {
        re.matches(in: s, range: range(s)).compactMap {
            Range($0.range(at: group), in: s).map { String(s[$0]) }
        }
    }
}

extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}
