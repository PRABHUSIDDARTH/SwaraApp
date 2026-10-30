package com.psthetech.swara.ui.settings;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.MorphismStyle;
import com.psthetech.swara.domain.model.ThemeMode;
import com.psthetech.swara.ui.theme.DesignTokens;
import com.psthetech.swara.ui.theme.MorphismThemeManager;

import java.util.Arrays;
import java.util.List;

public class MorphismPreviewAdapter extends RecyclerView.Adapter<MorphismPreviewAdapter.ViewHolder> {

    public interface OnMorphismSelectedListener {
        void onMorphismSelected(MorphismStyle style);
    }

    private final List<MorphismStyle> styles = Arrays.asList(MorphismStyle.values());
    private MorphismStyle activeStyle;
    private ThemeMode activeThemeMode;
    private final OnMorphismSelectedListener listener;

    public MorphismPreviewAdapter(MorphismStyle activeStyle, ThemeMode activeThemeMode, OnMorphismSelectedListener listener) {
        this.activeStyle = activeStyle;
        this.activeThemeMode = activeThemeMode;
        this.listener = listener;
    }

    public void updateState(MorphismStyle activeStyle, ThemeMode activeThemeMode) {
        this.activeStyle = activeStyle;
        this.activeThemeMode = activeThemeMode;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_morphism_preview, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MorphismStyle style = styles.get(position);
        holder.bind(style, style == activeStyle, activeThemeMode, listener);
    }

    @Override
    public int getItemCount() {
        return styles.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final MaterialCardView cardPreview;
        private final TextView tvStyleTitle;
        private final TextView tvStyleDesc;
        private final LinearLayout layoutActiveBadge;
        private final FrameLayout boxPreviewSurface;
        private final ImageView ivPreviewIcon;
        private final TextView tvPreviewSample;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardPreview = itemView.findViewById(R.id.cardPreview);
            tvStyleTitle = itemView.findViewById(R.id.tvStyleTitle);
            tvStyleDesc = itemView.findViewById(R.id.tvStyleDesc);
            layoutActiveBadge = itemView.findViewById(R.id.layoutActiveBadge);
            boxPreviewSurface = itemView.findViewById(R.id.boxPreviewSurface);
            ivPreviewIcon = itemView.findViewById(R.id.ivPreviewIcon);
            tvPreviewSample = itemView.findViewById(R.id.tvPreviewSample);
        }

        public void bind(MorphismStyle style, boolean isSelected, ThemeMode themeMode, OnMorphismSelectedListener listener) {
            Context context = itemView.getContext();
            tvStyleTitle.setText(style.getTitleResId());
            tvStyleDesc.setText(style.getDescResId());

            boolean isNight = MorphismThemeManager.isNightMode(context, themeMode);
            DesignTokens previewTokens = new DesignTokens(context, style, isNight);

            // Style the card background according to previewTokens
            MorphismThemeManager.getInstance().applyToCard(cardPreview, previewTokens);

            // Highlight border if selected
            if (isSelected) {
                cardPreview.setStrokeColor(previewTokens.getAccentColor());
                cardPreview.setStrokeWidth(Math.max(2, Math.round(3 * context.getResources().getDisplayMetrics().density)));
                layoutActiveBadge.setVisibility(View.VISIBLE);
            } else {
                layoutActiveBadge.setVisibility(View.GONE);
            }

            // Preview inner surface box styling
            GradientDrawable innerDrawable = (GradientDrawable) previewTokens.createSurfaceVariantDrawable(context);
            boxPreviewSurface.setBackground(innerDrawable);

            tvStyleTitle.setTextColor(previewTokens.getPrimaryTextColor());
            tvStyleDesc.setTextColor(previewTokens.getSecondaryTextColor());
            tvPreviewSample.setTextColor(previewTokens.getPrimaryTextColor());

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMorphismSelected(style);
                }
            });
        }
    }
}
