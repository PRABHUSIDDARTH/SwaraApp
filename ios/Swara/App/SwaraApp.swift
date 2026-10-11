import SwiftUI
import SwaraCore

@main
struct SwaraApp: App {
    @State private var library = LibraryStore()
    @State private var player = PlayerController()
    @State private var settings = AppSettings()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(library)
                .environment(player)
                .environment(settings)
                .preferredColorScheme(settings.themeMode.colorScheme)
                .task {
                    player.attach(library: library)
                    await library.refresh()
                    player.restoreSession()
                }
                .onOpenURL { url in
                    // "Open in Swara" from Files / share sheet.
                    Task { await library.importFiles([url]) }
                }
        }
    }
}
