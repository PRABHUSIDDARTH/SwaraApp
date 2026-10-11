import SwiftUI
import SwaraCore

enum ThemeMode: Int, CaseIterable, Identifiable {
    case system, light, dark
    var id: Int { rawValue }
    var title: String { ["System", "Light", "Dark"][rawValue] }
    var colorScheme: ColorScheme? {
        switch self { case .system: return nil; case .light: return .light; case .dark: return .dark }
    }
}

/// User preferences persisted in UserDefaults (mirrors Android `ThemePreferences`).
@Observable
final class AppSettings {
    private let defaults = UserDefaults.standard

    var colorTheme: ColorTheme {
        didSet { defaults.set(colorTheme.rawValue, forKey: "colorTheme") }
    }
    var themeMode: ThemeMode {
        didSet { defaults.set(themeMode.rawValue, forKey: "themeMode") }
    }
    var sortOrder: SwaraCore.SortOrder {
        didSet { defaults.set(sortOrder.rawValue, forKey: "sortOrder") }
    }
    var customOrder: [Int64] {
        didSet { defaults.set(customOrder.map(String.init).joined(separator: ","), forKey: "customOrder") }
    }

    init() {
        colorTheme = ColorTheme.fromKey(defaults.string(forKey: "colorTheme"))
        // Android defaults to Dark when nothing is stored.
        themeMode = ThemeMode(rawValue: defaults.object(forKey: "themeMode") as? Int ?? 2) ?? .dark
        sortOrder = SwaraCore.SortOrder.fromKey(defaults.string(forKey: "sortOrder"))
        customOrder = CustomSongOrder.decode(defaults.string(forKey: "customOrder"))
    }
}
