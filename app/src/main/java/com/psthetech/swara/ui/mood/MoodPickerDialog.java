package com.psthetech.swara.ui.mood;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.psthetech.swara.R;
import com.psthetech.swara.SwaraApplication;
import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.data.db.entity.MoodOverride;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.theme.DesignTokens;
import com.psthetech.swara.ui.theme.MorphismThemeManager;
import com.psthetech.swara.vibe.MoodLabel;
import com.psthetech.swara.vibe.android.MixRefreshWorker;

/**
 * "This song feels…": a single-choice dialog (Sad / Calm / Happy / Energetic / Clear) that
 * writes or clears the song's mood override. Shared by every song list. All Room access runs on
 * the DB executor.
 */
public final class MoodPickerDialog {

    /** Dialog order; index {@link #CLEAR} removes the override. */
    private static final MoodLabel[] LABELS = {
            MoodLabel.SAD, MoodLabel.CALM, MoodLabel.HAPPY, MoodLabel.ENERGETIC};
    private static final int CLEAR = LABELS.length;

    private MoodPickerDialog() {}

    /** Reads the current override, then shows the dialog with it preselected. */
    public static void show(@NonNull Context context, @NonNull Song song) {
        final long songId = song.getId();
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        SwaraApplication.getInstance().getDbExecutor().execute(() -> {
            MoodLabel current = null;
            try {
                MoodOverride o = AppDatabase.getInstance(app).moodOverrideDao().get(songId);
                current = o == null ? null : MoodLabel.fromName(o.label);
            } catch (RuntimeException e) {
                android.util.Log.w("MoodPickerDialog", "Override read failed", e);
            }
            final MoodLabel preselected = current;
            main.post(() -> showDialog(context, songId, preselected));
        });
    }

    private static void showDialog(@NonNull Context context, long songId,
                                   @Nullable MoodLabel current) {
        Activity activity = activityOf(context);
        if (activity != null && (activity.isFinishing() || activity.isDestroyed())) return;
        CharSequence[] items = {
                context.getString(R.string.mood_sad),
                context.getString(R.string.mood_calm),
                context.getString(R.string.mood_happy),
                context.getString(R.string.mood_energetic),
                context.getString(R.string.mood_clear)};
        int checked = -1;
        for (int i = 0; i < LABELS.length; i++) {
            if (LABELS[i] == current) checked = i;
        }
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.set_mood)
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    d.dismiss();
                    save(context.getApplicationContext(), songId,
                            which == CLEAR ? null : LABELS[which]);
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        DesignTokens tokens = MorphismThemeManager.getInstance().getCurrentTokens();
        if (tokens != null) {
            dialog.setOnShowListener(d -> {
                android.widget.Button cancel =
                        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE);
                if (cancel != null) cancel.setTextColor(tokens.getTextSecondaryColor());
            });
        }
        dialog.show();
    }

    /** Writes ({@code label} non-null) or clears the override, then rebuilds the mixes. */
    private static void save(@NonNull Context app, long songId, @Nullable MoodLabel label) {
        SwaraApplication.getInstance().getDbExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(app);
                if (label == null) {
                    db.moodOverrideDao().deleteForSong(songId);
                } else {
                    db.moodOverrideDao().upsert(
                            new MoodOverride(songId, label.name(), System.currentTimeMillis()));
                }
            } catch (RuntimeException e) {
                android.util.Log.w("MoodPickerDialog", "Override write failed", e);
                return;
            }
            // Vibe picks rebuild their pool on every refill; the stored mixes need a rebuild.
            MixRefreshWorker.refreshNow(app);
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(app, R.string.mood_saved, Toast.LENGTH_SHORT).show());
        });
    }

    @Nullable
    private static Activity activityOf(@Nullable Context c) {
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }
}
