import PhotosUI
import SwiftUI
import SwaraCore

struct PlaylistsView: View {
    @Environment(\.palette) var p
    @Environment(LibraryStore.self) var library
    @State var creating = false
    @State var newName = ""
    @State var deleting: Playlist?

    var body: some View {
        let playlists = library.playlists()
        List {
            if playlists.isEmpty {
                EmptyStateView(symbol: "music.note.list", title: "No playlists",
                               message: "Create one here, or long-press any song and choose Add to Playlist.")
                    .listRowBackground(Color.clear).listRowSeparator(.hidden)
            }
            ForEach(playlists) { pl in
                NavigationLink { PlaylistDetailView(playlistId: pl.id) } label: {
                    HStack(spacing: 12) {
                        ArtworkView(image: ArtworkCache.shared.playlistImage(pl, songs: library.songs(in: pl)),
                                    size: 56, radius: 12, symbol: "music.note.list")
                        VStack(alignment: .leading, spacing: 2) {
                            Text(pl.name).foregroundStyle(p.textPrimary).lineLimit(1)
                            Text(songCountText(library.songs(in: pl).count))
                                .font(.footnote).foregroundStyle(p.textSecondary)
                        }
                    }
                }
                .listRowBackground(Color.clear)
                .swipeActions {
                    Button(role: .destructive) { deleting = pl } label: { Label("Delete", systemImage: "trash") }
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .contentMargins(.bottom, 96, for: .scrollContent)
        .background(AtmosphereBackground())
        .navigationTitle("Playlists")
        .toolbar {
            Button { newName = ""; creating = true } label: { Image(systemName: "plus") }
                .accessibilityLabel("New playlist")
        }
        .alert("New Playlist", isPresented: $creating) {
            TextField("Name", text: $newName)
            Button("Cancel", role: .cancel) {}
            Button("Create") {
                let name = newName.trimmingCharacters(in: .whitespaces)
                if !name.isEmpty { library.createPlaylist(name: name) }
            }
        }
        .confirmationDialog("Delete playlist?", isPresented: Binding(get: { deleting != nil },
                                                                    set: { if !$0 { deleting = nil } }),
                            presenting: deleting) { pl in
            Button("Delete \"\(pl.name)\"", role: .destructive) { library.deletePlaylist(pl.id) }
        } message: { _ in Text("Songs stay in your library.") }
    }
}

struct PlaylistDetailView: View {
    @Environment(\.palette) var p
    @Environment(\.dismiss) var dismiss
    @Environment(LibraryStore.self) var library
    let playlistId: Int64
    @State var renaming = false
    @State var name = ""
    @State var photo: PhotosPickerItem?
    @State var pickingPhoto = false

    var body: some View {
        if let pl = library.playlist(playlistId) {
            let songs = library.songs(in: pl)
            SongListView(songs: songs,
                         onMove: { from, to in
                             var list = songs
                             list.move(fromOffsets: from, toOffset: to)
                             library.updatePlaylist(pl.id) { $0.songIds = list.map(\.id) }
                         },
                         onDelete: { offsets in
                             let removed = Set(offsets.map { songs[$0].id })
                             library.updatePlaylist(pl.id) { $0.songIds.removeAll { removed.contains($0) } }
                         }) {
                CollectionHeader(image: ArtworkCache.shared.playlistImage(pl, songs: songs),
                                 symbol: "music.note.list", title: pl.name,
                                 subtitle: songCountText(songs.count), songs: songs)
            }
            .background(AtmosphereBackground())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) { EditButton() }
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button { name = pl.name; renaming = true } label: { Label("Rename", systemImage: "pencil") }
                        Button { pickingPhoto = true } label: { Label("Choose Artwork", systemImage: "photo") }
                        if pl.artworkFile != nil {
                            Button { library.updatePlaylist(pl.id) { $0.artworkFile = nil } } label: {
                                Label("Use Album Collage", systemImage: "square.grid.2x2")
                            }
                        }
                        Divider()
                        Button(role: .destructive) {
                            library.deletePlaylist(pl.id)
                            dismiss()
                        } label: { Label("Delete Playlist", systemImage: "trash") }
                    } label: { Image(systemName: "ellipsis.circle") }
                    .accessibilityLabel("Playlist options")
                }
            }
            .photosPicker(isPresented: $pickingPhoto, selection: $photo, matching: .images)
            .onChange(of: photo) { _, item in
                guard let item else { return }
                Task {
                    if let data = try? await item.loadTransferable(type: Data.self),
                       let file = ArtworkCache.shared.saveCustomPlaylistArtwork(data, playlistId: pl.id) {
                        library.updatePlaylist(pl.id) { $0.artworkFile = file }
                    }
                    photo = nil
                }
            }
            .alert("Rename Playlist", isPresented: $renaming) {
                TextField("Name", text: $name)
                Button("Cancel", role: .cancel) {}
                Button("Save") {
                    let n = name.trimmingCharacters(in: .whitespaces)
                    if !n.isEmpty { library.updatePlaylist(pl.id) { $0.name = n } }
                }
            }
        } else {
            EmptyStateView(symbol: "music.note.list", title: "Playlist not found", message: "It may have been deleted.")
        }
    }
}
