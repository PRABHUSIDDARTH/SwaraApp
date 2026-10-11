import XCTest
@testable import SwaraCore

/// Verifies the Swift Korokae engine against the same PyTorch reference used by
/// Android's KorokaeEngineParityTest (fixtures read in place from android/).
final class KorokaeEngineTests: XCTestCase {
    private var model: KorokaeModel!

    override func setUpWithError() throws {
        model = try KorokaeModel(contentsOf: Fixtures.modelURL)
    }

    func testStreamingOutputMatchesPyTorchReference() throws {
        let raw = [UInt8](try Data(contentsOf: Fixtures.parityURL))
        func i32(_ p: Int) -> Int {
            Int(Int32(bitPattern: UInt32(raw[p]) | UInt32(raw[p + 1]) << 8
                | UInt32(raw[p + 2]) << 16 | UInt32(raw[p + 3]) << 24))
        }
        func f32(_ p: Int) -> Float {
            Float(bitPattern: UInt32(raw[p]) | UInt32(raw[p + 1]) << 8
                | UInt32(raw[p + 2]) << 16 | UInt32(raw[p + 3]) << 24)
        }
        let frames = i32(0), refFrames = i32(4)
        let input = (0..<2 * frames).map { f32(8 + 4 * $0) }
        let refBase = 8 + 8 * frames
        let ref = (0..<2 * refFrames).map { f32(refBase + 4 * $0) }

        let engine = KorokaeEngine(model: model)
        engine.setMix(1)
        engine.snapMix()
        var out = [Float](repeating: 0, count: input.count)
        // Odd-sized chunks exercise buffering across calls (same schedule as the Java test).
        var pos = 0, chunk = 37
        while pos < frames {
            let c = min(chunk, frames - pos)
            engine.process(input, inFrame: pos, into: &out, outFrame: pos, frames: c)
            pos += c
            chunk = chunk * 7 % 997 + 1
        }

        let hop = model.hop, n = model.nFft
        var maxErr: Float = 0, refPeak: Float = 0
        var f = n + hop
        while f < frames {
            let p = f - hop + 1
            if p >= refFrames - n { break }
            for ch in 0..<2 {
                maxErr = max(maxErr, abs(out[2 * f + ch] - ref[2 * p + ch]))
                refPeak = max(refPeak, abs(ref[2 * p + ch]))
            }
            f += 1
        }
        XCTAssertGreaterThan(refPeak, 0.01, "reference is silent")
        XCTAssertLessThan(maxErr, 2e-3, "max abs error vs PyTorch = \(maxErr)")
    }

    func testBypassIsExactDelayedPassthrough() {
        let engine = KorokaeEngine(model: model)
        engine.setMix(0)
        engine.snapMix()
        let frames = 8192
        var input = [Float](repeating: 0, count: 2 * frames)
        for i in 0..<frames {
            input[2 * i] = Float(sin(Double(i) * 0.01))
            input[2 * i + 1] = Float(cos(Double(i) * 0.013))
        }
        var out = [Float](repeating: 0, count: input.count)
        engine.process(input, into: &out, frames: frames)
        let lat = engine.latencyFrames
        for f in lat..<frames {
            XCTAssertEqual(out[2 * f], input[2 * (f - lat)], accuracy: 1e-6)
            XCTAssertEqual(out[2 * f + 1], input[2 * (f - lat) + 1], accuracy: 1e-6)
        }
    }

    func testSilenceStaysSilentAndResetRestoresMix() {
        let engine = KorokaeEngine(model: model)
        var buf = [Float](repeating: 0, count: 2 * 4096)
        let copy = buf
        engine.process(copy, into: &buf, frames: 4096)
        XCTAssertTrue(buf.allSatisfy { abs($0) < 1e-7 })
        engine.reset()
        XCTAssertEqual(engine.currentTargetMix, 1)
    }

    func testRejectsCorruptModelFiles() throws {
        let good = try Data(contentsOf: Fixtures.modelURL)
        XCTAssertThrowsError(try KorokaeModel(data: Data()))
        XCTAssertThrowsError(try KorokaeModel(data: Data("NOPE".utf8)))
        XCTAssertThrowsError(try KorokaeModel(data: good.dropLast(4)))
        XCTAssertThrowsError(try KorokaeModel(data: good + Data(count: 4)))
    }
}

final class KorokaePerformanceTests: XCTestCase {
    /// 5 s of audio must process well under real time (Android contract: < 2.5 s on host).
    /// Only meaningful in optimized builds: `swift test -c release`.
    func testRealtimeFactor() throws {
        #if DEBUG
        throw XCTSkip("run with -c release")
        #else
        let engine = KorokaeEngine(model: try KorokaeModel(contentsOf: Fixtures.modelURL))
        let frames = 44_100 * 5
        var buf = (0..<2 * frames).map { Float(sin(Double($0) * 0.0371)) * 0.3 }
        let copy = buf
        let t0 = Date()
        engine.process(copy, into: &buf, frames: frames)
        let seconds = Date().timeIntervalSince(t0)
        XCTAssertLessThan(seconds, 2.5, "took \(seconds)s for 5s of audio")
        print("Korokae: 5 s of audio in \(seconds) s")
        #endif
    }
}
