import Foundation

public enum KorokaeError: Error, Equatable {
    case badMagic, unsupportedHeader, truncated, trailingBytes
}

/// Weights of the Korokae causal vocal-removal network (mirrors Android `KorokaeModel`).
///
/// Binary format (little-endian), written by ml/korokae/export.py:
///   "KRK1", int32 version=1, nFft, hop, hidden, layers,
///   f32[bins] mean, f32[bins] std, f32[hidden*bins] inW, f32[hidden] inB,
///   per layer: f32[3H*H] wIh, f32[3H*H] wHh, f32[3H] bIh, f32[3H] bHh,
///   f32[bins*hidden] outW, f32[bins] outB. GRU gate order: reset, update, new.
public final class KorokaeModel: @unchecked Sendable {
    public static let resourceName = "korokae_v1"

    public let nFft: Int
    public let hop: Int
    public let bins: Int
    public let hidden: Int
    public let layers: Int
    let mean: [Float], std: [Float]
    let inW: [Float], inB: [Float]
    let wIh: [[Float]], wHh: [[Float]], bIh: [[Float]], bHh: [[Float]]
    let outW: [Float], outB: [Float]

    public convenience init(contentsOf url: URL) throws {
        try self.init(data: Data(contentsOf: url, options: .mappedIfSafe))
    }

    public init(data: Data) throws {
        var r = Reader(bytes: [UInt8](data))
        guard r.remaining >= 24 else { throw KorokaeError.badMagic }
        guard r.take(4) == Array("KRK1".utf8) else { throw KorokaeError.badMagic }
        let version = r.int32(), nFft = r.int32(), hop = r.int32()
        let hidden = r.int32(), layers = r.int32()
        guard version == 1, nFft > 0, nFft & (nFft - 1) == 0, hop == nFft / 2,
              hidden > 0, hidden <= 4096, layers > 0, layers <= 8 else {
            throw KorokaeError.unsupportedHeader
        }
        let bins = nFft / 2 + 1
        self.nFft = nFft; self.hop = hop; self.bins = bins
        self.hidden = hidden; self.layers = layers

        mean = try r.floats(bins); std = try r.floats(bins)
        inW = try r.floats(hidden * bins); inB = try r.floats(hidden)
        var a: [[Float]] = [], b: [[Float]] = [], c: [[Float]] = [], d: [[Float]] = []
        for _ in 0..<layers {
            a.append(try r.floats(3 * hidden * hidden))
            b.append(try r.floats(3 * hidden * hidden))
            c.append(try r.floats(3 * hidden))
            d.append(try r.floats(3 * hidden))
        }
        wIh = a; wHh = b; bIh = c; bHh = d
        outW = try r.floats(bins * hidden); outB = try r.floats(bins)
        guard r.remaining == 0 else { throw KorokaeError.trailingBytes }
    }

    struct Reader {
        let bytes: [UInt8]
        var pos = 0
        var remaining: Int { bytes.count - pos }

        mutating func take(_ n: Int) -> [UInt8] {
            defer { pos += n }
            return Array(bytes[pos..<pos + n])
        }

        mutating func int32() -> Int {
            let v = UInt32(bytes[pos]) | UInt32(bytes[pos + 1]) << 8
                | UInt32(bytes[pos + 2]) << 16 | UInt32(bytes[pos + 3]) << 24
            pos += 4
            return Int(Int32(bitPattern: v))
        }

        mutating func floats(_ n: Int) throws -> [Float] {
            guard remaining >= n * 4 else { throw KorokaeError.truncated }
            var out = [Float](repeating: 0, count: n)
            for i in 0..<n {
                let p = pos + 4 * i
                let v = UInt32(bytes[p]) | UInt32(bytes[p + 1]) << 8
                    | UInt32(bytes[p + 2]) << 16 | UInt32(bytes[p + 3]) << 24
                out[i] = Float(bitPattern: v)
            }
            pos += n * 4
            return out
        }
    }
}
