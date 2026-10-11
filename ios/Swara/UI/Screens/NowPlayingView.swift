import SwiftUI
import SwaraCore

struct NowPlayingView: View {
    @Environment(\.palette) var p
    @Environment(\.dismiss) var dismiss
    @Environment(PlayerController.self) var player
    @State var scrubbing: Double?
    @State var showQueue = false
    @State var dragOffset: CGFloat = 0
    @State var actions = SongActions()
    @State var noKaraoke = false

    var body: some View {
        ZStack {
            AtmosphereBackground()
            if let song = player.current {
                backdrop(song)
                VStack(spacing: 22) {
                    topBar
                    Spacer(minLength: 0)
                    ArtworkView(song: song, radius: 26)
                        .padding(.horizontal, 28)
                        .shadow(color: .black.opacity(0.35), radius: 30, y: 16)
                        .scaleEffect(player.isPlaying ? 1 : 0.92)
                        .animation(.spring(response: 0.5, dampingFraction: 0.75), value: player.isPlaying)
                    Spacer(minLength: 0)
                    titleRow(song)
                    scrubber
                    transport
                    secondaryControls
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 12)
            } else {
                EmptyStateView(symbol: "music.note", title: "Nothing playing", message: "Pick a song to start.")
            }
        }
        .offset(y: dragOffset)
        .gesture(DragGesture().onChanged { v in
            dragOffset = max(0, v.translation.height)
        }.onEnded { v in
            if v.translation.height > 140 || v.predictedEndTranslation.height > 400 { dismiss() }
            withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) { dragOffset = 0 }
        })
        .sheet(isPresented: $showQueue) { QueueView().modifier(ThemedRoot()) }
        .songActions($actions)
        .alert("No karaoke version found", isPresented: $noKaraoke) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Add a file such as \"Song (Karaoke)\" or \"Song (Instrumental)\" to your library, or turn on Korokae vocal removal.")
        }
        .modifier(ThemedRoot())
    }

    private func backdrop(_ song: Song) -> some View {
        Group {
            if let img = ArtworkCache.shared.image(songId: song.id) {
                Image(uiImage: img).resizable().scaledToFill()
                    .blur(radius: 60).opacity(0.45)
                    .overlay(p.background.opacity(0.35))
            }
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
    }

    private var topBar: some View {
        HStack {
            Button { dismiss() } label: { Image(systemName: "chevron.down").font(.title3.weight(.semibold)) }
                .accessibilityLabel("Close")
            Spacer()
            Text("NOW PLAYING").font(.caption.weight(.bold)).tracking(1.5).foregroundStyle(p.textSecondary)
            Spacer()
            if let s = player.current {
                Button { actions.sharing = s } label: { Image(systemName: "photo.on.rectangle").font(.title3) }
                    .accessibilityLabel("Share card")
                    .padding(.trailing, 6)
            }
            Menu {
                if let s = player.current {
                    SongMenu(song: s, addToPlaylist: $actions.addToPlaylist, editing: $actions.editing,
                             deleting: $actions.deleting, sharing: $actions.sharing)
                    Button { if !player.playKaraokeVersion() { noKaraoke = true } } label: {
                        Label("Karaoke Mode", systemImage: "music.mic")
                    }
                }
            } label: { Image(systemName: "ellipsis").font(.title3.weight(.semibold)).frame(width: 32, height: 32) }
            .accessibilityLabel("More")
        }
        .foregroundStyle(p.textPrimary)
        .padding(.top, 8)
    }

    private func titleRow(_ song: Song) -> some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 4) {
                Text(song.title).font(.title2.bold()).foregroundStyle(p.textPrimary).lineLimit(1)
                Text(song.artist).font(.body).foregroundStyle(p.textSecondary).lineLimit(1)
                if song.album != "Unknown Album" {
                    Text(song.album).font(.caption).foregroundStyle(p.textTertiary).lineLimit(1)
                }
            }
            Spacer()
            Button { player.toggleFavorite(song) } label: {
                Image(systemName: player.isFavorite(song) ? "heart.fill" : "heart")
                    .font(.title2)
                    .foregroundStyle(player.isFavorite(song) ? p.accent : p.textSecondary)
                    .symbolEffect(.bounce, value: player.isFavorite(song))
            }
            .accessibilityLabel(player.isFavorite(song) ? "Remove from favorites" : "Add to favorites")
        }
    }

    private var scrubber: some View {
        let dur = max(player.duration, 0.1)
        let value = scrubbing ?? min(player.position, dur)
        return VStack(spacing: 4) {
            Slider(value: Binding(get: { value }, set: { scrubbing = $0 }), in: 0...dur) { editing in
                if !editing, let s = scrubbing {
                    player.seek(to: s)
                    scrubbing = nil
                }
            }
            .accessibilityLabel("Playback position")
            .accessibilityValue(TimeFormatter.formatSeconds(value))
            HStack {
                Text(TimeFormatter.formatSeconds(value))
                Spacer()
                Text("-" + TimeFormatter.formatSeconds(max(0, dur - value)))
            }
            .font(.caption.monospacedDigit())
            .foregroundStyle(p.textTertiary)
        }
    }

    private var transport: some View {
        HStack {
            Button { player.skip(by: -15) } label: { Image(systemName: "gobackward.15").font(.title2) }
                .accessibilityLabel("Back 15 seconds")
            Spacer()
            Button { player.previous() } label: { Image(systemName: "backward.fill").font(.title) }
                .accessibilityLabel("Previous")
            Spacer()
            Button { player.togglePlayPause() } label: {
                Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: 34, weight: .bold))
                    .contentTransition(.symbolEffect(.replace))
                    .foregroundStyle(p.onAccent)
                    .frame(width: 78, height: 78)
                    .background(p.accent, in: Circle())
                    .shadow(color: p.accent.opacity(0.45), radius: 16, y: 6)
            }
            .accessibilityLabel(player.isPlaying ? "Pause" : "Play")
            Spacer()
            Button { player.next() } label: { Image(systemName: "forward.fill").font(.title) }
                .accessibilityLabel("Next")
            Spacer()
            Button { player.skip(by: 15) } label: { Image(systemName: "goforward.15").font(.title2) }
                .accessibilityLabel("Forward 15 seconds")
        }
        .foregroundStyle(p.textPrimary)
    }

    private var secondaryControls: some View {
        VStack(spacing: 10) {
            HStack {
                Button { player.toggleShuffle() } label: {
                    Image(systemName: "shuffle").foregroundStyle(player.shuffleEnabled ? p.accent : p.textSecondary)
                }
                .accessibilityLabel(player.shuffleEnabled ? "Shuffle on" : "Shuffle off")
                Spacer()
                Button { player.cycleRepeat() } label: {
                    Image(systemName: player.repeatMode == .one ? "repeat.1" : "repeat")
                        .foregroundStyle(player.repeatMode == .off ? p.textSecondary : p.accent)
                }
                .accessibilityLabel(["Repeat off", "Repeat one", "Repeat all"][player.repeatMode.rawValue])
                Spacer()
                Button { player.toggleKorokae() } label: {
                    Image(systemName: "music.mic")
                        .foregroundStyle(player.korokaeEnabled ? p.onAccent : p.textSecondary)
                        .padding(8)
                        .background(player.korokaeEnabled ? p.accent : .clear, in: Circle())
                }
                .accessibilityLabel(player.korokaeEnabled ? "Korokae Mode on" : "Korokae Mode off")
                Spacer()
                sleepMenu
                Spacer()
                Button { showQueue = true } label: {
                    Image(systemName: "list.bullet").foregroundStyle(p.textSecondary)
                }
                .accessibilityLabel("Queue")
            }
            .font(.title3)
            if let msg = player.korokaeMessage {
                Text(msg).font(.caption).foregroundStyle(p.textTertiary)
            } else if let left = player.sleepRemaining {
                Text("Sleep in \(TimeFormatter.formatSeconds(left))").font(.caption.monospacedDigit())
                    .foregroundStyle(p.accent)
            } else if player.sleepAfterSong {
                Text("Sleep at end of song").font(.caption).foregroundStyle(p.accent)
            }
        }
    }

    private var sleepMenu: some View {
        Menu {
            ForEach(SleepTimerPreset.minutes, id: \.self) { m in
                Button("\(m) minutes") { player.startSleepTimer(minutes: m) }
            }
            Button("End of song") { player.sleepAtEndOfSong() }
            if player.sleepTimerActive {
                Divider()
                Button("Turn Off", role: .destructive) { player.cancelSleepTimer() }
            }
        } label: {
            Image(systemName: player.sleepTimerActive ? "moon.zzz.fill" : "moon.zzz")
                .foregroundStyle(player.sleepTimerActive ? p.accent : p.textSecondary)
        }
        .accessibilityLabel("Sleep timer")
    }
}
