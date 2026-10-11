import SwiftUI
import SwaraCore

enum RootTab: Hashable { case home, library, search, favorites }

/// Four tabs (Home, Library, Search, Favorites) like Android's bottom nav, with a floating
/// glass mini player that expands into Now Playing.
struct RootView: View {
    @Environment(PlayerController.self) var player
    @State var tab: RootTab = .home
    @State var showNowPlaying = false

    var body: some View {
        TabView(selection: $tab) {
            NavigationStack { HomeView() }
                .tabItem { Label("Home", systemImage: "house.fill") }.tag(RootTab.home)
            NavigationStack { LibraryView() }
                .tabItem { Label("Library", systemImage: "square.stack.fill") }.tag(RootTab.library)
            NavigationStack { SearchView() }
                .tabItem { Label("Search", systemImage: "magnifyingglass") }.tag(RootTab.search)
            NavigationStack { FavoritesView() }
                .tabItem { Label("Favorites", systemImage: "heart.fill") }.tag(RootTab.favorites)
        }
        .overlay(alignment: .bottom) {
            if player.current != nil {
                MiniPlayerView { showNowPlaying = true }
                    .padding(.horizontal, 10)
                    .padding(.bottom, 54) // float just above the tab bar
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.spring(response: 0.4, dampingFraction: 0.85), value: player.current != nil)
        .fullScreenCover(isPresented: $showNowPlaying) { NowPlayingView() }
        .modifier(ThemedRoot())
    }
}

struct MiniPlayerView: View {
    @Environment(\.palette) var p
    @Environment(PlayerController.self) var player
    let expand: () -> Void

    var body: some View {
        if let song = player.current {
            VStack(spacing: 0) {
                HStack(spacing: 12) {
                    ArtworkView(song: song, size: 44, radius: 10)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(song.title).font(.subheadline.weight(.semibold)).foregroundStyle(p.textPrimary).lineLimit(1)
                        Text(song.artist).font(.caption).foregroundStyle(p.textSecondary).lineLimit(1)
                    }
                    Spacer()
                    Button { player.togglePlayPause() } label: {
                        Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                            .font(.title3).frame(width: 40, height: 40)
                            .contentTransition(.symbolEffect(.replace))
                    }
                    .accessibilityLabel(player.isPlaying ? "Pause" : "Play")
                    Button { player.next() } label: {
                        Image(systemName: "forward.fill").font(.title3).frame(width: 40, height: 40)
                    }
                    .accessibilityLabel("Next")
                }
                .foregroundStyle(p.textPrimary)
                .padding(.horizontal, 10).padding(.top, 8).padding(.bottom, 6)

                GeometryReader { g in
                    let frac = player.duration > 0 ? min(1, player.position / player.duration) : 0
                    Capsule().fill(p.stroke.opacity(0.5))
                        .overlay(alignment: .leading) {
                            Capsule().fill(p.accent).frame(width: g.size.width * frac)
                        }
                }
                .frame(height: 3)
                .padding(.horizontal, 14).padding(.bottom, 8)
            }
            .glass(radius: 18)
            .shadow(color: .black.opacity(0.2), radius: 12, y: 4)
            .contentShape(Rectangle())
            .onTapGesture(perform: expand)
            .gesture(DragGesture(minimumDistance: 20).onEnded { v in
                if v.translation.height < -30 { expand() }
                else if v.translation.width < -60 { player.next() }
                else if v.translation.width > 60 { player.previous() }
            })
        }
    }
}
