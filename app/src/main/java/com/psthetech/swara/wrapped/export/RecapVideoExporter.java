package com.psthetech.swara.wrapped.export;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.psthetech.swara.wrapped.SlideType;
import com.psthetech.swara.wrapped.WrappedStats;
import com.psthetech.swara.wrapped.render.SlideRenderer;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Exports a recap as a silent H.264 MP4 (1080×1920, 30 fps, 3 s per slide; 720×1280 fallback).
 * Frames are drawn with {@link SlideRenderer} onto the encoder's input surface. Runs on its own
 * background thread; callbacks arrive on the main thread.
 */
public final class RecapVideoExporter {

    private static final String TAG = "RecapVideoExporter";
    private static final String MIME = MediaFormat.MIMETYPE_VIDEO_AVC;
    static final int FPS = 30;
    static final int SECONDS_PER_SLIDE = 3;
    private static final long TIMEOUT_US = 10_000L;
    private static final int MAX_IN_FLIGHT = 2;

    public interface Listener {
        void onProgress(float progress);
        void onDone(@NonNull Uri uri);
        void onError(@NonNull String message);
    }

    public interface Cancellable {
        void cancel();
    }

    private RecapVideoExporter() {}

    /**
     * @param art              artwork by song id (see {@link SlideRenderer#artSongFor})
     * @param publishToGallery also copy to Movies/Swara via MediaStore (Android 10+)
     */
    @NonNull
    public static Cancellable export(@NonNull Context context, @NonNull WrappedStats stats,
                                     @NonNull List<SlideType> slides, @NonNull Map<Long, Bitmap> art,
                                     boolean publishToGallery, @NonNull Listener listener) {
        Context app = context.getApplicationContext();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        Handler main = new Handler(Looper.getMainLooper());
        Thread t = new Thread(() -> {
            File dir = new File(app.getCacheDir(), "share_cards");
            dir.mkdirs();
            File tmp = new File(dir, "Swara-Wrapped-" + stats.period.key() + ".mp4.tmp");
            File out = new File(dir, "Swara-Wrapped-" + stats.period.key() + ".mp4");
            try {
                int[][] sizes = {{1080, 1920}, {720, 1280}};
                Exception last = null;
                boolean ok = false;
                for (int[] size : sizes) {
                    if (!supported(size[0], size[1])) continue;
                    try {
                        encode(app, stats, slides, art, size[0], size[1], tmp, cancelled,
                                p -> main.post(() -> listener.onProgress(p)));
                        ok = true;
                        break;
                    } catch (CancelledException c) {
                        throw c;
                    } catch (Exception e) {
                        last = e;
                        Log.w(TAG, "Encoding at " + size[0] + "x" + size[1] + " failed", e);
                        tmp.delete();
                    }
                }
                if (!ok) throw last != null ? last : new IllegalStateException("No H.264 encoder");
                if (!tmp.renameTo(out)) throw new IllegalStateException("Could not finish the video file");
                Uri uri = publishToGallery ? publish(app, out, stats.period.key()) : null;
                if (uri == null) {
                    uri = FileProvider.getUriForFile(app, app.getPackageName() + ".fileprovider", out);
                }
                Uri done = uri;
                main.post(() -> listener.onDone(done));
            } catch (CancelledException c) {
                tmp.delete();
                main.post(() -> listener.onError("cancelled"));
            } catch (Throwable e) {
                tmp.delete();
                Log.w(TAG, "Video export failed", e);
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                main.post(() -> listener.onError(msg));
            }
        }, "wrapped-video");
        t.start();
        return () -> cancelled.set(true);
    }

    private interface Progress {
        void report(float p);
    }

    private static final class CancelledException extends RuntimeException {
    }

