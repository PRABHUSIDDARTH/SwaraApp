import SwiftUI
import SwaraCore

struct SettingsView: View {
    @Environment(\.palette) var p
    @Environment(\.dismiss) var dismiss
    @Environment(AppSettings.self) var settings
    @Environment(LibraryStore.self) var library
    @State var confirmClear = false

    var body: some View {
        @Bindable var bindable = settings
        NavigationStack {
            Form {
                Section("Appearance") {
                    Picker("Mode", selection: $bindable.themeMode) {
                        ForEach(ThemeMode.allCases) { Text($0.title).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    ForEach(ColorTheme.allCases) { theme in
                        Button { settings.colorTheme = theme } label: { themeRow(theme) }
                            .buttonStyle(.plain)
                    }
                }
                Section("Library") {
                    LabeledContent("Songs", value: "\(library.songs.count)")
                    Button {
                        Task { await library.refresh() }
                    } label: {
                        HStack {
                            Text("Rescan Library")
                            Spacer()
                            if library.isScanning { ProgressView() }
                        }
                    }
                    .disabled(library.isScanning)
                    Text("Music lives in Files > On My iPhone > Swara. Add files there, through Finder file sharing, AirDrop, or the import button on Home.")
                        .font(.footnote).foregroundStyle(p.textSecondary)
                }
                Section("Privacy") {
                    Button("Clear Listening History", role: .destructive) { confirmClear = true }
                }
                Section("About") {
                    LabeledContent("Version", value: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "-")
                    Text("Swara plays your own music fully offline. No accounts, no tracking.")
                        .font(.footnote).foregroundStyle(p.textSecondary)
                }
            }
            .scrollContentBackground(.hidden)
            .background(AtmosphereBackground())
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { Button("Done") { dismiss() } }
            .confirmationDialog("Clear listening history?", isPresented: $confirmClear, titleVisibility: .visible) {
                Button("Clear History", role: .destructive) { SwaraDatabase.shared.clearHistory() }
            } message: {
                Text("Recently Played and Most Played will be reset.")
            }
        }
        .modifier(ThemedRoot())
    }

    private func themeRow(_ theme: ColorTheme) -> some View {
        let dark = settings.themeMode == .dark
        return HStack(spacing: 12) {
            Circle().fill(Color(theme.accent(dark: dark))).frame(width: 26, height: 26)
                .overlay(Circle().strokeBorder(p.stroke, lineWidth: 1))
            VStack(alignment: .leading, spacing: 2) {
                Text(theme.title).foregroundStyle(p.textPrimary)
                Text(theme.subtitle).font(.caption).foregroundStyle(p.textSecondary).lineLimit(2)
            }
            Spacer()
            if settings.colorTheme == theme {
                Image(systemName: "checkmark").foregroundStyle(p.accent).fontWeight(.semibold)
            }
        }
        .contentShape(Rectangle())
        .accessibilityAddTraits(settings.colorTheme == theme ? .isSelected : [])
    }
}
