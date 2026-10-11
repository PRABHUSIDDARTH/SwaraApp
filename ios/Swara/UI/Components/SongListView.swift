import SwiftUI
import SwaraCore

/// Plain, themed song list shared by every screen. Tapping a row plays the list from that
/// row; long-press opens the song menu. Optional reorder (Edit mode) and swipe-to-remove.
struct SongListView<Header: View>: View {
    @Environment(PlayerController.self) var player
    @State var actions = SongActions()

    let songs: [Song]
    var onMove: ((IndexSet, Int) -> Void)?
    var onDelete: ((IndexSet) -> Void)?
    var deleteLabel = "Remove"
    let header: Header

    init(songs: [Song], onMove: ((IndexSet, Int) -> Void)? = nil, onDelete: ((IndexSet) -> Void)? = nil,
         deleteLabel: String = "Remove", @ViewBuilder header: () -> Header) {
        self.songs = songs
        self.onMove = onMove
        self.onDelete = onDelete
        self.deleteLabel = deleteLabel
        self.header = header()
    }

    var body: some View {
        List {
            header
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
                .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
            ForEach(Array(songs.enumerated()), id: \.element.id) { i, song in
                Button { player.play(songs, startAt: i) } label: { SongRow(song: song) }
                    .buttonStyle(.plain)
                    .songMenu(song, $actions)
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                    .listRowInsets(EdgeInsets(top: 1, leading: 6, bottom: 1, trailing: 6))
            }
            .onMove { onMove?($0, $1) }
            .moveDisabled(onMove == nil)
            .onDelete { onDelete?($0) }
            .deleteDisabled(onDelete == nil)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .contentMargins(.bottom, 96, for: .scrollContent) // room for the mini player
        .songActions($actions)
    }
}

extension SongListView where Header == EmptyView {
    init(songs: [Song], onMove: ((IndexSet, Int) -> Void)? = nil, onDelete: ((IndexSet) -> Void)? = nil) {
        self.init(songs: songs, onMove: onMove, onDelete: onDelete) { EmptyView() }
    }
}

/// Large header used by album / artist / playlist detail screens.
struct CollectionHeader: View {
    @Environment(\.palette) var p
    let image: UIImage?
    var symbol = "music.note"
    let title: String
    let subtitle: String
    let songs: [Song]

    var body: some View {
        VStack(spacing: 14) {
            ArtworkView(image: image, size: 220, radius: 22, symbol: symbol)
                .shadow(color: p.accent.opacity(0.25), radius: 24, y: 10)
            VStack(spacing: 4) {
                Text(title).font(.title2.bold()).foregroundStyle(p.textPrimary)
                    .multilineTextAlignment(.center)
                Text(subtitle).font(.subheadline).foregroundStyle(p.textSecondary)
                    .multilineTextAlignment(.center)
            }
            PlayShuffleBar(songs: songs)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
    }
}

func songCountText(_ n: Int) -> String { n == 1 ? "1 song" : "\(n) songs" }
