package com.psthetech.swara.ui.voice;

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.psthetech.swara.R;
import com.psthetech.swara.ui.glass.LiquidGlassRenderer;
import com.psthetech.swara.ui.theme.DesignTokens;
import com.psthetech.swara.ui.theme.MorphismThemeManager;
import com.psthetech.swara.voice.exec.VoiceOutcome;
import com.psthetech.swara.voice.match.Candidate;

import java.util.List;

/**
 * The listening sheet: state title, live level bar, reply text and up to four choice chips.
 * All state comes from {@link VoiceUiCoordinator#sheet()}; this fragment only renders it.
 */
public class VoiceSheetFragment extends BottomSheetDialogFragment {

    public static final String TAG = "VoiceSheetFragment";
    private static final long AUTO_DISMISS_MS = 2500;
    private static final int MAX_CHIPS = 4;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoDismiss = this::dismissSafely;

    private View cardVoice;
    private ImageView ivMic;
    private TextView tvTitle;
    private View levelTrack;
    private View levelFill;
    private TextView tvReply;
    private LinearLayout chipContainer;
    private TextView btnAction;
    private TextView tvDebug;

    @Nullable private DesignTokens tokens;
    @Nullable private VoiceUiCoordinator.SheetModel lastModel;
    private boolean dismissing;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_voice, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        cardVoice = view.findViewById(R.id.cardVoice);
        ivMic = view.findViewById(R.id.ivVoiceMic);
        tvTitle = view.findViewById(R.id.tvVoiceTitle);
        levelTrack = view.findViewById(R.id.levelTrack);
        levelFill = view.findViewById(R.id.levelFill);
        tvReply = view.findViewById(R.id.tvVoiceReply);
        chipContainer = view.findViewById(R.id.chipContainer);
        btnAction = view.findViewById(R.id.btnVoiceAction);
        tvDebug = view.findViewById(R.id.tvVoiceDebug);
        levelFill.setPivotX(0f);
        LiquidGlassRenderer.attachPressAnimation(btnAction);

        MorphismThemeManager.getInstance().getDesignTokens().observe(getViewLifecycleOwner(), t -> {
            if (t == null || getView() == null) return;
            tokens = t;
            applyDesignTokens(t);
            if (lastModel != null) render(lastModel);
        });

