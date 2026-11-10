package com.psthetech.swara.korokae.v2;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Reads the generated Korokae v2 test assets (git-ignored; made by korokae_v2.export). */
final class TestAssets {

    private TestAssets() {
    }

    /**
     * Copies an androidTest asset into the app's cache dir and returns the file. A cached copy is
     * reused only when its size matches the asset: {@code adb install -r} keeps the cache dir, so
     * a re-exported model under the same name must replace the old one.
     */
    static File copyToCache(Context appContext, String assetName) throws IOException {
        File out = new File(appContext.getCacheDir(), assetName);
        if (out.exists() && out.length() == assetLength(assetName)) return out;
        File tmp = new File(appContext.getCacheDir(), assetName + ".tmp");
        try (InputStream in = testAssets().open(assetName);
             OutputStream os = new FileOutputStream(tmp)) {
            byte[] buf = new byte[1 << 20];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        }
        if (!tmp.renameTo(out)) throw new IOException("rename failed: " + out);
        return out;
    }

    /** Length of an uncompressed androidTest asset ({@code .onnx} is stored with noCompress). */
    private static long assetLength(String assetName) throws IOException {
        try (android.content.res.AssetFileDescriptor fd = testAssets().openFd(assetName)) {
            return fd.getLength();
        }
    }

    /** Reads a KRP2 parity fixture: returns {input, expected}, both interleaved stereo. */
    static float[][] readParity(String assetName) throws IOException {
        try (DataInputStream in = new DataInputStream(testAssets().open(assetName))) {
            byte[] header = new byte[16];
            in.readFully(header);
            ByteBuffer h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            byte[] magic = new byte[4];
            h.get(magic);
            if (!"KRP2".equals(new String(magic, "US-ASCII"))) throw new IOException("bad magic");
            int version = h.getInt();
            h.getInt(); // sample rate
            int frames = h.getInt();
            if (version != 1) throw new IOException("bad version " + version);
            return new float[][]{readFloats(in, frames * 2), readFloats(in, frames * 2)};
        }
    }

    private static float[] readFloats(DataInputStream in, int count) throws IOException {
        byte[] raw = new byte[count * 4];
        in.readFully(raw);
        float[] out = new float[count];
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(out);
        return out;
    }

    private static android.content.res.AssetManager testAssets() {
        return InstrumentationRegistry.getInstrumentation().getContext().getAssets();
    }
}
