package com.psthetech.swara.ui.share;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.psthetech.swara.R;
import com.psthetech.swara.data.repository.ArtworkRepository;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.glass.LiquidGlassRenderer;
import com.psthetech.swara.ui.viewmodel.PlaybackViewModel;
import com.psthetech.swara.util.ArtworkHelper;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Previews a 9:16 share card for the current song and sends it to WhatsApp, an Instagram
 * story, Instagram messages, the system share sheet, or saves it to the gallery.
 */
public class ShareCardBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "ShareCardBottomSheet";

    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Nullable private Song song;
    @Nullable private Bitmap card;
    @Nullable private Uri cardUri;
    @Nullable private CustomTarget<Bitmap> artTarget;

    private ShapeableImageView ivPreview;
    private ProgressBar progress;

    public static ShareCardBottomSheet newInstance() {
        return new ShareCardBottomSheet();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) sheet.setBackgroundColor(Color.TRANSPARENT);
            dialog.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            dialog.getBehavior().setSkipCollapsed(true);
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_share_card, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        float density = getResources().getDisplayMetrics().density;

        // Dark material, consistent with Now Playing; only top corners rounded.
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(28, 28, 30));
        float r = 14f * density;
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        view.setBackground(bg);
        ((TextView) view.findViewById(R.id.tvShareCardTitle)).setTextColor(Color.WHITE);

        ivPreview = view.findViewById(R.id.ivCardPreview);
        ivPreview.setShapeAppearanceModel(ShapeAppearanceModel.builder()
                .setAllCornerSizes(16f * density).build());
        progress = view.findViewById(R.id.progressCard);

        PlaybackViewModel vm = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);
        song = vm.getCurrentSong().getValue();
        if (song == null) {
            dismissAllowingStateLoss();
            return;
        }

        LinearLayout targets = view.findViewById(R.id.layoutTargets);
        addTarget(targets, R.drawable.ic_share, getString(R.string.share_card_whatsapp), 0xFF25D366, this::toWhatsApp);
        addTarget(targets, R.drawable.ic_share_card, getString(R.string.share_card_ig_story), 0xFFE1306C, this::toInstagramStory);
        addTarget(targets, R.drawable.ic_share, getString(R.string.share_card_ig_message), 0xFF833AB4, this::toInstagram);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            addTarget(targets, R.drawable.ic_download, getString(R.string.share_card_save), 0xFF3A3A3C, this::saveToGallery);
        }
        addTarget(targets, R.drawable.ic_more_vert, getString(R.string.share_card_more), 0xFF3A3A3C, this::toChooser);

        loadArtworkAndRender(song);
    }

    private void addTarget(LinearLayout parent, @DrawableRes int icon, String label, int color, Runnable action) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout item = new LinearLayout(requireContext());
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (84 * d), ViewGroup.LayoutParams.WRAP_CONTENT);
        item.setLayoutParams(lp);
        item.setClickable(true);
        item.setFocusable(true);
        item.setContentDescription(label);

        ImageView iv = new ImageView(requireContext());
        int size = (int) (58 * d);
        iv.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(color);
        iv.setBackground(circle);
        iv.setImageResource(icon);
        iv.setColorFilter(Color.WHITE);
        iv.setScaleType(ImageView.ScaleType.CENTER);
        iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        TextView tv = new TextView(requireContext());
        tv.setText(label);
        tv.setTextColor(Color.argb(200, 255, 255, 255));
        tv.setTextSize(12f);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(2);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = (int) (8 * d);
        tv.setLayoutParams(tlp);
        tv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        item.addView(iv);
        item.addView(tv);
        item.setOnClickListener(v -> {
            if (card == null || cardUri == null) return; // still rendering
            action.run();
        });
        LiquidGlassRenderer.attachPressAnimation(item);
        parent.addView(item);
    }

    private void loadArtworkAndRender(@NonNull Song s) {
        Object source;
        File custom = new ArtworkRepository(requireContext()).getCustomArtworkStore().getArtworkFile(s.getId());
        if (custom.exists()) {
            source = custom;
        } else {
            Uri album = ArtworkRepository.getAlbumArtUri(s.getAlbumId());
            source = album != null ? album : (Object) R.drawable.ic_artwork_fallback;
        }
        artTarget = new CustomTarget<Bitmap>(900, 900) {
            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> t) {
                extractTintThenRender(s, resource);
            }

            @Override
            public void onLoadFailed(@Nullable android.graphics.drawable.Drawable errorDrawable) {
                extractTintThenRender(s, null);
            }

            @Override
            public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) { }
        };
        Glide.with(this).asBitmap().load(source).centerCrop().into(artTarget);
    }

    private void extractTintThenRender(@NonNull Song s, @Nullable Bitmap art) {
        int fallback = 0xFFD4AF5A;
        int tint = art != null ? ArtworkHelper.findVibrantColor(art, fallback) : fallback;
        // Copy: Glide may recycle its bitmap once the target is cleared.
        Bitmap artCopy = art != null ? art.copy(Bitmap.Config.ARGB_8888, false) : null;
        String footer = getString(R.string.share_listening_on);
        android.content.Context appCtx = requireContext().getApplicationContext();
        renderExecutor.execute(() -> {
            Bitmap rendered;
            Uri uri;
            try {
                rendered = ShareCardRenderer.render(artCopy, s.getTitle(), s.getArtist(), footer, tint);
                uri = SongShareHelper.writeCardToCache(appCtx, rendered);
            } catch (Exception | OutOfMemoryError e) {
                main.post(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), R.string.share_card_failed, Toast.LENGTH_SHORT).show();
                    dismissAllowingStateLoss();
                });
                return;
            }
            main.post(() -> {
                if (!isAdded() || getView() == null) return;
                card = rendered;
                cardUri = uri;
                progress.setVisibility(View.GONE);
                ivPreview.setImageBitmap(rendered);
                ivPreview.setAlpha(0f);
                ivPreview.setScaleX(0.96f);
                ivPreview.setScaleY(0.96f);
                ivPreview.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(LiquidGlassRenderer.isReducedMotion(requireContext()) ? 0 : 320)
                        .setInterpolator(LiquidGlassRenderer.SPRING_CRITICAL).start();
            });
        });
    }

    // ── Destinations ─────────────────────────────────────────────────────────────

    private void toWhatsApp() {
        String pkg = SongShareHelper.isInstalled(requireContext(), SongShareHelper.PKG_WHATSAPP)
                ? SongShareHelper.PKG_WHATSAPP
                : SongShareHelper.isInstalled(requireContext(), SongShareHelper.PKG_WHATSAPP_BUSINESS)
                    ? SongShareHelper.PKG_WHATSAPP_BUSINESS : null;
        if (pkg == null || !SongShareHelper.shareCardToApp(requireActivity(), cardUri, pkg, SongShareHelper.caption(song))) {
            notInstalled(getString(R.string.share_card_whatsapp));
            return;
        }
        dismissAllowingStateLoss();
    }

    private void toInstagramStory() {
        if (!SongShareHelper.isInstalled(requireContext(), SongShareHelper.PKG_INSTAGRAM)
                || !SongShareHelper.shareCardToInstagramStory(requireActivity(), cardUri)) {
            notInstalled("Instagram");
            return;
        }
        dismissAllowingStateLoss();
    }

    private void toInstagram() {
        if (!SongShareHelper.isInstalled(requireContext(), SongShareHelper.PKG_INSTAGRAM)
                || !SongShareHelper.shareCardToApp(requireActivity(), cardUri, SongShareHelper.PKG_INSTAGRAM, null)) {
            notInstalled("Instagram");
            return;
        }
        dismissAllowingStateLoss();
    }

    private void toChooser() {
        SongShareHelper.shareCardWithChooser(requireActivity(), cardUri, SongShareHelper.caption(song));
        dismissAllowingStateLoss();
    }

    private void saveToGallery() {
        String name = "Swara_" + song.getTitle().replaceAll("[^A-Za-z0-9]+", "_") + "_" + System.currentTimeMillis();
        boolean ok = SongShareHelper.saveCardToGallery(requireContext(), card, name);
        Toast.makeText(requireContext(), ok ? R.string.share_card_saved : R.string.share_card_failed,
                Toast.LENGTH_SHORT).show();
    }

    private void notInstalled(String app) {
        Toast.makeText(requireContext(), getString(R.string.share_app_not_installed, app), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        if (artTarget != null) {
            try {
                Glide.with(this).clear(artTarget);
            } catch (Exception ignored) { /* detached */ }
            artTarget = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        renderExecutor.shutdownNow();
        super.onDestroy();
    }
}
