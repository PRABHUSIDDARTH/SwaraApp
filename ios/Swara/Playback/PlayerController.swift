import AVFoundation
import MediaPlayer
import SwaraCore
import UIKit

/// Single source of truth for playback (mirrors Android `SwaraPlaybackService` + `QueueManager`).
/// Owns the AVPlayer, the queue, shuffle/repeat, sleep timer, lock-screen controls,
/// play history and Korokae vocal removal.
@Observable
@MainActor
final class PlayerController {
    // Queue state. `queue` is the play order (already shuffled when shuffle is on).
    private(set) var queue: [Song] = []
    private(set) var index: Int = 0
    private(set) var isPlaying = false
    private(set) var position: Double = 0
    private(set) var duration: Double = 0
    private(set) var shuffleEnabled = false
    private(set) var repeatMode: RepeatMode = .off
    private(set) var korokaeEnabled = false
    private(set) var korokaeMessage: String?
    private(set) var sleepRemaining: Double?   // seconds; nil = off
    private(set) var sleepAfterSong = false
    private(set) var favoritesVersion = 0      // bump to refresh favorite UI

    var current: Song? { queue.indices.contains(index) ? queue[index] : nil }
    var upNext: [Song] { queue.indices.contains(index + 1) ? Array(queue[(index + 1)...]) : [] }

    private let player = AVPlayer()
    private weak var library: LibraryStore?
    private var unshuffled: [Song] = []
    private var lastShuffle: [Song]?
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    private var tapContext: KorokaeTapContext?
    private var sleepTask: Task<Void, Never>?
    private var historyRecorded = false
    private var loadToken = 0

