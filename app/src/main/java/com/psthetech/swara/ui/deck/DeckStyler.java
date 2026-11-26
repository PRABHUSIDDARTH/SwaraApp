package com.psthetech.swara.ui.deck;

import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import com.psthetech.swara.R;
import com.psthetech.swara.ui.theme.DesignTokens;

/** Applies the Deck colours to an inflated Deck layout. */
public final class DeckStyler {

    private static final int[] HEADER_ICONS = {
            R.id.btnCollapse, R.id.btnShareCard, R.id.btnShare, R.id.btnQueue, R.id.btnFavorite};

    private DeckStyler() { }

    @NonNull
    public static DeckPalette apply(@NonNull View root, @NonNull DesignTokens tokens) {
        DeckPalette p = new DeckPalette(tokens);
        float density = root.getResources().getDisplayMetrics().density;
        root.setBackground(new DeckBodyDrawable(p, density));

        text(root, R.id.nowPlayingHeader, p.textSecondary);
        text(root, R.id.tvTitle, p.text);
        text(root, R.id.tvArtist, p.accentText);
        text(root, R.id.tvAlbum, p.textTertiary);
        for (int id : HEADER_ICONS) {
            ImageView icon = root.findViewById(id);
            if (icon != null) icon.setColorFilter(p.textSecondary);
        }

        View handle = root.findViewById(R.id.dragHandle);
        if (handle != null && handle.getBackground() != null) {
            handle.getBackground().mutate().setTint(p.textTertiary);
        }

        View window = root.findViewById(R.id.deckWindow);
        if (window != null) {
            GradientDrawable glass = new GradientDrawable();
            glass.setColor(p.well);
            glass.setCornerRadius(10f * density);
            glass.setStroke(Math.round(1.5f * density), p.bodyEdge);
            window.setBackground(glass);
        }

        styleTree(root, p);
        DeckKeyView play = root.findViewById(R.id.btnPlayPause);
        if (play != null) {
            play.setPrimary(true);
            play.setColorFilter(p.onAccent);
        }
        return p;
    }

    private static void styleTree(View view, DeckPalette p) {
        if (view instanceof DeckReelView) {
            ((DeckReelView) view).setPalette(p);
        } else if (view instanceof DeckTimeView) {
            ((DeckTimeView) view).setPalette(p);
        } else if (view instanceof DeckSpectrumView) {
            ((DeckSpectrumView) view).setPalette(p);
        } else if (view instanceof DeckFaderView) {
            ((DeckFaderView) view).setPalette(p);
        } else if (view instanceof DeckKeyView) {
            ((DeckKeyView) view).setPalette(p);
            ((DeckKeyView) view).setColorFilter(p.icon);
        } else if (view instanceof DeckKeyLayout) {
            DeckKeyLayout key = (DeckKeyLayout) view;
            key.setPalette(p);
            for (int i = 0; i < key.getChildCount(); i++) {
                if (key.getChildAt(i) instanceof ImageView) {
                    ((ImageView) key.getChildAt(i)).setColorFilter(p.icon);
                }
            }
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) styleTree(group.getChildAt(i), p);
        }
    }

    private static void text(View root, @IdRes int id, int color) {
        TextView view = root.findViewById(id);
        if (view != null) view.setTextColor(color);
    }
}
