import Foundation

/// RGBA color value independent of UIKit/SwiftUI so it can be unit-tested on Linux.
public struct RGBA: Equatable, Sendable {
    public let r: Double, g: Double, b: Double, a: Double

    public init(hex: UInt32, alpha: Double = 1) {
        r = Double((hex >> 16) & 0xFF) / 255
        g = Double((hex >> 8) & 0xFF) / 255
        b = Double(hex & 0xFF) / 255
        a = alpha
    }

    public func withAlpha(_ a: Double) -> RGBA {
        RGBA(hex: UInt32(r * 255) << 16 | UInt32(g * 255) << 8 | UInt32(b * 255), alpha: a)
    }

    /// WCAG relative luminance.
    public var luminance: Double {
        func ch(_ c: Double) -> Double { c <= 0.03928 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)
    }

    /// Perceived luma used by Android `DesignTokens.getButtonTextColor`.
    public var perceivedLuma: Double { 0.299 * r + 0.587 * g + 0.114 * b }

    public static func contrast(_ x: RGBA, _ y: RGBA) -> Double {
        let (l1, l2) = (max(x.luminance, y.luminance), min(x.luminance, y.luminance))
        return (l1 + 0.05) / (l2 + 0.05)
    }
}

/// The eight Swara color themes (mirrors Android `ColorTheme` + `DesignTokens.resolveAccent`).
public enum ColorTheme: String, CaseIterable, Codable, Identifiable, Sendable {
    case swara = "SWARA", midnight = "MIDNIGHT", lavender = "LAVENDER", champagne = "CHAMPAGNE"
    case rose = "ROSE", ocean = "OCEAN", forest = "FOREST", offWhite = "OFF_WHITE"

    public var id: String { rawValue }

    public static func fromKey(_ key: String?) -> ColorTheme {
        guard let key else { return .swara }
        return allCases.first { $0.rawValue.caseInsensitiveCompare(key) == .orderedSame } ?? .swara
    }

    public var title: String {
        switch self {
        case .swara: return "Swara"
        case .midnight: return "Midnight"
        case .lavender: return "Lavender"
        case .champagne: return "Champagne"
        case .rose: return "Rose"
        case .ocean: return "Ocean"
        case .forest: return "Forest"
        case .offWhite: return "Off-White"
        }
    }

    public var subtitle: String {
        switch self {
        case .swara: return "Signature midnight purple with obsidian gold accents"
        case .midnight: return "Pitch black obsidian with ice blue highlights"
        case .lavender: return "Royal violet with soft amethyst lavender tones"
        case .champagne: return "Rich dark amber with warm champagne gold accents"
        case .rose: return "Crimson obsidian with soft rose gold highlights"
        case .ocean: return "Deep sapphire ocean with aquamarine accents"
        case .forest: return "Deep emerald forest with sage leaf gold accents"
        case .offWhite: return "Premium warm cream with near-black contrast"
        }
    }

    /// One accent per theme; light values are tuned for >= 4.5:1 on white.
    public func accent(dark: Bool) -> RGBA {
        switch self {
        case .midnight: return RGBA(hex: dark ? 0x0A84FF : 0x0066D6)
        case .lavender: return RGBA(hex: dark ? 0xBF5AF2 : 0x8E3DBD)
        case .champagne: return RGBA(hex: dark ? 0xFF9F0A : 0xB25000)
        case .rose: return RGBA(hex: dark ? 0xFF375F : 0xD30F45)
        case .ocean: return RGBA(hex: dark ? 0x40C8E0 : 0x0A7C8C)
        case .forest: return RGBA(hex: dark ? 0x30D158 : 0x1E7F3A)
        case .offWhite: return RGBA(hex: dark ? 0xE8C97A : 0x5C4A1E)
        case .swara: return RGBA(hex: dark ? 0xD4AF5A : 0x8A6610)
        }
    }
}

/// Semantic tokens shared by every theme (mirrors Android `DesignTokens`).
public struct DesignTokens: Sendable {
    public let accent: RGBA
    public let background: RGBA
    public let backgroundSecondary: RGBA
    public let surface: RGBA
    public let surfaceElevated: RGBA
    public let surfaceVariant: RGBA
    public let textPrimary: RGBA
    public let textSecondary: RGBA
    public let textTertiary: RGBA
    public let stroke: RGBA
    public let divider: RGBA
    public let selected: RGBA
    /// Text/icon color drawn on top of a solid accent fill.
    public let onAccent: RGBA

    public init(theme: ColorTheme, dark: Bool) {
        let a = theme.accent(dark: dark)
        accent = a
        if dark {
            background = RGBA(hex: 0x000000)
            backgroundSecondary = RGBA(hex: 0x0B0B0D)
            surface = RGBA(hex: 0x1C1C1E)
            surfaceElevated = RGBA(hex: 0x2C2C2E)
            surfaceVariant = RGBA(hex: 0x2C2C2E)
            textPrimary = RGBA(hex: 0xFFFFFF)
            textSecondary = RGBA(hex: 0xA1A1A6)
            textTertiary = RGBA(hex: 0x8E8E93)
            stroke = RGBA(hex: 0x38383A)
            divider = RGBA(hex: 0x2C2C2E)
            selected = a.withAlpha(Double(0x26) / 255)
        } else {
            background = RGBA(hex: 0xF2F2F7)
            backgroundSecondary = RGBA(hex: 0xEBEBF0)
            surface = RGBA(hex: 0xFFFFFF)
            surfaceElevated = RGBA(hex: 0xFFFFFF)
            surfaceVariant = RGBA(hex: 0xE9E9EE)
            textPrimary = RGBA(hex: 0x1C1C1E)
            textSecondary = RGBA(hex: 0x55555A)
            textTertiary = RGBA(hex: 0x6E6E73)
            stroke = RGBA(hex: 0xD1D1D6)
            divider = RGBA(hex: 0xE5E5EA)
            selected = a.withAlpha(Double(0x1F) / 255)
        }
        onAccent = a.perceivedLuma > 0.55 ? RGBA(hex: 0x1A1A1A) : RGBA(hex: 0xFFFFFF)
    }
}

/// Sleep timer presets (minutes), as on Android.
public enum SleepTimerPreset {
    public static let minutes = [15, 30, 45, 60]
}

/// Repeat mode cycle Off -> One -> All -> Off (mirrors `QueueManager.cycleRepeatMode`).
public enum RepeatMode: Int, Codable, Sendable {
    case off, one, all
    public var next: RepeatMode {
        switch self { case .off: return .one; case .one: return .all; case .all: return .off }
    }
}
