package com.psthetech.swara.wrapped.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.psthetech.swara.R;
import com.psthetech.swara.SwaraApplication;
import com.psthetech.swara.wrapped.Period;
import com.psthetech.swara.wrapped.RecapRepository;
import com.psthetech.swara.wrapped.SlidePlan;
import com.psthetech.swara.wrapped.SlideType;
import com.psthetech.swara.wrapped.WrappedStats;
import com.psthetech.swara.wrapped.export.RecapVideoExporter;
import com.psthetech.swara.wrapped.export.SlideImageExporter;
import com.psthetech.swara.wrapped.render.SlideRenderer;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Swara Wrapped story. Opens a stored recap (by key) or computes live stats for a period, so it
 * works straight from a notification even when nothing else in the app is running.
 */
public class WrappedStoryActivity extends AppCompatActivity {

    public static final String EXTRA_RECAP_KEY = "wrapped_recap_key";
    public static final String EXTRA_PERIOD_KIND = "wrapped_period_kind";
    public static final String EXTRA_PERIOD_START = "wrapped_period_start";
    public static final String EXTRA_PERIOD_END = "wrapped_period_end";

    private StoryView story;
    private View video;
    @Nullable private WrappedStats stats;
    private Map<Long, Bitmap> art = Collections.emptyMap();
    @Nullable private RecapVideoExporter.Cancellable exportJob;

    public static Intent forRecap(@NonNull Context c, @NonNull String key) {
        return new Intent(c, WrappedStoryActivity.class).putExtra(EXTRA_RECAP_KEY, key);
    }

    public static Intent forPeriod(@NonNull Context c, @NonNull Period p) {
        return new Intent(c, WrappedStoryActivity.class)
                .putExtra(EXTRA_PERIOD_KIND, p.kind.name())
                .putExtra(EXTRA_PERIOD_START, p.start)
                .putExtra(EXTRA_PERIOD_END, p.end);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_wrapped_story);
        story = findViewById(R.id.storyView);
        video = findViewById(R.id.btnStoryVideo);
        ProgressBar loading = findViewById(R.id.storyLoading);
        findViewById(R.id.btnStoryClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnStoryShare).setOnClickListener(v -> shareCurrent());
        video.setOnClickListener(v -> exportVideo());

        Intent in = getIntent();
        String key = in.getStringExtra(EXTRA_RECAP_KEY);
        Period period = key == null && in.hasExtra(EXTRA_PERIOD_KIND)
                ? new Period(Period.Kind.valueOf(in.getStringExtra(EXTRA_PERIOD_KIND)),
                in.getIntExtra(EXTRA_PERIOD_START, 0), in.getIntExtra(EXTRA_PERIOD_END, 0))
                : null;
        Context app = getApplicationContext();
        SwaraApplication.getInstance().getIoExecutor().execute(() -> {
            RecapRepository repo = new RecapRepository(app);
            WrappedStats s;
            if (key != null) {
                s = repo.stored(key);
                if (s != null) repo.markSeen(key);
            } else {
                s = repo.statsFor(period != null ? period : RecapRepository.thisMonth());
            }
            Map<Long, Bitmap> artwork = s != null ? repo.artFor(s) : Collections.emptyMap();
            WrappedStats loaded = s;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                loading.setVisibility(View.GONE);
                List<SlideType> plan = loaded != null ? SlidePlan.plan(loaded) : Collections.emptyList();
                if (plan.isEmpty()) {
                    Toast.makeText(this, R.string.wrapped_not_enough, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                stats = loaded;
                art = artwork;
                story.bind(loaded, plan, artwork, new StoryView.Listener() {
                    @Override public void onSlideChanged(int index, SlideType type) {
                        video.setVisibility(type == SlideType.SUMMARY ? View.VISIBLE : View.GONE);
                    }

                    @Override public void onFinished() {
                        // Stay on the summary slide.
                    }
                });
            });
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        story.pauseStory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        story.resumeStory();
    }

    @Override
    protected void onDestroy() {
        if (exportJob != null) exportJob.cancel();
        super.onDestroy();
    }

    private void shareCurrent() {
        SlideType t = story.current();
        if (t == null || stats == null) return;
        SlideImageExporter.share(this, t, stats, art.get(SlideRenderer.artSongFor(t, stats)));
    }

    private void exportVideo() {
        if (stats == null || exportJob != null) return;
        story.pauseStory();
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        bar.setPadding(pad, pad, pad, 0);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.wrapped_video_progress)
                .setView(bar)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (d, w) -> {
                    if (exportJob != null) exportJob.cancel();
                })
                .show();
        exportJob = RecapVideoExporter.export(this, stats, SlidePlan.plan(stats), art, true,
                new RecapVideoExporter.Listener() {
                    @Override public void onProgress(float p) {
                        bar.setProgress(Math.round(p * 100));
                    }

                    @Override public void onDone(@NonNull Uri uri) {
                        exportJob = null;
                        if (isFinishing() || isDestroyed()) return;
                        dialog.dismiss();
                        Toast.makeText(WrappedStoryActivity.this, R.string.wrapped_video_saved, Toast.LENGTH_SHORT).show();
                        SlideImageExporter.shareVideo(WrappedStoryActivity.this, uri);
                    }

                    @Override public void onError(@NonNull String message) {
                        exportJob = null;
                        if (isFinishing() || isDestroyed()) return;
                        dialog.dismiss();
                        if (!"cancelled".equals(message)) {
                            Toast.makeText(WrappedStoryActivity.this, R.string.wrapped_video_failed, Toast.LENGTH_LONG).show();
                        }
                        story.resumeStory();
                    }
                });
    }
}
