import AVFoundation
import MediaToolbox
import SwaraCore
import os

/// Real-time Korokae vocal removal for AVPlayer via MTAudioProcessingTap
/// (the iOS counterpart of Android's `KorokaeAudioProcessor`).
///
/// Active only for 44.1/48 kHz stereo float PCM; other formats pass through untouched.
/// Enable/disable crossfades inside the engine, so toggling never rebuilds the player item.
final class KorokaeTapContext {
    private var lock = os_unfair_lock()
    private var engine: KorokaeEngine?
    private var model: KorokaeModel?
    private var enabled = false
    private var resetPending = false
    fileprivate var supported = false
    fileprivate var scratch: UnsafeMutablePointer<Float>?
    fileprivate var capacity = 0

    init(model: KorokaeModel?, enabled: Bool) {
        self.model = model
        self.enabled = enabled
    }

    /// Main thread.
    func setEnabled(_ on: Bool, model: KorokaeModel?) {
        os_unfair_lock_lock(&lock)
        if self.model == nil { self.model = model }
        enabled = on
        engine?.setMix(on ? 1 : 0)
        os_unfair_lock_unlock(&lock)
    }

    /// Main thread: call after seeks so stale overlap/GRU state is dropped.
    func requestReset() {
        os_unfair_lock_lock(&lock)
        resetPending = true
        os_unfair_lock_unlock(&lock)
    }

    fileprivate func prepare(maxFrames: Int, format: AudioStreamBasicDescription) {
        let isFloat = format.mFormatFlags & kAudioFormatFlagIsFloat != 0
        supported = format.mFormatID == kAudioFormatLinearPCM && isFloat
            && format.mBitsPerChannel == 32 && format.mChannelsPerFrame == 2
            && (format.mSampleRate == 44_100 || format.mSampleRate == 48_000)
        capacity = maxFrames
        scratch = .allocate(capacity: 2 * maxFrames)
    }

    fileprivate func unprepare() {
        scratch?.deallocate()
        scratch = nil
        capacity = 0
    }

    /// Render thread. Uses try-lock so it never blocks on the main thread.
    fileprivate func process(_ abl: UnsafeMutableAudioBufferListPointer, frames: Int) {
        guard supported, let s = scratch, frames <= capacity else { return }
        guard os_unfair_lock_trylock(&lock) else { return }
        defer { os_unfair_lock_unlock(&lock) }

        if engine == nil {
            guard enabled, let model else { return }
            let e = KorokaeEngine(model: model) // one-time allocation on first enable
            e.setMix(0)
            e.snapMix() // start dry so the first engagement fades in
            e.setMix(1)
            engine = e
        }
        guard let e = engine else { return }
        if resetPending { e.reset(); resetPending = false }

        let interleaved = abl.count == 1
        if interleaved {
            guard let p = abl[0].mData?.assumingMemoryBound(to: Float.self) else { return }
            e.process(p, p, frames: frames)
            return
        }
        guard abl.count >= 2,
              let l = abl[0].mData?.assumingMemoryBound(to: Float.self),
              let r = abl[1].mData?.assumingMemoryBound(to: Float.self) else { return }
        for i in 0..<frames { s[2 * i] = l[i]; s[2 * i + 1] = r[i] }
        e.process(s, s, frames: frames)
        for i in 0..<frames { l[i] = s[2 * i]; r[i] = s[2 * i + 1] }
    }
}

enum KorokaeTap {
    /// Builds an audio mix carrying the tap for the item's first audio track.
    static func makeAudioMix(for asset: AVAsset, context: KorokaeTapContext) async -> AVAudioMix? {
        guard let track = try? await asset.loadTracks(withMediaType: .audio).first else { return nil }
        var callbacks = MTAudioProcessingTapCallbacks(
            version: kMTAudioProcessingTapCallbacksVersion_0,
            clientInfo: UnsafeMutableRawPointer(Unmanaged.passRetained(context).toOpaque()),
            init: { _, clientInfo, storageOut in
                storageOut.pointee = clientInfo
            },
            finalize: { tap in
                Unmanaged<KorokaeTapContext>.fromOpaque(MTAudioProcessingTapGetStorage(tap)).release()
            },
            prepare: { tap, maxFrames, format in
                let ctx = Unmanaged<KorokaeTapContext>.fromOpaque(MTAudioProcessingTapGetStorage(tap)).takeUnretainedValue()
                ctx.prepare(maxFrames: Int(maxFrames), format: format.pointee)
            },
            unprepare: { tap in
                let ctx = Unmanaged<KorokaeTapContext>.fromOpaque(MTAudioProcessingTapGetStorage(tap)).takeUnretainedValue()
                ctx.unprepare()
            },
            process: { tap, frames, _, bufferList, framesOut, flagsOut in
                let status = MTAudioProcessingTapGetSourceAudio(tap, frames, bufferList, flagsOut, nil, framesOut)
                guard status == noErr else { return }
                let ctx = Unmanaged<KorokaeTapContext>.fromOpaque(MTAudioProcessingTapGetStorage(tap)).takeUnretainedValue()
                ctx.process(UnsafeMutableAudioBufferListPointer(bufferList), frames: Int(framesOut.pointee))
            })

        var tap: MTAudioProcessingTap?
        let err = MTAudioProcessingTapCreate(kCFAllocatorDefault, &callbacks,
                                             kMTAudioProcessingTapCreationFlag_PostEffects, &tap)
        guard err == noErr, let tap else {
            Unmanaged<KorokaeTapContext>.fromOpaque(callbacks.clientInfo!).release()
            return nil
        }
        let params = AVMutableAudioMixInputParameters(track: track)
        params.audioTapProcessor = tap
        let mix = AVMutableAudioMix()
        mix.inputParameters = [params]
        return mix
    }
}

/// Loads the bundled model once, off the main thread (mirrors `KorokaeModelProvider`).
actor KorokaeModelProvider {
    static let shared = KorokaeModelProvider()
    private var cached: KorokaeModel?
    private(set) var error: String?

    func model() -> KorokaeModel? {
        if let cached { return cached }
        guard let url = Bundle.main.url(forResource: KorokaeModel.resourceName, withExtension: "bin") else {
            error = "Karaoke model unavailable"
            return nil
        }
        do {
            cached = try KorokaeModel(contentsOf: url)
        } catch {
            self.error = "Karaoke model unavailable"
        }
        return cached
    }
}
