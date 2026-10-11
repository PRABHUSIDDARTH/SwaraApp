import SwiftUI
import SwaraCore
import UniformTypeIdentifiers

struct HomeView: View {
    @Environment(\.palette) var p
    @Environment(LibraryStore.self) var library
    @Environment(PlayerController.self) var player
    @State var importing = false
    @State var showSettings = false
    @State var actions = SongActions()
    @State var refreshToken = 0

    private var greeting: String {
        switch Calendar.current.component(.hour, from: Date()) {
        case 5..<12: return "Good morning"
        case 12..<17: return "Good afternoon"
        default: return "Good evening"
        }
    }

    var body: some View {
        let _ = refreshToken
        let recent = SwaraDatabase.shared.recentlyPlayedIds(limit: 12).compactMap(library.song(id:))
        let most = SwaraDatabase.shared.mostPlayedIds(limit: 10).compactMap(library.song(id:))
        let recentlyAdded = Array(SwaraCore.SortOrder.dateAdded.apply(library.songs).prefix(12))

        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(greeting).font(.subheadline).foregroundStyle(p.textSecondary)
                    Text("Swara").font(.system(size: 40, weight: .heavy, design: .serif))
                        .foregroundStyle(LinearGradient(colors: [p.accent, p.textPrimary],
                                                        startPoint: .leading, endPoint: .trailing))
                }

                if library.songs.isEmpty {
                    ImportPromptCard(importing: $importing)
                } else {
                    PlayShuffleBar(songs: library.songs)
                }

                NavigationLink { PlaylistsView() } label: {
                    HStack {
                        Image(systemName: "music.note.list").foregroundStyle(p.accent)
                        Text("Playlists").font(.headline).foregroundStyle(p.textPrimary)
                        Spacer()
                        Image(systemName: "chevron.right").foregroundStyle(p.textTertiary)
                    }
                    .padding(16).glass()
                }

                if !recent.isEmpty {
                    SectionHeader(title: "Recently Played")
                    Carousel(songs: recent)
                }
                if !recentlyAdded.isEmpty {
                    SectionHeader(title: "Recently Added")
                    Carousel(songs: recentlyAdded)
                }
                if !most.isEmpty {
                    SectionHeader(title: "Most Played")
                    LazyVStack(spacing: 4) {
                        ForEach(most) { s in
                            SongRow(song: s)
                                .onTapGesture { player.play(most, startAt: most.firstIndex(of: s) ?? 0) }
                                .songMenu(s, $actions)
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 120)
        }
        .background(AtmosphereBackground())
        .refreshable { await library.refresh(); refreshToken += 1 }
        .onAppear { refreshToken += 1 }
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { importing = true } label: { Image(systemName: "plus") }
                    .accessibilityLabel("Import music")
                Button { showSettings = true } label: { Image(systemName: "gearshape") }
                    .accessibilityLabel("Settings")
            }
        }
        .sheet(isPresented: $showSettings) { SettingsView() }
        .musicImporter(isPresented: $importing)
        .songActions($actions)
    }
}

struct Carousel: View {
    @Environment(\.palette) var p
    @Environment(PlayerController.self) var player
    let songs: [Song]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(spacing: 14) {
                ForEach(Array(songs.enumerated()), id: \.element.id) { i, s in
                    Button { player.play(songs, startAt: i) } label: {
                        VStack(alignment: .leading, spacing: 6) {
                            ArtworkView(song: s, size: 132, radius: 16)
                                .shadow(color: .black.opacity(0.18), radius: 8, y: 4)
                            Text(s.title).font(.subheadline.weight(.semibold))
                                .foregroundStyle(player.current == s ? p.accent : p.textPrimary).lineLimit(1)
                            Text(s.artist).font(.caption).foregroundStyle(p.textSecondary).lineLimit(1)
                        }
                        .frame(width: 132)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

struct ImportPromptCard: View {
    @Environment(\.palette) var p
    @Binding var importing: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Image(systemName: "waveform.badge.plus").font(.largeTitle).foregroundStyle(p.accent)
            Text("Bring your music").font(.title3.bold()).foregroundStyle(p.textPrimary)
            Text("Import audio files from Files, or drop them into On My iPhone > Swara (Finder file sharing and AirDrop work too). Swara plays them fully offline.")
                .font(.subheadline).foregroundStyle(p.textSecondary)
            Button { importing = true } label: { Label("Import Music", systemImage: "square.and.arrow.down") }
                .buttonStyle(AccentButtonStyle())
        }
        .padding(20)
        .glass()
    }
}

extension View {
    /// Multi-select audio importer that copies files into the library.
    func musicImporter(isPresented: Binding<Bool>) -> some View {
        modifier(MusicImporter(isPresented: isPresented))
    }
}

private struct MusicImporter: ViewModifier {
    @Environment(LibraryStore.self) var library
    @Binding var isPresented: Bool

    func body(content: Content) -> some View {
        content.fileImporter(isPresented: $isPresented, allowedContentTypes: [.audio],
                             allowsMultipleSelection: true) { result in
            if case let .success(urls) = result {
                Task { await library.importFiles(urls) }
            }
        }
    }
}
