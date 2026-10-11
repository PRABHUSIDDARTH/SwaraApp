import SwiftUI
import SwaraCore

struct FavoritesView: View {
    @Environment(LibraryStore.self) var library
    @Environment(PlayerController.self) var player

    var body: some View {
        let _ = player.favoritesVersion // re-read when favorites change
        let songs = SwaraDatabase.shared.favoriteIds().compactMap(library.song(id:))
        Group {
            if songs.isEmpty {
                EmptyStateView(symbol: "heart", title: "No favorites yet",
                               message: "Tap the heart in Now Playing or long-press a song to add it here.")
                    .frame(maxHeight: .infinity)
            } else {
                SongListView(songs: songs, onDelete: { offsets in
                    for i in offsets { player.toggleFavorite(songs[i]) }
                }) {
                    PlayShuffleBar(songs: songs)
                }
            }
        }
        .background(AtmosphereBackground())
        .navigationTitle("Favorites")
    }
}
