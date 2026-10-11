import SwiftUI
import SwaraCore

/// Song / album artwork with the Swara fallback glyph. Never tints user artwork.
struct ArtworkView: View {
    @Environment(\.palette) var p
    let image: UIImage?
    var size: CGFloat? = nil
    var radius: CGFloat = 10
    var symbol = "music.note"

    var body: some View {
        Color.clear
            .aspectRatio(1, contentMode: .fit)
            .frame(width: size, height: size)
            .overlay {
                if let image {
                    Image(uiImage: image).resizable().scaledToFill()
                } else {
                    ZStack {
                        LinearGradient(colors: [p.accent.opacity(0.35), p.surfaceVariant],
                                       startPoint: .topLeading, endPoint: .bottomTrailing)
                        Image(systemName: symbol)
                            .font(.system(size: (size ?? 120) * 0.38, weight: .medium))
                            .foregroundStyle(p.textPrimary.opacity(0.7))
                    }
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
            .accessibilityHidden(true)
    }
}

extension ArtworkView {
    init(song: Song?, size: CGFloat? = nil, radius: CGFloat = 10) {
        self.init(image: song.flatMap { ArtworkCache.shared.image(songId: $0.id) }, size: size, radius: radius)
    }

    init(album: Album, size: CGFloat? = nil, radius: CGFloat = 10) {
        self.init(image: ArtworkCache.shared.image(forAnyOf: album.songs), size: size, radius: radius, symbol: "square.stack")
    }
}

/// Row used by every song list. The playing track gets an accent wash, a glow stroke and a
/// bold title (Android's accessible current-playing indicator; visible while paused too).
struct SongRow: View {
    @Environment(\.palette) var p
    @Environment(PlayerController.self) var player
    let song: Song
    var showArtwork = true
    var subtitle: String? = nil

    var body: some View {
        let isCurrent = player.current == song
        HStack(spacing: 12) {
            if showArtwork {
                ArtworkView(song: song, size: 48, radius: 8)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(song.title)
                    .font(.body.weight(isCurrent ? .bold : .regular))
                    .foregroundStyle(isCurrent ? p.accent : p.textPrimary)
                    .lineLimit(1)
                Text(subtitle ?? "\(song.artist) - \(song.album)")
                    .font(.footnote)
                    .foregroundStyle(p.textSecondary)
                    .lineLimit(1)
            }
            Spacer(minLength: 8)
            if isCurrent {
                Image(systemName: player.isPlaying ? "waveform" : "pause.fill")
                    .symbolEffect(.variableColor.iterative, isActive: player.isPlaying)
                    .foregroundStyle(p.accent)
                    .accessibilityLabel(player.isPlaying ? "Now playing" : "Paused")
            } else {
                Text(song.formattedDuration)
                    .font(.footnote.monospacedDigit())
                    .foregroundStyle(p.textTertiary)
            }
        }
        .padding(.vertical, 6)
        .padding(.horizontal, 10)
        .background(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(isCurrent ? p.selected : .clear)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .strokeBorder(isCurrent ? p.accent.opacity(0.55) : .clear, lineWidth: 1)
        )
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityHint(TimeFormatter.formatAccessible(song.duration))
    }
}

/// Long-press menu shared by song rows.
struct SongMenu: View {
    @Environment(PlayerController.self) var player
    @Environment(LibraryStore.self) var library
    let song: Song
    @Binding var addToPlaylist: Song?
    @Binding var editing: Song?
    @Binding var deleting: Song?
    @Binding var sharing: Song?

    var body: some View {
        Button { player.playNext(song) } label: { Label("Play Next", systemImage: "text.insert") }
        Button { player.addToQueue([song]) } label: { Label("Add to Queue", systemImage: "text.append") }
        Button { addToPlaylist = song } label: { Label("Add to Playlist", systemImage: "music.note.list") }
        Button { player.toggleFavorite(song) } label: {
            player.isFavorite(song)
                ? Label("Remove from Favorites", systemImage: "heart.slash")
                : Label("Add to Favorites", systemImage: "heart")
        }
        Button { sharing = song } label: { Label("Share", systemImage: "square.and.arrow.up") }
        Button { editing = song } label: { Label("Edit Info", systemImage: "pencil") }
        Divider()
        Button(role: .destructive) { deleting = song } label: { Label("Delete from Device", systemImage: "trash") }
    }
}

/// Bundles the sheets/dialogs triggered from `SongMenu` so each list only adds one modifier.
struct SongActionsHost: ViewModifier {
    @Environment(LibraryStore.self) var library
    @Binding var addToPlaylist: Song?
    @Binding var editing: Song?
    @Binding var deleting: Song?
    @Binding var sharing: Song?

    func body(content: Content) -> some View {
        content
            .sheet(item: $addToPlaylist) { AddToPlaylistSheet(songs: [$0]) }
            .sheet(item: $editing) { MetadataEditorSheet(song: $0) }
            .sheet(item: $sharing) { ShareCardSheet(song: $0) }
            .confirmationDialog("Delete this song from your device?",
                                isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }),
                                titleVisibility: .visible, presenting: deleting) { song in
                Button("Delete \"\(song.title)\"", role: .destructive) {
                    Task { await library.delete(song) }
                }
            } message: { _ in
                Text("The file is removed from Swara's folder and from all playlists.")
            }
    }
}

/// State holder so lists can attach song actions with one line.
struct SongActions {
    var addToPlaylist: Song?
    var editing: Song?
    var deleting: Song?
    var sharing: Song?
}

extension View {
    func songActions(_ a: Binding<SongActions>) -> some View {
        modifier(SongActionsHost(addToPlaylist: a.addToPlaylist, editing: a.editing,
                                 deleting: a.deleting, sharing: a.sharing))
    }
}

struct SongContextMenu: ViewModifier {
    let song: Song
    @Binding var actions: SongActions

    func body(content: Content) -> some View {
        content.contextMenu {
            SongMenu(song: song, addToPlaylist: $actions.addToPlaylist, editing: $actions.editing,
                     deleting: $actions.deleting, sharing: $actions.sharing)
        }
    }
}

extension View {
    func songMenu(_ song: Song, _ actions: Binding<SongActions>) -> some View {
        modifier(SongContextMenu(song: song, actions: actions))
    }
}

/// Section title used across screens.
struct SectionHeader: View {
    @Environment(\.palette) var p
    let title: String

    var body: some View {
        Text(title)
            .font(.title3.weight(.bold))
            .foregroundStyle(p.textPrimary)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Play All / Shuffle pair (Play is the primary accent action, Shuffle secondary glass).
struct PlayShuffleBar: View {
    @Environment(PlayerController.self) var player
    let songs: [Song]

    var body: some View {
        HStack(spacing: 12) {
            Button { player.play(songs) } label: { Label("Play", systemImage: "play.fill") }
                .buttonStyle(AccentButtonStyle())
            Button { player.shufflePlay(songs) } label: { Label("Shuffle", systemImage: "shuffle") }
                .buttonStyle(GlassButtonStyle())
        }
        .disabled(songs.isEmpty)
    }
}

struct EmptyStateView: View {
    @Environment(\.palette) var p
    let symbol: String
    let title: String
    let message: String

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: symbol).font(.system(size: 44)).foregroundStyle(p.accent)
            Text(title).font(.headline).foregroundStyle(p.textPrimary)
            Text(message).font(.subheadline).foregroundStyle(p.textSecondary).multilineTextAlignment(.center)
        }
        .padding(32)
        .frame(maxWidth: .infinity)
    }
}