        VoiceUiCoordinator coordinator = VoiceUiCoordinator.from(this);
        if (coordinator == null) {
            dismissSafely();
            return;
        }
        coordinator.sheet().observe(getViewLifecycleOwner(), this::render);
    }

    private void applyDesignTokens(@NonNull DesignTokens t) {
        View root = getView();
        if (root == null) return;
        root.setBackground(t.createAmbientDrawable());
        cardVoice.setBackground(t.createCardDrawable(requireContext()));
        tvTitle.setTextColor(t.getTextPrimaryColor());
        tvReply.setTextColor(t.getTextSecondaryColor());

        int accent = t.getAccentColor();
        GradientDrawable micBg = new GradientDrawable();
        micBg.setShape(GradientDrawable.OVAL);
        micBg.setColor(Color.argb(t.isNightMode() ? 46 : 30,
                Color.red(accent), Color.green(accent), Color.blue(accent)));
        ivMic.setBackground(micBg);
        ivMic.setColorFilter(accent);

        float density = getResources().getDisplayMetrics().density;
        GradientDrawable track = new GradientDrawable();
        track.setColor(t.getDividerColor());
        track.setCornerRadius(3 * density);
        levelTrack.setBackground(track);
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(accent);
        fill.setCornerRadius(3 * density);
        levelFill.setBackground(fill);

        GradientDrawable debugBg = new GradientDrawable();
        debugBg.setColor(t.getSurfaceVariantColor());
        debugBg.setCornerRadius(10 * density);
        tvDebug.setBackground(debugBg);
        tvDebug.setTextColor(t.getTextSecondaryColor());
    }

    private void render(@Nullable VoiceUiCoordinator.SheetModel m) {
        if (m == null || getView() == null) return;
        VoiceUiCoordinator.SheetModel prev = lastModel;
        lastModel = m;
        if (m.mode == VoiceUiCoordinator.SheetModel.Mode.HIDDEN) {
            dismissSafely();
            return;
        }
        // A level-only update while listening just moves the bar.
        if (m.mode == VoiceUiCoordinator.SheetModel.Mode.LISTENING && prev != null
                && prev.mode == VoiceUiCoordinator.SheetModel.Mode.LISTENING
                && levelTrack.getVisibility() == View.VISIBLE) {
            setLevel(m.level);
            return;
        }
        handler.removeCallbacks(autoDismiss);

        levelTrack.setVisibility(View.GONE);
        tvReply.setVisibility(View.GONE);
        chipContainer.removeAllViews();
        chipContainer.setVisibility(View.GONE);
        btnAction.setVisibility(View.GONE);
        btnAction.setOnClickListener(null);
        tvDebug.setVisibility(m.debug != null ? View.VISIBLE : View.GONE);
        if (m.debug != null) tvDebug.setText(m.debug);

        VoiceUiCoordinator coordinator = VoiceUiCoordinator.from(this);
        switch (m.mode) {
            case PREPARING:
                tvTitle.setText(R.string.voice_getting_ready);
                break;
            case LISTENING:
                tvTitle.setText(R.string.voice_listening);
                levelTrack.setVisibility(View.VISIBLE);
                setLevel(m.level);
                tvReply.setText(R.string.voice_hint);
                tvReply.setVisibility(View.VISIBLE);
                break;
            case THINKING:
                tvTitle.setText(R.string.voice_thinking);
                break;
            case REPLY: {
                VoiceOutcome out = m.outcome;
                tvTitle.setText(out != null ? out.text : "");
                if (out != null && !out.choices.isEmpty()) {
                    renderChoices(out, coordinator);
                } else {
                    handler.postDelayed(autoDismiss, AUTO_DISMISS_MS);
                }
                break;
            }
            case PERMISSION:
                tvTitle.setText(R.string.voice_permission_title);
                tvReply.setText(R.string.voice_permission_rationale);
                tvReply.setVisibility(View.VISIBLE);
                showAction(R.string.voice_permission_allow, v -> {
                    if (coordinator != null) coordinator.requestPermission();
                });
                break;
            case SETTINGS:
                tvTitle.setText(R.string.voice_permission_title);
                tvReply.setText(getString(R.string.voice_permission_settings) + "\n\n"
                        + getString(R.string.voice_permission_rationale));
                tvReply.setVisibility(View.VISIBLE);
                showAction(R.string.open_settings, v -> {
                    if (coordinator != null) coordinator.openAppSettings();
                    dismissSafely();
                });
                break;
            case MESSAGE:
                tvTitle.setText(m.message != null ? m.message : "");
                handler.postDelayed(autoDismiss, AUTO_DISMISS_MS);
                break;
            default:
                break;
        }
    }

    private void renderChoices(@NonNull VoiceOutcome out, @Nullable VoiceUiCoordinator coordinator) {
        List<Candidate> choices = out.choices;
        // CONFIRM ("Did you mean X?"): choices[0] is the Yes option, the rest are alternatives.
        // CHOOSE ("Which one?"): all choices are peers.
        boolean medium = out.kind == VoiceOutcome.Kind.CONFIRM;
        int start = 0;
        if (medium) {
            final Candidate top = choices.get(0);
            showAction(R.string.voice_yes, v -> {
                if (coordinator != null) coordinator.onChoice(top);
            });
            btnAction.setContentDescription(getString(R.string.voice_yes) + ", " + label(top));
            start = 1;
        }
        int shown = 0;
        for (int i = start; i < choices.size() && shown < MAX_CHIPS; i++, shown++) {
            final Candidate c = choices.get(i);
            chipContainer.addView(makeChip(label(c), v -> {
                if (coordinator != null) coordinator.onChoice(c);
            }));
        }
        chipContainer.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
    }

    private static String label(Candidate c) {
        return c.detail == null || c.detail.isEmpty() ? c.display : c.display + " · " + c.detail;
    }

    private TextView makeChip(String text, View.OnClickListener click) {
        float density = getResources().getDisplayMetrics().density;
        TextView chip = new TextView(requireContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(8 * density);
        chip.setLayoutParams(lp);
        chip.setMinHeight(Math.round(44 * density));
        chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int ph = Math.round(16 * density);
        int pv = Math.round(10 * density);
        chip.setPadding(ph, pv, ph, pv);
        chip.setTextSize(14);
        chip.setMaxLines(2);
        chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chip.setText(text);
        chip.setClickable(true);
        chip.setFocusable(true);
        if (tokens != null) {
            chip.setBackground(tokens.createGlassPillDrawable(requireContext()));
            chip.setTextColor(tokens.getTextPrimaryColor());
        }
        chip.setOnClickListener(click);
        LiquidGlassRenderer.attachPressAnimation(chip);
        return chip;
    }

    private void showAction(int textRes, View.OnClickListener click) {
        btnAction.setText(textRes);
        btnAction.setContentDescription(null);
        btnAction.setOnClickListener(click);
        btnAction.setVisibility(View.VISIBLE);
        if (tokens != null) {
            float density = getResources().getDisplayMetrics().density;
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(tokens.getAccentColor());
            bg.setCornerRadius(999 * density);
            btnAction.setBackground(bg);
            btnAction.setTextColor(tokens.getOnAccentColor());
        }
    }

    private void setLevel(float rms) {
        // Speech RMS sits around 0.02–0.2; map it onto the bar with a soft curve.
        float v = (float) Math.min(1.0, Math.sqrt(Math.max(0f, rms) * 8f));
        levelFill.animate().scaleX(v).setDuration(70).start();
    }

    private void dismissSafely() {
        if (dismissing || !isAdded()) return;
        dismissing = true;
        dismissAllowingStateLoss();
    }

    /** True once this sheet is going away; the coordinator then treats it as absent. */
    public boolean isDismissing() {
        return dismissing || isRemoving();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        dismissing = true;
        super.onDismiss(dialog);
        handler.removeCallbacks(autoDismiss);
        FragmentActivity a = getActivity();
        if (a != null && a.isChangingConfigurations()) return;
        VoiceUiCoordinator coordinator = VoiceUiCoordinator.from(this);
        if (coordinator != null) coordinator.onSheetDismissed();
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacks(autoDismiss);
        super.onDestroyView();
    }
}
