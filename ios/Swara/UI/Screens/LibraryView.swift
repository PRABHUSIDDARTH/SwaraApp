import SwiftUI
import SwaraCore

private enum LibrarySegment: String, CaseIterable, Identifiable {
    case songs = "Songs", albums = "Albums", artists = "Artists"
    var id: String { rawValue }
}

struct LibraryView: View {
    @Environment(\.palette) var p
    @Environment(LibraryStore.self) var library
    @Environment(AppSettings.self) var settings
    @State var segment: LibrarySegment = .songs
    @State var importing = false

    private var sortedSongs: [Song] {
        settings.sortOrder.apply(library.songs, customOrder: settings.customOrder)
    }

    var body: some View {
        VStack(spacing: 0) {
            Picker("Section", selection: $segment) {
                ForEach(LibrarySegment.allCases) { Text($0.rawValue).tag($0) }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16).padding(.vertical, 8)

            if library.songs.isEmpty {
                ScrollView {
                    ImportPromptCard(importing: $importing).padding(16)
                }
            } else {
                switch segment {
                case .songs: songsList
                case .albums: AlbumGrid(albums: library.albums)
                case .artists: ArtistList(artists: library.artists)
                }
            }
        }
        .background(AtmosphereBackground())
        .navigationTitle("Library")
        .toolbar {
            if segment == .songs && !library.songs.isEmpty {
                ToolbarItem(placement: .topBarTrailing) { sortMenu }
                if settings.sortOrder == .custom {
                    ToolbarItem(placement: .topBarLeading) { EditButton() }
                }
            }
        }
        .refreshable { await library.refresh() }
        .musicImporter(isPresented: $importing)
    }

    private var songsList: some View {
        let songs = sortedSongs
        let isCustom = settings.sortOrder == .custom
        let reorder: ((IndexSet, Int) -> Void)? = isCustom ? { from, to in
            var list = songs
            list.move(fromOffsets: from, toOffset: to)
            settings.customOrder = list.map(\.id)
        } : nil
        return SongListView(songs: songs, onMove: reorder) {
            HStack {
                Text(songCountText(songs.count)).font(.subheadline).foregroundStyle(p.textSecondary)
                Spacer()
                Text(settings.sortOrder.label).font(.subheadline).foregroundStyle(p.textTertiary)
            }
            PlayShuffleBar(songs: songs)
        }
    }

    private var sortMenu: some View {
        Menu {
            Picker("Sort by", selection: Bindable(settings).sortOrder) {
                ForEach(SortOrder.allCases, id: \.self) { Text($0.label).tag($0) }
            }
        } label: {
            Image(systemName: "arrow.up.arrow.down")
        }
        .accessibilityLabel("Sort songs")
    }
}

struct AlbumGrid: View {
    @Environment(\.palette) var p
    let albums: [Album]
    private let columns = [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)]

    var body: some View {
        ScrollView {
            LazyVGrid(columns: columns, spacing: 18) {
                ForEach(albums) { album in
                    NavigationLink { AlbumDetailView(album: album) } label: {
                        VStack(alignment: .leading, spacing: 6) {
                            ArtworkView(album: album, radius: 16)
                            Text(album.title).font(.subheadline.weight(.semibold))
                                .foregroundStyle(p.textPrimary).lineLimit(1)
                            Text(album.artist).font(.caption).foregroundStyle(p.textSecondary).lineLimit(1)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(16)
        }
        .contentMargins(.bottom, 96, for: .scrollContent)
    }
}

struct ArtistList: View {
    @Environment(\.palette) var p
    let artists: [Artist]

    var body: some View {
        List(artists) { artist in
            NavigationLink { ArtistDetailView(artist: artist) } label: {
                HStack(spacing: 12) {
                    ArtworkView(image: ArtworkCache.shared.image(forAnyOf: artist.songs), size: 48, radius: 24,
                                symbol: "person.fill")
                    VStack(alignment: .leading, spacing: 2) {
                        Text(artist.name).foregroundStyle(p.textPrimary).lineLimit(1)
                        Text("\(artist.albumCount) album\(artist.albumCount == 1 ? "" : "s") - \(songCountText(artist.songCount))")
                            .font(.footnote).foregroundStyle(p.textSecondary)
                    }
                }
            }
            .listRowBackground(Color.clear)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .contentMargins(.bottom, 96, for: .scrollContent)
    }
}

struct AlbumDetailView: View {
    let album: Album

    var body: some View {
        SongListView(songs: album.songs) {
            CollectionHeader(image: ArtworkCache.shared.image(forAnyOf: album.songs), symbol: "square.stack",
                             title: album.title,
                             subtitle: [album.artist, album.year > 0 ? String(album.year) : nil,
                                        songCountText(album.songCount)].compactMap { $0 }.joined(separator: " - "),
                             songs: album.songs)
        }
        .background(AtmosphereBackground())
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct ArtistDetailView: View {
    @Environment(\.palette) var p
    @Environment(LibraryStore.self) var library
    let artist: Artist

    var body: some View {
        // Re-resolve against the live library so edits/deletes are reflected.
        let songs = ArtistIdentity.songs(forArtist: artist.canonicalKey, in: library.songs)
        let albums = Album.build(from: songs)
        SongListView(songs: songs) {
            CollectionHeader(image: ArtworkCache.shared.image(forAnyOf: songs), symbol: "person.fill",
                             title: artist.name,
                             subtitle: "\(albums.count) album\(albums.count == 1 ? "" : "s") - \(songCountText(songs.count))",
                             songs: songs)
            if albums.count > 1 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        ForEach(albums) { a in
                            NavigationLink { AlbumDetailView(album: a) } label: {
                                VStack(alignment: .leading, spacing: 4) {
                                    ArtworkView(album: a, size: 110, radius: 12)
                                    Text(a.title).font(.caption.weight(.semibold))
                                        .foregroundStyle(p.textPrimary).lineLimit(1)
                                }
                                .frame(width: 110)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
        .background(AtmosphereBackground())
        .navigationBarTitleDisplayMode(.inline)
    }
}
