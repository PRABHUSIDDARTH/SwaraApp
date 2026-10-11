import SwiftUI
import SwaraCore

extension Color {
    init(_ c: RGBA) { self.init(.sRGB, red: c.r, green: c.g, blue: c.b, opacity: c.a) }
}

/// SwiftUI view of Android `DesignTokens` for the active theme + appearance.
struct Palette {
    let tokens: DesignTokens
    var accent: Color { Color(tokens.accent) }
    var background: Color { Color(tokens.background) }
    var backgroundSecondary: Color { Color(tokens.backgroundSecondary) }
    var surface: Color { Color(tokens.surface) }
    var surfaceElevated: Color { Color(tokens.surfaceElevated) }
    var surfaceVariant: Color { Color(tokens.surfaceVariant) }
    var textPrimary: Color { Color(tokens.textPrimary) }
    var textSecondary: Color { Color(tokens.textSecondary) }
    var textTertiary: Color { Color(tokens.textTertiary) }
    var stroke: Color { Color(tokens.stroke) }
    var selected: Color { Color(tokens.selected) }
    var onAccent: Color { Color(tokens.onAccent) }
}

private struct PaletteKey: EnvironmentKey {
    static let defaultValue = Palette(tokens: DesignTokens(theme: .swara, dark: true))
}

extension EnvironmentValues {
    var palette: Palette {
        get { self[PaletteKey.self] }
        set { self[PaletteKey.self] = newValue }
    }
}

/// Injects the palette for the current color scheme and tints the hierarchy.
struct ThemedRoot: ViewModifier {
    @Environment(AppSettings.self) var settings
    @Environment(\.colorScheme) var scheme

    func body(content: Content) -> some View {
        let p = Palette(tokens: DesignTokens(theme: settings.colorTheme, dark: scheme == .dark))
        content
            .environment(\.palette, p)
            .tint(p.accent)
    }
}

/// Liquid Glass surface: translucent material, soft accent stroke, 20 pt corners
/// (Android LIQUID_GLASS morphism: radius 20, stroke 1).
struct GlassBackground: ViewModifier {
    @Environment(\.palette) var p
    var radius: CGFloat = 20

    func body(content: Content) -> some View {
        content
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: radius, style: .continuous)
                    .strokeBorder(LinearGradient(colors: [p.accent.opacity(0.35), p.stroke.opacity(0.4)],
                                                 startPoint: .topLeading, endPoint: .bottomTrailing),
                                  lineWidth: 1)
            )
    }
}

extension View {
    func glass(radius: CGFloat = 20) -> some View { modifier(GlassBackground(radius: radius)) }
}

/// Atmospheric background: theme background with a soft accent glow.
struct AtmosphereBackground: View {
    @Environment(\.palette) var p

    var body: some View {
        ZStack {
            p.background
            RadialGradient(colors: [p.accent.opacity(0.18), .clear], center: .topLeading,
                           startRadius: 10, endRadius: 420)
            RadialGradient(colors: [p.accent.opacity(0.08), .clear], center: .bottomTrailing,
                           startRadius: 10, endRadius: 360)
        }
        .ignoresSafeArea()
    }
}

/// Solid accent capsule button (primary CTA: Play All, dialog confirm).
struct AccentButtonStyle: ButtonStyle {
    @Environment(\.palette) var p

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.semibold))
            .padding(.horizontal, 18).padding(.vertical, 11)
            .frame(maxWidth: .infinity)
            .foregroundStyle(p.onAccent)
            .background(p.accent, in: Capsule())
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

/// Secondary glass capsule (Shuffle).
struct GlassButtonStyle: ButtonStyle {
    @Environment(\.palette) var p

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.semibold))
            .padding(.horizontal, 18).padding(.vertical, 11)
            .frame(maxWidth: .infinity)
            .foregroundStyle(p.textPrimary)
            .glass(radius: 22)
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }
}
