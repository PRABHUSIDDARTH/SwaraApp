import Foundation

/// Streaming, causal vocal remover (direct port of Android `KorokaeEngine`).
///
/// Feed interleaved stereo float samples to `process`; output is delayed by exactly
/// `nFft - 1` frames. The mask network runs on the mid channel's log-magnitude
/// spectrum and is applied to both channels, preserving stereo image and phase.
/// `mix` (0...1) crossfades original (0) and instrumental (1) per hop, click-free.
///
/// Allocation-free per frame. Not thread-safe: use from the audio render thread only.
public final class KorokaeEngine {
    private let m: KorokaeModel
    private let n: Int, hop: Int, bins: Int, hidden: Int

    private var window: [Float], cosT: [Float], sinT: [Float], bitrev: [Int]
    private var inL: [Float], inR: [Float]
    private var filled = 0
    private var olaL: [Float], olaR: [Float]
    private var outBuf: [Float]
    private var outRead = 0, outAvail = 0
    private var reL: [Float], imL: [Float], reR: [Float], imR: [Float], reM: [Float], imM: [Float]
    private var feat: [Float], x: [Float], h: [[Float]], gi: [Float], gh: [Float], mask: [Float]

    private var mix: Float = 1
    private var targetMix: Float = 1

    public init(model: KorokaeModel) {
        m = model; n = model.nFft; hop = model.hop; bins = model.bins; hidden = model.hidden
        let n = model.nFft
        window = (0..<n).map { Float((0.5 - 0.5 * cos(2 * Double.pi * Double($0) / Double(n))).squareRoot()) }
        cosT = (0..<n / 2).map { Float(cos(-2 * Double.pi * Double($0) / Double(n))) }
        sinT = (0..<n / 2).map { Float(sin(-2 * Double.pi * Double($0) / Double(n))) }
        let bits = n.trailingZeroBitCount
        bitrev = (0..<n).map { i in
            var r = 0
            for b in 0..<bits where i & (1 << b) != 0 { r |= 1 << (bits - 1 - b) }
            return r
        }
        let z = [Float](repeating: 0, count: n)
        inL = z; inR = z; olaL = z; olaR = z
        reL = z; imL = z; reR = z; imR = z; reM = z; imM = z
        outBuf = [Float](repeating: 0, count: 2 * model.hop)
        feat = [Float](repeating: 0, count: model.bins)
        mask = feat
        x = [Float](repeating: 0, count: model.hidden)
        h = Array(repeating: x, count: model.layers)
        gi = [Float](repeating: 0, count: 3 * model.hidden)
        gh = gi
        reset()
    }

    /// Output latency in sample frames.
    public var latencyFrames: Int { n - 1 }

    /// Wet amount target (0 = original, 1 = instrumental); ramped per hop.
    public func setMix(_ value: Float) { targetMix = min(1, max(0, value)) }
    public var currentTargetMix: Float { targetMix }
    /// Jumps the wet amount immediately (e.g. on seek).
    public func snapMix() { mix = targetMix }

    public func reset() {
        for i in 0..<n { inL[i] = 0; inR[i] = 0; olaL[i] = 0; olaR[i] = 0 }
        for l in 0..<h.count { for j in 0..<hidden { h[l][j] = 0 } }
        filled = n - hop // pre-roll so the first frame fires after one hop
        outRead = 0
        outAvail = 0
        mix = targetMix
    }

    /// Processes `frames` interleaved stereo frames. `input` and `output` may alias.
    public func process(_ input: UnsafePointer<Float>, _ output: UnsafeMutablePointer<Float>, frames: Int) {
        for f in 0..<frames {
            let l = input[2 * f], r = input[2 * f + 1]
            inL[filled] = l
            inR[filled] = r
            filled += 1
            if filled == n {
                runFrame()
                shiftLeft(&inL, by: hop)
                shiftLeft(&inR, by: hop)
                filled = n - hop
            }
            if outAvail > 0 {
                output[2 * f] = outBuf[2 * outRead]
                output[2 * f + 1] = outBuf[2 * outRead + 1]
                outRead += 1
                outAvail -= 1
            } else {
                output[2 * f] = 0
                output[2 * f + 1] = 0
            }
        }
    }

    /// Array convenience (tests); offsets are in frames.
    public func process(_ input: [Float], inFrame: Int = 0, into output: inout [Float],
                        outFrame: Int = 0, frames: Int) {
        input.withUnsafeBufferPointer { ib in
            output.withUnsafeMutableBufferPointer { ob in
                process(ib.baseAddress! + 2 * inFrame, ob.baseAddress! + 2 * outFrame, frames: frames)
            }
        }
    }

