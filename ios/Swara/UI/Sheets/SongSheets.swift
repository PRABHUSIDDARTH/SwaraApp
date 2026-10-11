import SwiftUI
import SwaraCore

/// Pick (or create) a playlist for one or more songs.
struct AddToPlaylistSheet: View {
    @Environment(\.palette) var p
    @Environment(\.dismiss) var dismiss
    @Environment(LibraryStore.self) var library
    let songs: [Song]
    @State var newName = ""
    @State var creating = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Button { creating = true } label: { Label("New Playlist", systemImage: "plus") }
                        .listRowBackground(Color.clear)
                }
                Section("Playlists") {
                    ForEach(library.playlists()) { pl in
                        Button {
                            library.add(songs, toPlaylist: pl.id)
                            dismiss()
                        } label: {
                            HStack {
                                Text(pl.name).foregroundStyle(p.textPrimary)
                                Spacer()
                                Text("\(pl.songIds.count)").foregroundStyle(p.textTertiary)
                            }
                        }
                        .listRowBackground(Color.clear)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(AtmosphereBackground())
            .navigationTitle(songs.count == 1 ? "Add to Playlist" : "Add \(songs.count) Songs")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { Button("Cancel") { dismiss() } }
            .alert("New Playlist", isPresented: $creating) {
                TextField("Name", text: $newName)
                Button("Cancel", role: .cancel) {}
                Button("Create") {
                    let name = newName.trimmingCharacters(in: .whitespaces)
                    if !name.isEmpty { library.createPlaylist(name: name, songs: songs) }
                    dismiss()
                }
            }
        }
        .modifier(ThemedRoot())
        .presentationDetents([.medium, .large])
    }
}

/// Edits title / artist / album. Stored as Swara overrides (files are not re-tagged).
struct MetadataEditorSheet: View {
    @Environment(\.dismiss) var dismiss
    @Environment(LibraryStore.self) var library
    let song: Song
    @State var title: String
    @State var artist: String
    @State var album: String

    init(song: Song) {
        self.song = song
        _title = State(initialValue: song.title)
        _artist = State(initialValue: song.artist)
        _album = State(initialValue: song.album)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Title", text: $title)
                    TextField("Artist", text: $artist)
                    TextField("Album", text: $album)
                } footer: {
                    Text("Changes are kept in Swara and shown across the app. The audio file itself is not modified.")
                }
            }
            .scrollContentBackground(.hidden)
            .background(AtmosphereBackground())
            .navigationTitle("Edit Info")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Save") {
                        library.updateMetadata(song, title: title.trimmingCharacters(in: .whitespaces),
                                               artist: artist.trimmingCharacters(in: .whitespaces),
                                               album: album.trimmingCharacters(in: .whitespaces))
                        dismiss()
                    }
                    .disabled(title.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
        .modifier(ThemedRoot())
        .presentationDetents([.medium, .large])
    }
}

/// Renders a shareable card (artwork + title + artist) and offers it via the system share sheet.
struct ShareCardSheet: View {
    @Environment(\.dismiss) var dismiss
    let song: Song

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                ShareCard(song: song)
                    .shadow(color: .black.opacity(0.3), radius: 20, y: 10)
                if let rendered = renderedImage {
                    ShareLink(item: Image(uiImage: rendered),
                              preview: SharePreview(song.title, image: Image(uiImage: rendered))) {
                        Label("Share", systemImage: "square.and.arrow.up")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(AccentButtonStyle())
                }
            }
            .padding(24)
            .navigationTitle("Share")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { Button("Done") { dismiss() } }
        }
        .presentationDetents([.medium, .large])
    }

    @MainActor private var renderedImage: UIImage? {
        let renderer = ImageRenderer(content: ShareCard(song: song))
        renderer.scale = 3
        return renderer.uiImage
    }
}

private struct ShareCard: View {
    let song: Song

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            ArtworkView(song: song, radius: 18)
                .frame(width: 260)
            Text(song.title).font(.title2.bold()).foregroundStyle(.white).lineLimit(2)
            Text(song.artist).font(.body).foregroundStyle(.white.opacity(0.75)).lineLimit(1)
            Text("Swara").font(.caption.weight(.semibold)).foregroundStyle(.white.opacity(0.5))
        }
        .padding(22)
        .frame(width: 304, alignment: .leading)
        .background(
            LinearGradient(colors: [Color(red: 0.16, green: 0.10, blue: 0.28), .black],
                           startPoint: .top, endPoint: .bottom),
            in: RoundedRectangle(cornerRadius: 28, style: .continuous)
        )
    }
}
