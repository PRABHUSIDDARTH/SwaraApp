package com.psthetech.swara.ui.theme;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.psthetech.swara.R;

/**
 * ThemedDialogHelper — Centralized utility for constructing theme-consistent dialogs
 * in Swara V2 across all 7 ColorThemes (Light & Dark modes).
 *
 * Implements:
 *  - Themed Create Playlist dialog
 *  - Themed Rename Playlist dialog
 *  - Themed Confirmation dialogs (Delete, Clear History)
 *  - Themed Item Picker dialogs
 *  - Smooth, subtle entrance and item-appearance transitions
 */
public final class ThemedDialogHelper {

    private ThemedDialogHelper() {}

    public interface ItemClickListener {
        void onItemClick(int which);
    }

    /**
     * Shows the themed Create Playlist dialog.
     */
    public static void showCreatePlaylistDialog(@NonNull Context context,
                                                @NonNull Consumer<String> onConfirm) {
        showPlaylistNameDialog(context, null, false, onConfirm);
    }

    /**
     * Shows the themed Rename Playlist dialog prefilled with the current name.
     */
    public static void showRenamePlaylistDialog(@NonNull Context context,
                                                @NonNull String currentName,
                                                @NonNull Consumer<String> onConfirm) {
        showPlaylistNameDialog(context, currentName, true, onConfirm);
    }

    private static void showPlaylistNameDialog(@NonNull Context context,
                                               @Nullable String initialName,
                                               boolean isRename,
                                               @NonNull Consumer<String> onConfirm) {
        DesignTokens tokens = MorphismThemeManager.getInstance().getCurrentTokens();
        if (tokens == null) {
            tokens = new DesignTokens(context, null, false);
        }

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_create_playlist, null);
        View cardRoot = view.findViewById(R.id.dialogCardRoot);
        ImageView ivIcon = view.findViewById(R.id.ivDialogIcon);
        TextView tvTitle = view.findViewById(R.id.tvDialogTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvDialogSubtitle);
        View inputContainer = view.findViewById(R.id.inputContainer);
        EditText etName = view.findViewById(R.id.etPlaylistName);
        TextView btnCancel = view.findViewById(R.id.btnCancel);
        TextView btnConfirm = view.findViewById(R.id.btnConfirm);

        float density = context.getResources().getDisplayMetrics().density;

        // Apply Liquid Glass dialog card background
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        int dialogBgColor = tokens.isNightMode() ? tokens.getSurfaceElevatedColor() : tokens.getSurfaceColor();
        cardBg.setColor(dialogBgColor);
        cardBg.setCornerRadius(tokens.getCornerRadiusDp() * density);
        cardBg.setStroke(Math.max(1, Math.round(density)), tokens.getStrokeColor());
        cardRoot.setBackground(cardBg);

