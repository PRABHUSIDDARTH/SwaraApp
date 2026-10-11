package com.psthetech.swara.ui.settings;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.ColorTheme;
import com.psthetech.swara.domain.model.MorphismStyle;
import com.psthetech.swara.domain.model.ThemeMode;
import com.psthetech.swara.ui.theme.DesignTokens;
import com.psthetech.swara.ui.theme.MorphismThemeManager;

import java.util.Arrays;
import java.util.List;

public class ColorThemePreviewAdapter extends RecyclerView.Adapter<ColorThemePreviewAdapter.ViewHolder> {

    public interface OnColorThemeSelectedListener {
        void onColorThemeSelected(ColorTheme theme);
    }

    private final List<ColorTheme> themes = Arrays.asList(ColorTheme.values());
    private ColorTheme activeTheme;
    private ThemeMode activeThemeMode;
    private MorphismStyle activeStyle;
    private final OnColorThemeSelectedListener listener;

    public ColorThemePreviewAdapter(ColorTheme activeTheme, ThemeMode activeThemeMode, MorphismStyle activeStyle, OnColorThemeSelectedListener listener) {
        this.activeTheme = activeTheme;
        this.activeThemeMode = activeThemeMode;
        this.activeStyle = activeStyle;
        this.listener = listener;
    }

    public void updateState(ColorTheme activeTheme, ThemeMode activeThemeMode, MorphismStyle activeStyle) {
        this.activeTheme = activeTheme;
        this.activeThemeMode = activeThemeMode;
        this.activeStyle = activeStyle;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_color_theme_preview, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ColorTheme theme = themes.get(position);
        holder.bind(theme, theme == activeTheme, activeThemeMode, activeStyle, listener);
    }

    @Override
    public int getItemCount() {
        return themes.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final MaterialCardView cardColorPreview;
        private final View swatchBg;
        private final View swatchSurface;
        private final View swatchAccent;
        private final TextView tvColorTitle;
        private final TextView tvColorDesc;
        private final LinearLayout layoutActiveColorBadge;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardColorPreview = itemView.findViewById(R.id.cardColorPreview);
            swatchBg = itemView.findViewById(R.id.swatchBg);
            swatchSurface = itemView.findViewById(R.id.swatchSurface);
            swatchAccent = itemView.findViewById(R.id.swatchAccent);
            tvColorTitle = itemView.findViewById(R.id.tvColorTitle);
            tvColorDesc = itemView.findViewById(R.id.tvColorDesc);
            layoutActiveColorBadge = itemView.findViewById(R.id.layoutActiveColorBadge);
        }

        public void bind(ColorTheme colorTheme, boolean isSelected, ThemeMode themeMode, MorphismStyle morphismStyle, OnColorThemeSelectedListener listener) {
            Context context = itemView.getContext();
            tvColorTitle.setText(colorTheme.getTitleResId());
            tvColorDesc.setText(colorTheme.getDescResId());

            boolean isNight = MorphismThemeManager.isNightMode(context, themeMode);
            DesignTokens previewTokens = new DesignTokens(context, colorTheme, morphismStyle, isNight);

            MorphismThemeManager.getInstance().applyToCard(cardColorPreview, previewTokens);

            // Swatches
            setCircleColor(swatchBg, previewTokens.getBackgroundColor(), previewTokens.getStrokeColor());
            setCircleColor(swatchSurface, previewTokens.getSurfaceColor(), previewTokens.getStrokeColor());
            setCircleColor(swatchAccent, previewTokens.getAccentColor(), previewTokens.getStrokeColor());

            tvColorTitle.setTextColor(previewTokens.getTextPrimaryColor());
            tvColorDesc.setTextColor(previewTokens.getTextSecondaryColor());

            if (isSelected) {
                cardColorPreview.setStrokeColor(previewTokens.getAccentColor());
                cardColorPreview.setStrokeWidth(Math.max(2, Math.round(3 * context.getResources().getDisplayMetrics().density)));
                layoutActiveColorBadge.setVisibility(View.VISIBLE);
            } else {
                layoutActiveColorBadge.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onColorThemeSelected(colorTheme);
                }
            });
        }

        private void setCircleColor(View circle, int color, int strokeColor) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(color);
            drawable.setStroke(1, strokeColor);
            circle.setBackground(drawable);
        }
    }
}