    private func shiftLeft(_ a: inout [Float], by k: Int) {
        a.withUnsafeMutableBufferPointer { p in
            let base = p.baseAddress!
            base.update(from: base + k, count: n - k)
        }
    }

    private func runFrame() {
        for i in 0..<n {
            let w = window[i]
            reL[i] = inL[i] * w; imL[i] = 0
            reR[i] = inR[i] * w; imR[i] = 0
            reM[i] = 0.5 * (reL[i] + reR[i]); imM[i] = 0
        }
        fft(&reL, &imL)
        fft(&reR, &imR)
        fft(&reM, &imM)

        for k in 0..<bins {
            let mag = (reM[k] * reM[k] + imM[k] * imM[k]).squareRoot()
            feat[k] = (log1pf(mag) - m.mean[k]) / m.std[k]
        }
        computeMask()

        let startMix = mix, endMix = targetMix
        applyMaskAndInverse(&reL, &imL)
        applyMaskAndInverse(&reR, &imR)

        let invN = 1 / Float(n)
        for i in 0..<n {
            olaL[i] += reL[i] * invN * window[i]
            olaR[i] += reR[i] * invN * window[i]
        }
        // First hop of the accumulator is complete; it aligns with inL/inR[0..<hop).
        for i in 0..<hop {
            let t = startMix + (endMix - startMix) * Float(i + 1) / Float(hop)
            outBuf[2 * i] = t * olaL[i] + (1 - t) * inL[i]
            outBuf[2 * i + 1] = t * olaR[i] + (1 - t) * inR[i]
        }
        mix = endMix
        shiftLeft(&olaL, by: hop)
        shiftLeft(&olaR, by: hop)
        for i in (n - hop)..<n { olaL[i] = 0; olaR[i] = 0 }
        outRead = 0
        outAvail = hop
    }

    private func applyMaskAndInverse(_ re: inout [Float], _ im: inout [Float]) {
        for k in 0..<bins {
            re[k] *= mask[k]
            im[k] = -im[k] * mask[k] // conjugate: inverse via forward FFT
        }
        for k in bins..<n {
            re[k] = re[n - k]
            im[k] = -im[n - k]
        }
        fft(&re, &im)
    }

    private func computeMask() {
        m.inW.withUnsafeBufferPointer { w in
            for j in 0..<hidden {
                var s = m.inB[j]
                let row = j * bins
                for k in 0..<bins { s += w[row + k] * feat[k] }
                x[j] = s > 0 ? s : 0
            }
        }
        for l in 0..<m.layers {
            let layerIn = l == 0 ? x : h[l - 1]
            matVec(m.wIh[l], m.bIh[l], layerIn, &gi)
            matVec(m.wHh[l], m.bHh[l], h[l], &gh)
            for j in 0..<hidden {
                let r = sigmoid(gi[j] + gh[j])
                let z = sigmoid(gi[hidden + j] + gh[hidden + j])
                let nn = tanhf(gi[2 * hidden + j] + r * gh[2 * hidden + j])
                h[l][j] = (1 - z) * nn + z * h[l][j]
            }
        }
        let last = h[m.layers - 1]
        m.outW.withUnsafeBufferPointer { w in
            for k in 0..<bins {
                var s = m.outB[k]
                let row = k * hidden
                for j in 0..<hidden { s += w[row + j] * last[j] }
                mask[k] = sigmoid(s)
            }
        }
    }

    private func matVec(_ w: [Float], _ b: [Float], _ v: [Float], _ out: inout [Float]) {
        let cols = v.count
        w.withUnsafeBufferPointer { wp in
            v.withUnsafeBufferPointer { vp in
                for i in 0..<out.count {
                    var s = b[i]
                    let row = i * cols
                    for j in 0..<cols { s += wp[row + j] * vp[j] }
                    out[i] = s
                }
            }
        }
    }

    @inline(__always) private func sigmoid(_ v: Float) -> Float { 1 / (1 + expf(-v)) }

    /// In-place iterative radix-2 forward FFT.
    private func fft(_ re: inout [Float], _ im: inout [Float]) {
        for i in 0..<n {
            let j = bitrev[i]
            if j > i { re.swapAt(i, j); im.swapAt(i, j) }
        }
        var size = 2
        while size <= n {
            let half = size >> 1, step = n / size
            var start = 0
            while start < n {
                for k in 0..<half {
                    let wr = cosT[k * step], wi = sinT[k * step]
                    let a = start + k, b = a + half
                    let tr = re[b] * wr - im[b] * wi
                    let ti = re[b] * wi + im[b] * wr
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                }
                start += size
            }
            size <<= 1
        }
    }
}