    static boolean supported(int w, int h) {
        MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo info : list.getCodecInfos()) {
            if (!info.isEncoder()) continue;
            for (String type : info.getSupportedTypes()) {
                if (!type.equalsIgnoreCase(MIME)) continue;
                MediaCodecInfo.VideoCapabilities caps = info.getCapabilitiesForType(MIME).getVideoCapabilities();
                if (caps != null && caps.isSizeSupported(w, h)) return true;
            }
        }
        return false;
    }

    private static void encode(Context ctx, WrappedStats stats, List<SlideType> slides, Map<Long, Bitmap> art,
                               int w, int h, File outFile, AtomicBoolean cancelled, Progress progress)
            throws Exception {
        MediaFormat fmt = MediaFormat.createVideoFormat(MIME, w, h);
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, w >= 1080 ? 8_000_000 : 5_000_000);
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
        fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        // Timestamps are rewritten in output order below, so B-frames must be off.
        fmt.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
        fmt.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel41);

        MediaCodec codec = MediaCodec.createEncoderByType(MIME);
        MediaMuxer muxer = null;
        Surface surface = null;
        SlideRenderer renderer = new SlideRenderer(ctx);
        VideoPlan plan = new VideoPlan(slides.size(), FPS, SECONDS_PER_SLIDE);
        try {
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface = codec.createInputSurface();
            codec.start();
            muxer = new MediaMuxer(outFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int[] track = {-1};
            int[] written = {0};
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int total = plan.totalFrames();
            for (int f = 0; f < total; f++) {
                if (cancelled.get()) throw new CancelledException();
                SlideType type = slides.get(plan.slideAt(f));
                Canvas c = surface.lockHardwareCanvas();
                try {
                    renderer.draw(c, w, h, type, stats, plan.progressAt(f),
                            art.get(SlideRenderer.artSongFor(type, stats)));
                } finally {
                    surface.unlockCanvasAndPost(c);
                }
                drain(codec, muxer, info, track, written, plan, false);
                // The input surface drops frames posted faster than the encoder takes them;
                // keep at most MAX_IN_FLIGHT frames ahead so every frame is encoded.
                long waitUntil = System.currentTimeMillis() + 2_000L;
                while (f + 1 - written[0] > MAX_IN_FLIGHT && System.currentTimeMillis() < waitUntil) {
                    if (cancelled.get()) throw new CancelledException();
                    drainOnce(codec, muxer, info, track, written, plan);
                }
                if (f % FPS == 0) progress.report(f / (float) total);
            }
            // Let the last frames reach the encoder before ending the stream.
            long waitUntil = System.currentTimeMillis() + 2_000L;
            while (written[0] < total && System.currentTimeMillis() < waitUntil) {
                drainOnce(codec, muxer, info, track, written, plan);
            }
            codec.signalEndOfInputStream();
            drain(codec, muxer, info, track, written, plan, true);
            progress.report(1f);
        } finally {
            try { codec.stop(); } catch (Exception ignored) { }
            codec.release();
            if (surface != null) surface.release();
            if (muxer != null) {
                try { muxer.stop(); } catch (Exception ignored) { }
                muxer.release();
            }
        }
    }

    /** Waits up to 10 ms for one encoded frame and writes it. */
    private static void drainOnce(MediaCodec codec, MediaMuxer muxer, MediaCodec.BufferInfo info, int[] track,
                                  int[] written, VideoPlan plan) {
        int idx = codec.dequeueOutputBuffer(info, TIMEOUT_US);
        if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            track[0] = muxer.addTrack(codec.getOutputFormat());
            muxer.start();
            return;
        }
        if (idx < 0) return;
        write(codec, muxer, info, track, written, plan, idx);
    }

    private static void write(MediaCodec codec, MediaMuxer muxer, MediaCodec.BufferInfo info, int[] track,
                              int[] written, VideoPlan plan, int idx) {
        ByteBuffer buf = codec.getOutputBuffer(idx);
        boolean config = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
        if (buf != null && info.size > 0 && !config && track[0] >= 0) {
            info.presentationTimeUs = plan.presentationTimeUs(written[0]++);
            buf.position(info.offset);
            buf.limit(info.offset + info.size);
            muxer.writeSampleData(track[0], buf, info);
        }
        codec.releaseOutputBuffer(idx, false);
    }

    /** Moves encoded frames to the muxer; frame times are assigned by output order at 30 fps. */
    private static void drain(MediaCodec codec, MediaMuxer muxer, MediaCodec.BufferInfo info, int[] track,
                              int[] written, VideoPlan plan, boolean endOfStream) {
        while (true) {
            int idx = codec.dequeueOutputBuffer(info, endOfStream ? TIMEOUT_US * 10 : 0);
            if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) return;
                continue;
            }
            if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                track[0] = muxer.addTrack(codec.getOutputFormat());
                muxer.start();
                continue;
            }
            if (idx < 0) continue;
            write(codec, muxer, info, track, written, plan, idx);
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return;
        }
    }

    /** Copies the finished MP4 into Movies/Swara (Android 10+); returns its content Uri. */
    @Nullable
    private static Uri publish(Context ctx, File file, String key) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;
        ContentResolver cr = ctx.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Video.Media.DISPLAY_NAME, "Swara-Wrapped-" + key + ".mp4");
        v.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        v.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Swara");
        v.put(MediaStore.Video.Media.IS_PENDING, 1);
        Uri uri = cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
        if (uri == null) return null;
        try (InputStream in = new FileInputStream(file); OutputStream out = cr.openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("No output stream");
            byte[] b = new byte[64 * 1024];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            ContentValues done = new ContentValues();
            done.put(MediaStore.Video.Media.IS_PENDING, 0);
            cr.update(uri, done, null, null);
            return uri;
        } catch (Exception e) {
            Log.w(TAG, "Saving to Movies failed; sharing from cache instead", e);
            cr.delete(uri, null, null);
            return null;
        }
    }
}
