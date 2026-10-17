package com.psthetech.swara.wrapped.export;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psthetech.swara.R;
import com.psthetech.swara.SwaraApplication;
import com.psthetech.swara.ui.share.SongShareHelper;
import com.psthetech.swara.wrapped.SlideType;
import com.psthetech.swara.wrapped.WrappedStats;
import com.psthetech.swara.wrapped.render.SlideRenderer;

/** Shares one slide's settled frame as a 1080×1920 PNG through the system share sheet. */
public final class SlideImageExporter {

    private SlideImageExporter() {}

    public static void share(@NonNull Activity activity, @NonNull SlideType type, @NonNull WrappedStats stats,
                             @Nullable Bitmap art) {
        SwaraApplication.getInstance().getIoExecutor().execute(() -> {
            try {
                Bitmap card = SlideRenderer.renderFinalFrame(activity, type, stats, art);
                Uri uri = SongShareHelper.writeCardToCache(activity, card);
                card.recycle();
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    Intent send = SongShareHelper.buildImageIntent(activity, uri,
                            activity.getString(R.string.wrapped_share_caption));
                    activity.startActivity(Intent.createChooser(send, activity.getString(R.string.share)));
                });
            } catch (Exception e) {
                Log.w("SlideImageExporter", "Share failed", e);
                activity.runOnUiThread(() -> Toast.makeText(activity, R.string.wrapped_share_failed,
                        Toast.LENGTH_SHORT).show());
            }
        });
    }

    /** Shares an exported video Uri. */
    public static void shareVideo(@NonNull Activity activity, @NonNull Uri video) {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("video/mp4")
                .putExtra(Intent.EXTRA_STREAM, video)
                .putExtra(Intent.EXTRA_TEXT, activity.getString(R.string.wrapped_share_caption))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(Intent.createChooser(send, activity.getString(R.string.share)));
    }
}