    init() {
        player.automaticallyWaitsToMinimizeStalling = false
        configureSession()
        configureRemoteCommands()
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.25, preferredTimescale: 600),
                                                      queue: .main) { [weak self] t in
            MainActor.assumeIsolated { self?.tick(t.seconds) }
        }
        NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification, object: nil,
                                               queue: .main) { [weak self] n in
            MainActor.assumeIsolated { self?.handleInterruption(n) }
        }
        NotificationCenter.default.addObserver(forName: AVAudioSession.routeChangeNotification, object: nil,
                                               queue: .main) { [weak self] n in
            MainActor.assumeIsolated { self?.handleRouteChange(n) }
        }
        NotificationCenter.default.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil,
                                               queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.persistSession() }
        }
    }

    func attach(library: LibraryStore) { self.library = library }

    // MARK: - Queue

    func play(_ songs: [Song], startAt start: Int = 0) {
        let list = ShuffleEngine.deduplicate(songs)
        guard !list.isEmpty else { return }
        let startSong = list[min(max(0, start), list.count - 1)]
        unshuffled = list
        if shuffleEnabled {
            var rest = ShuffleEngine.shuffleSongs(list.filter { $0 != startSong }, previous: nil)
            rest.insert(startSong, at: 0)
            queue = rest
            index = 0
        } else {
            queue = list
            index = list.firstIndex(of: startSong) ?? 0
        }
        load(autoplay: true)
    }

    /// "Shuffle all": fresh permutation, never the same as the last shuffle.
    func shufflePlay(_ songs: [Song]) {
        let order = ShuffleEngine.shuffleSongs(songs, previous: lastShuffle)
        guard !order.isEmpty else { return }
        lastShuffle = order
        unshuffled = ShuffleEngine.deduplicate(songs)
        shuffleEnabled = true
        queue = order
        index = 0
        load(autoplay: true)
    }

    func playNext(_ song: Song) {
        if queue.isEmpty { play([song]); return }
        queue.removeAll { $0 == song && $0 != current }
        queue.insert(song, at: min(index + 1, queue.count))
        if !unshuffled.contains(song) { unshuffled.append(song) }
        persistSession()
    }

    func addToQueue(_ songs: [Song]) {
        if queue.isEmpty { play(songs); return }
        for s in songs where !queue.contains(s) {
            queue.append(s)
            unshuffled.append(s)
        }
        persistSession()
    }

    func removeFromQueue(at offsets: IndexSet) {
        // Offsets are relative to `upNext`.
        let absolute = IndexSet(offsets.map { $0 + index + 1 })
        let removed = absolute.map { queue[$0] }
        queue.remove(atOffsets: absolute)
        unshuffled.removeAll { removed.contains($0) }
        persistSession()
    }

    func moveInQueue(from source: IndexSet, to destination: Int) {
        var next = upNext
        next.move(fromOffsets: source, toOffset: destination)
        queue = Array(queue[...index]) + next
        persistSession()
    }

    func jump(toUpNext offset: Int) {
        index = index + 1 + offset
        load(autoplay: true)
    }

    func clearQueue() {
        player.pause()
        player.replaceCurrentItem(with: nil)
        queue = []
        unshuffled = []
        index = 0
        isPlaying = false
        position = 0
        duration = 0
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        persistSession()
    }

    // MARK: - Transport

    func togglePlayPause() { isPlaying ? pause() : resume() }

    func resume() {
        guard current != nil else { return }
        if player.currentItem == nil { load(autoplay: true); return }
        try? AVAudioSession.sharedInstance().setActive(true)
        player.play()
        isPlaying = true
        updateNowPlaying()
    }

    func pause() {
        player.pause()
        isPlaying = false
        updateNowPlaying()
        persistSession()
    }

    func next() { advance(userInitiated: true) }

    /// > 3 s into the track restarts it, otherwise goes to the previous track (Android behaviour).
    func previous() {
        if position > 3 || index == 0 {
            seek(to: 0)
        } else {
            index -= 1
            load(autoplay: isPlaying)
        }
    }

    func seek(to seconds: Double) {
        let t = max(0, min(seconds, duration > 0 ? duration : seconds))
        tapContext?.requestReset()
        player.seek(to: CMTime(seconds: t, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
        position = t
        updateNowPlaying()
    }

    func skip(by seconds: Double) { seek(to: position + seconds) }

    func toggleShuffle() {
        shuffleEnabled.toggle()
        guard let cur = current else { return }
        if shuffleEnabled {
            let rest = ShuffleEngine.shuffleSongs(unshuffled.filter { $0 != cur }, previous: nil)
            queue = [cur] + rest
            index = 0
        } else {
            queue = unshuffled.isEmpty ? queue : unshuffled
            index = queue.firstIndex(of: cur) ?? 0
        }
        updateRemoteCommandState()
        persistSession()
    }

    func cycleRepeat() {
        repeatMode = repeatMode.next
        updateRemoteCommandState()
    }

    private func advance(userInitiated: Bool) {
        guard !queue.isEmpty else { return }
        if sleepAfterSong && !userInitiated {
            cancelSleepTimer()
            pause()
            seek(to: 0)
            return
        }
        if repeatMode == .one && !userInitiated {
            seek(to: 0)
            player.play()
            historyRecorded = false
            return
        }
        if index + 1 < queue.count {
            index += 1
        } else if repeatMode == .all {
            index = 0
        } else if userInitiated {
            return // no next item (Media3 semantics)
        } else {
            // End of queue: stop at the start of the last track.
            pause()
            seek(to: 0)
            return
        }
        load(autoplay: true)
    }

    // MARK: - Loading

    private func load(autoplay: Bool, startAt: Double = 0) {
        guard let song = current, let library else { return }
        loadToken += 1
        let token = loadToken
        historyRecorded = false
        let url = library.url(for: song)
        let asset = AVURLAsset(url: url)
        let item = AVPlayerItem(asset: asset)
        let ctx = KorokaeTapContext(model: nil, enabled: korokaeEnabled)
        tapContext = ctx

        if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
        endObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime,
                                                             object: item, queue: .main) { [weak self] _ in
            MainActor.assumeIsolated { self?.advance(userInitiated: false) }
        }
        player.replaceCurrentItem(with: item)
        duration = Double(song.duration) / 1000
        position = startAt
        if startAt > 0 { player.seek(to: CMTime(seconds: startAt, preferredTimescale: 600)) }
        if autoplay {
            try? AVAudioSession.sharedInstance().setActive(true)
            player.play()
        }
        isPlaying = autoplay
        updateNowPlaying()
        persistSession()

        // Attach the Korokae tap asynchronously; audio starts immediately and the tap
        // joins as soon as the track is loaded (no audible gap when disabled).
        Task {
            var model: KorokaeModel?
            if korokaeEnabled { model = await KorokaeModelProvider.shared.model() }
            ctx.setEnabled(korokaeEnabled, model: model)
            let mix = await KorokaeTap.makeAudioMix(for: asset, context: ctx)
            guard token == loadToken else { return }
            item.audioMix = mix
            if let d = try? await asset.load(.duration), d.isNumeric, token == loadToken {
                duration = d.seconds
                updateNowPlaying()
            }
        }
    }

    private func tick(_ seconds: Double) {
        guard seconds.isFinite else { return }
        position = seconds
        if let song = current, !historyRecorded, isPlaying {
            let durMs = Int64((duration > 0 ? duration : Double(song.duration) / 1000) * 1000)
            if PlayHistoryRule.qualifies(positionMs: Int64(seconds * 1000), durationMs: durMs) {
                SwaraDatabase.shared.maybeRecordPlay(songId: song.id, positionMs: Int64(seconds * 1000), durationMs: durMs)
                historyRecorded = true
            }
        }
    }

    // MARK: - Korokae (vocal removal)

    func toggleKorokae() {
        let on = !korokaeEnabled
        korokaeEnabled = on
        korokaeMessage = nil
        Task {
            var model: KorokaeModel?
            if on { model = await KorokaeModelProvider.shared.model() }
            if on && model == nil {
                korokaeEnabled = false
                korokaeMessage = await KorokaeModelProvider.shared.error ?? "Karaoke model unavailable"
                return
            }
            tapContext?.setEnabled(on, model: model)
        }
    }

    /// Plays a local karaoke/instrumental version of the current song, if one exists.
    func playKaraokeVersion() -> Bool {
        guard let cur = current, let library,
              let match = KaraokeMatcher.findBestMatch(cur, in: library.songs) else { return false }
        queue.insert(match, at: index + 1)
        index += 1
        load(autoplay: true)
        return true
    }

    // MARK: - Favorites

    func isFavorite(_ song: Song) -> Bool {
        _ = favoritesVersion
        return SwaraDatabase.shared.isFavorite(song.id)
    }

    func toggleFavorite(_ song: Song) {
        SwaraDatabase.shared.toggleFavorite(song.id)
        favoritesVersion += 1
        updateRemoteCommandState()
    }

    // MARK: - Sleep timer

    func startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        let end = Date().addingTimeInterval(Double(minutes) * 60)
        sleepRemaining = Double(minutes) * 60
        sleepTask = Task { [weak self] in
            while !Task.isCancelled {
                let left = end.timeIntervalSinceNow
                guard let self else { return }
                if left <= 0 {
                    self.pause()
                    self.sleepRemaining = nil
                    return
                }
                self.sleepRemaining = left
                try? await Task.sleep(for: .seconds(1))
            }
        }
    }

    func sleepAtEndOfSong() {
        cancelSleepTimer()
        sleepAfterSong = true
    }

    func cancelSleepTimer() {
        sleepTask?.cancel()
        sleepTask = nil
        sleepRemaining = nil
        sleepAfterSong = false
    }

    var sleepTimerActive: Bool { sleepRemaining != nil || sleepAfterSong }

    // MARK: - Session persistence

    func persistSession() {
        SwaraDatabase.shared.saveQueue(ids: queue.map(\.id), index: index, positionSec: position)
    }

    func restoreSession() {
        guard queue.isEmpty, let library else { return }
        let saved = SwaraDatabase.shared.savedQueue()
        let songs = saved.ids.compactMap { library.song(id: $0) }
        guard !songs.isEmpty else { return }
        queue = songs
        unshuffled = songs
        index = min(saved.index, songs.count - 1)
        load(autoplay: false, startAt: saved.positionSec)
    }

    // MARK: - System integration

    private func configureSession() {
        let s = AVAudioSession.sharedInstance()
        try? s.setCategory(.playback, mode: .default, policy: .longFormAudio)
    }

    private func handleInterruption(_ n: Notification) {
        guard let raw = n.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: raw) else { return }
        if type == .began {
            isPlaying = false
            updateNowPlaying()
        } else if let o = n.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt,
                  AVAudioSession.InterruptionOptions(rawValue: o).contains(.shouldResume) {
            resume()
        }
    }

    /// Headphones unplugged / Bluetooth disconnected: pause (like Android's becoming-noisy).
    private func handleRouteChange(_ n: Notification) {
        guard let raw = n.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt,
              AVAudioSession.RouteChangeReason(rawValue: raw) == .oldDeviceUnavailable else { return }
        pause()
    }

    private func configureRemoteCommands() {
        let c = MPRemoteCommandCenter.shared()
        c.playCommand.addTarget { [weak self] _ in self?.resume(); return .success }
        c.pauseCommand.addTarget { [weak self] _ in self?.pause(); return .success }
        c.togglePlayPauseCommand.addTarget { [weak self] _ in self?.togglePlayPause(); return .success }
        c.nextTrackCommand.addTarget { [weak self] _ in self?.next(); return .success }
        c.previousTrackCommand.addTarget { [weak self] _ in self?.previous(); return .success }
        c.changePlaybackPositionCommand.addTarget { [weak self] e in
            guard let e = e as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            self?.seek(to: e.positionTime)
            return .success
        }
        c.changeShuffleModeCommand.addTarget { [weak self] _ in self?.toggleShuffle(); return .success }
        c.changeRepeatModeCommand.addTarget { [weak self] _ in self?.cycleRepeat(); return .success }
        c.likeCommand.localizedTitle = "Favorite"
        c.likeCommand.addTarget { [weak self] _ in
            guard let self, let s = self.current else { return .noSuchContent }
            self.toggleFavorite(s)
            return .success
        }
    }

    private func updateRemoteCommandState() {
        let c = MPRemoteCommandCenter.shared()
        c.changeShuffleModeCommand.currentShuffleType = shuffleEnabled ? .items : .off
        let repeatTypes: [MPRepeatType] = [.off, .one, .all]
        c.changeRepeatModeCommand.currentRepeatType = repeatTypes[repeatMode.rawValue]
        if let s = current { c.likeCommand.isActive = SwaraDatabase.shared.isFavorite(s.id) }
    }

    private func updateNowPlaying() {
        guard let s = current else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: s.title,
            MPMediaItemPropertyArtist: s.artist,
            MPMediaItemPropertyAlbumTitle: s.album,
            MPMediaItemPropertyPlaybackDuration: duration,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: position,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1.0 : 0.0,
            MPNowPlayingInfoPropertyPlaybackQueueIndex: index,
            MPNowPlayingInfoPropertyPlaybackQueueCount: queue.count,
        ]
        if let img = ArtworkCache.shared.image(songId: s.id) {
            info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: img.size) { _ in img }
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        MPNowPlayingInfoCenter.default().playbackState = isPlaying ? .playing : .paused
        updateRemoteCommandState()
    }
}
