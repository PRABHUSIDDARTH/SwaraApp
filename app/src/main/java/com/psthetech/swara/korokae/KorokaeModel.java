package com.psthetech.swara.korokae;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Immutable weights of the Korokae causal vocal-removal network.
 *
 * Binary format (little-endian), written by ml/korokae/export.py:
 *   magic "KRK1", int32 version=1, int32 nFft, int32 hop, int32 hidden, int32 layers,
 *   float32[bins] mean, float32[bins] std,
 *   float32[hidden*bins] inW, float32[hidden] inB,
 *   per layer: float32[3H*H] wIh, float32[3H*H] wHh, float32[3H] bIh, float32[3H] bHh,
 *   float32[bins*hidden] outW, float32[bins] outB.
 * GRU gate order follows PyTorch: reset, update, new.
 */
public final class KorokaeModel {

    public static final String ASSET_PATH = "models/korokae_v1.bin";

    final int nFft;
    final int hop;
    final int bins;
    final int hidden;
    final int layers;
    final float[] mean;
    final float[] std;
    final float[] inW;
    final float[] inB;
    final float[][] wIh;
    final float[][] wHh;
    final float[][] bIh;
    final float[][] bHh;
    final float[] outW;
    final float[] outB;

    private KorokaeModel(int nFft, int hop, int hidden, int layers, FloatReader r) throws IOException {
        this.nFft = nFft;
        this.hop = hop;
        this.bins = nFft / 2 + 1;
        this.hidden = hidden;
        this.layers = layers;
        mean = r.read(bins);
        std = r.read(bins);
        inW = r.read(hidden * bins);
        inB = r.read(hidden);
        wIh = new float[layers][];
        wHh = new float[layers][];
        bIh = new float[layers][];
        bHh = new float[layers][];
        for (int l = 0; l < layers; l++) {
            wIh[l] = r.read(3 * hidden * hidden);
            wHh[l] = r.read(3 * hidden * hidden);
            bIh[l] = r.read(3 * hidden);
            bHh[l] = r.read(3 * hidden);
        }
        outW = r.read(bins * hidden);
        outB = r.read(bins);
    }

    /** Parses a model from a stream (e.g. AssetManager.open(ASSET_PATH)). */
    public static KorokaeModel load(InputStream in) throws IOException {
        DataInputStream din = new DataInputStream(in);
        byte[] head = new byte[24];
        din.readFully(head);
        ByteBuffer hb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        if (hb.get() != 'K' || hb.get() != 'R' || hb.get() != 'K' || hb.get() != '1') {
            throw new IOException("Not a Korokae model file");
        }
        int version = hb.getInt();
        int nFft = hb.getInt();
        int hop = hb.getInt();
        int hidden = hb.getInt();
        int layers = hb.getInt();
        if (version != 1 || nFft <= 0 || (nFft & (nFft - 1)) != 0 || hop != nFft / 2
                || hidden <= 0 || hidden > 4096 || layers <= 0 || layers > 8) {
            throw new IOException("Unsupported Korokae model header");
        }
        KorokaeModel model = new KorokaeModel(nFft, hop, hidden, layers, new FloatReader(din));
        if (din.read() != -1) {
            throw new IOException("Trailing bytes in Korokae model file");
        }
        return model;
    }

    public int getNFft() { return nFft; }
    public int getHop() { return hop; }

    private static final class FloatReader {
        private final DataInputStream in;

        FloatReader(DataInputStream in) { this.in = in; }

        float[] read(int n) throws IOException {
            byte[] raw = new byte[n * 4];
            in.readFully(raw);
            float[] out = new float[n];
            ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(out);
            return out;
        }
    }
}
