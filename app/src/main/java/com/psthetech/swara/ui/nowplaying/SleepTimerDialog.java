package com.psthetech.swara.ui.nowplaying;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.psthetech.swara.R;
import com.psthetech.swara.ui.theme.DesignTokens;
import com.psthetech.swara.ui.theme.MorphismThemeManager;
import com.psthetech.swara.util.SleepTimerManager;

/**
 * Sleep Timer bottom sheet dialog.
 *
 * Presents fixed-duration options (15/30/45/60 min) and "end of song" mode.
 * Also allows cancelling an active timer.
 *
 * Usage:
 *   SleepTimerDialog.newInstance(listener).show(parentFragmentManager, TAG);
 */
public class SleepTimerDialog extends BottomSheetDialogFragment {

    public static final String TAG = "SleepTimerDialog";

    /** Callback triggered by the dialog parent (NowPlayingFragment) to pause playback. */
    public interface OnSleepTimerActionListener {
        void onPausePlayback();
    }

    private OnSleepTimerActionListener actionListener;

    public static SleepTimerDialog newInstance(OnSleepTimerActionListener listener) {
        SleepTimerDialog dialog = new SleepTimerDialog();
        dialog.actionListener = listener;
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_sleep_timer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SleepTimerManager timer = SleepTimerManager.getInstance();

        DesignTokens tokens = MorphismThemeManager.getInstance().getCurrentTokens();
        if (tokens != null) {
            view.setBackgroundColor(tokens.getSurfaceColor());
            TextView title = view.findViewById(R.id.tvTimerTitle);
            if (title != null) title.setTextColor(tokens.getTextPrimaryColor());
        }

        SleepTimerManager.OnTimerFinishedListener finishedListener = () -> {
            if (actionListener != null) actionListener.onPausePlayback();
        };

        setOptionClick(view, R.id.option15, () -> { timer.startTimer(15, finishedListener); dismiss(); });
        setOptionClick(view, R.id.option30, () -> { timer.startTimer(30, finishedListener); dismiss(); });
        setOptionClick(view, R.id.option45, () -> { timer.startTimer(45, finishedListener); dismiss(); });
        setOptionClick(view, R.id.option60, () -> { timer.startTimer(60, finishedListener); dismiss(); });
        setOptionClick(view, R.id.optionEndOfSong, () -> { timer.sleepAfterCurrentSong(finishedListener); dismiss(); });
        setOptionClick(view, R.id.optionCancel, () -> { timer.cancelTimer(); dismiss(); });

        // Show/hide the "cancel timer" option based on whether a timer is active
        View cancelRow = view.findViewById(R.id.optionCancel);
        if (cancelRow != null) {
            cancelRow.setVisibility(timer.isActive() ? View.VISIBLE : View.GONE);
        }
    }

    private void setOptionClick(View root, int viewId, Runnable action) {
        View v = root.findViewById(viewId);
        if (v != null) v.setOnClickListener(btn -> action.run());
    }
}