        // Header icon with theme accent tint
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.OVAL);
        int accent = tokens.getAccentColor();
        int iconAlpha = tokens.isNightMode() ? 46 : 30;
        iconBg.setColor(Color.argb(iconAlpha, Color.red(accent), Color.green(accent), Color.blue(accent)));
        ivIcon.setBackground(iconBg);
        ivIcon.setColorFilter(accent);

        // Typography
        tvTitle.setTextColor(tokens.getTextPrimaryColor());
        tvSubtitle.setTextColor(tokens.getTextSecondaryColor());

        if (isRename) {
            tvTitle.setText(R.string.rename_playlist);
            tvSubtitle.setText(R.string.playlist_name_hint);
            btnConfirm.setText(R.string.rename);
            if (initialName != null) {
                etName.setText(initialName);
                etName.selectAll();
            }
        } else {
            tvTitle.setText(R.string.create_playlist);
            tvSubtitle.setText(R.string.playlist_name_hint);
            btnConfirm.setText(R.string.create);
        }

        // Input container styling
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setShape(GradientDrawable.RECTANGLE);
        inputBg.setColor(tokens.getSearchBackgroundColor());
        inputBg.setCornerRadius(10 * density);
        inputBg.setStroke(Math.max(1, Math.round(density)), tokens.getStrokeColor());
        inputContainer.setBackground(inputBg);

        etName.setTextColor(tokens.getTextPrimaryColor());
        etName.setHintTextColor(tokens.getSearchHintColor());

        final DesignTokens activeTokens = tokens;
        etName.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                inputBg.setStroke(Math.max(1, Math.round(1.5f * density)), activeTokens.getAccentColor());
            } else {
                inputBg.setStroke(Math.max(1, Math.round(density)), activeTokens.getStrokeColor());
            }
        });

        // Cancel button
        btnCancel.setTextColor(tokens.getTextSecondaryColor());

        // Confirm button
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setShape(GradientDrawable.RECTANGLE);
        btnBg.setColor(tokens.getAccentColor());
        btnBg.setCornerRadius(tokens.getCornerRadiusDp() * density);
        btnConfirm.setBackground(btnBg);
        btnConfirm.setTextColor(tokens.getButtonTextColor());

        AlertDialog dialog = new AlertDialog.Builder(context).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            String name = etName.getText() != null ? etName.getText().toString().trim() : "";
            if (name.isEmpty()) {
                Toast.makeText(context, R.string.playlist_name_hint, Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            onConfirm.accept(name);
        });

        dialog.show();

        // Entrance animation
        cardRoot.setScaleX(0.95f);
        cardRoot.setScaleY(0.95f);
        cardRoot.setAlpha(0f);
        cardRoot.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1.0f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Keyboard focus
        etName.requestFocus();
        etName.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etName, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 120);
    }

    /**
     * Shows a themed confirmation dialog (e.g. Delete, Clear History).
     */
    public static void showConfirmationDialog(@NonNull Context context,
                                              @NonNull String title,
                                              @Nullable String message,
                                              @NonNull String positiveButtonText,
                                              @NonNull Runnable onConfirm) {
        DesignTokens currentTokens = MorphismThemeManager.getInstance().getCurrentTokens();
        final DesignTokens tokens = currentTokens != null ? currentTokens : new DesignTokens(context, null, false);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(title);
        if (message != null && !message.isEmpty()) {
            builder.setMessage(message);
        }
        builder.setPositiveButton(positiveButtonText, (d, w) -> onConfirm.run());
        builder.setNegativeButton(R.string.cancel, null);

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            int accent = tokens.getAccentColor();
            int secondary = tokens.getTextSecondaryColor();
            if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accent);
            }
            if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(secondary);
            }
        });
        dialog.show();
    }

    /**
     * Shows a themed item picker dialog (e.g. Add to Playlist list, Artwork options).
     */
    public static void showItemPickerDialog(@NonNull Context context,
                                            @NonNull String title,
                                            @NonNull CharSequence[] items,
                                            @NonNull ItemClickListener listener) {
        DesignTokens currentTokens = MorphismThemeManager.getInstance().getCurrentTokens();
        final DesignTokens tokens = currentTokens != null ? currentTokens : new DesignTokens(context, null, false);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(title);
        builder.setItems(items, (d, which) -> listener.onItemClick(which));
        builder.setNegativeButton(R.string.cancel, null);

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(tokens.getTextSecondaryColor());
            }
        });
        dialog.show();
    }

    /**
     * Smooth, theme-aware appearance transition for newly inserted playlist items.
     * Uses active theme accent highlight that gracefully fades back to normal.
     */
    public static void animateNewItemAppearance(@NonNull View view, @Nullable DesignTokens tokens) {
        view.setScaleX(0.96f);
        view.setScaleY(0.96f);
        view.setAlpha(0.2f);
        view.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1.0f)
                .setDuration(320)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        if (tokens != null) {
            float density = view.getContext().getResources().getDisplayMetrics().density;
            GradientDrawable highlight = new GradientDrawable();
            highlight.setShape(GradientDrawable.RECTANGLE);
            highlight.setColor(tokens.getPlaybackSurfaceColor());
            highlight.setCornerRadius(tokens.getCornerRadiusDp() * density);
            highlight.setStroke(Math.max(1, Math.round(1.5f * density)), tokens.getPlaybackStrokeColor());
            view.setBackground(highlight);

            view.postDelayed(() -> {
                MorphismThemeManager.getInstance().applyToView(view, false, tokens);
            }, 650);
        }
    }
}
