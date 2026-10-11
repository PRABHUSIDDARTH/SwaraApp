import SwiftUI
import SwaraCore

/// Multi-attribute search: every query word must match a song's title, artist or album
/// (case/diacritic-insensitive). Albums, artists and playlists are matched by name.
struct SearchView: View {
    @Environment(\.palette) var p
    @Environment(LibraryStore.self) var library
    @Environment(PlayerController.self) var player
    @State var query = ""
    @State var actions = SongActions()

    static func matches(_ fields: [String], _ words: [String]) -> Bool {
        words.allSatisfy { w in
            fields.contains { $0.range(of: w, options: [.caseInsensitive, .diacriticInsensitive]) != nil }
        }
    }

    var body: some View {
        let words = query.split(separator: " ").map(String.init)
        let songs = words.isEmpty ? [] : library.songs.filter { Self.matches([$0.title, $0.artist, $0.album], words) }
        let albums = words.isEmpty ? [] : library.albums.filter { Self.matches([$0.title, $0.artist], words) }
        let artists = words.isEmpty ? [] : library.artists.filter { Self.matches([$0.name], words) }
        let playlists = words.isEmpty ? [] : library.playlists().filter { Self.matches([$0.name], words) }

        List {
            if words.isEmpty {
                EmptyStateView(symbol: "magnifyingglass", title: "Search your library",
                               message: "Find songs, albums, artists and playlists.")
                    .listRowBackground(Color.clear).listRowSeparator(.hidden)
            } else if songs.isEmpty && albums.isEmpty && artists.isEmpty && playlists.isEmpty {
                EmptyStateView(symbol: "questionmark.circle", title: "No results",
                               message: "Nothing matches \"\(query)\".")
                    .listRowBackground(Color.clear).listRowSeparator(.hidden)
            }
            if !songs.isEmpty {
                Section("Songs") {
                    ForEach(Array(songs.prefix(50).enumerated()), id: \.element.id) { i, s in
                        Button { player.play(songs, startAt: i) } label: { SongRow(song: s) }
                            .buttonStyle(.plain)
                            .songMenu(s, $actions)
                    }
                }
            }
            if !artists.isEmpty {
                Section("Artists") {
                    ForEach(artists.prefix(20)) { a in
                        NavigationLink(a.name) { ArtistDetailView(artist: a) }
                    }
                }
            }
            if !albums.isEmpty {
                Section("Albums") {
                    ForEach(albums.prefix(20)) { a in
                        NavigationLink { AlbumDetailView(album: a) } label: {
                            HStack(spacing: 12) {
                                ArtworkView(album: a, size: 44, radius: 8)
                                VStack(alignment: .leading) {
                                    Text(a.title).foregroundStyle(p.textPrimary).lineLimit(1)
                                    Text(a.artist).font(.footnote).foregroundStyle(p.textSecondary).lineLimit(1)
                                }
                            }
                        }
                    }
                }
            }
            if !playlists.isEmpty {
                Section("Playlists") {
                    ForEach(playlists) { pl in
                        NavigationLink(pl.name) { PlaylistDetailView(playlistId: pl.id) }
                    }
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .contentMargins(.bottom, 96, for: .scrollContent)
        .background(AtmosphereBackground())
        .navigationTitle("Search")
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always),
                    prompt: "Songs, artists, albums, playlists")
        .songActions($actions)
    }
}
