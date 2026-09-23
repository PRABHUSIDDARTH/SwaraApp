package com.psthetech.swara.ui.miniplayer;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.glass.LiquidGlassRenderer;
import com.psthetech.swara.ui.viewmodel.PlaybackViewModel;
import com.psthetech.swara.util.ArtworkHelper;

/**
 * MiniPlayer — persistent playback bar shown above BottomNavigationView.
 *
 * Observes the Activity-scoped PlaybackViewModel.
 * Tapping opens NowPlayingFragment.
 * Play/Pause and Next buttons delegate to PlaybackViewModel.
 */
public class MiniPlayerFragment extends Fragment {

    private PlaybackViewModel playbackViewModel;
    private ImageView ivArtwork;
    private TextView tvTitle;
    private TextView tvArtist;
    private ImageView btnPlayPause;
    private ImageView btnNext;
    private View progressLine;
    private View rootView;
    private com.psthetech.swara.ui.widget.AudioWaveView miniAudioWaveView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mini_player, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rootView = view;
        ivArtwork = view.findViewById(R.id.ivArtwork);
        tvTitle = view.findViewById(R.id.tvTitle);
        tvArtist = view.findViewById(R.id.tvArtist);
        btnPlayPause = view.findViewById(R.id.btnPlayPause);
        btnNext = view.findViewById(R.id.btnNext);
        progressLine = view.findViewById(R.id.progressLine);
        miniAudioWaveView = view.findViewById(R.id.miniAudioWaveView);
        if (miniAudioWaveView != null) {
            miniAudioWaveView.setCompact(true);
        }

        // Activity-scoped ViewModel
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        // Observe current song
        playbackViewModel.getCurrentSong().observe(getViewLifecycleOwner(), this::updateSong);

        // Observe playing state
        playbackViewModel.getIsPlaying().observe(getViewLifecycleOwner(), playing -> {
            boolean isPlaying = Boolean.TRUE.equals(playing);
            btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
            btnPlayPause.setContentDescription(getString(isPlaying ? R.string.pause : R.string.play));
            if (miniAudioWaveView != null) {
                miniAudioWaveView.setPlaying(isPlaying);
            }
        });

        // Observe progress for the top line
        playbackViewModel.getCurrentPositionMs().observe(getViewLifecycleOwner(), pos -> {
            updateProgressLine(pos, playbackViewModel.getDurationMs().getValue());
        });

        playbackViewModel.getDurationMs().observe(getViewLifecycleOwner(), duration -> {
            updateProgressLine(playbackViewModel.getCurrentPositionMs().getValue(), duration);
        });

        // Observe Morphism design tokens
        com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                .getDesignTokens().observe(getViewLifecycleOwner(), tokens -> {
                    if (tokens == null || getView() == null) return;

                    // Apply glass mini-player surface (fully-rounded, translucent, highlight gradient)
                    GradientDrawable glassDrawable = tokens.createGlassMiniPlayerDrawable(requireContext());
                    rootView.setBackground(glassDrawable);
                    rootView.setClipToOutline(true);

                    // Round the artwork corners using a ShapeAppearance-style outline
                    if (ivArtwork != null) {
                        float artworkCornerPx = 8 * requireContext().getResources().getDisplayMetrics().density;
                        android.graphics.drawable.GradientDrawable artworkBg =
                                new android.graphics.drawable.GradientDrawable();
                        artworkBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                        artworkBg.setColor(tokens.getSurfaceElevatedColor());
                        artworkBg.setCornerRadius(artworkCornerPx);
                        ivArtwork.setBackground(artworkBg);
                        ivArtwork.setClipToOutline(true);
                    }

                    // Text colors
                    tvTitle.setTextColor(tokens.getTextPrimaryColor());
                    tvArtist.setTextColor(tokens.getTextSecondaryColor());

                    // Play/pause button — glass pill with accent tint
                    if (btnPlayPause != null) {
                        GradientDrawable btnBg = tokens.createGlassPillDrawable(requireContext());
                        btnPlayPause.setBackground(btnBg);
                        btnPlayPause.setColorFilter(tokens.getReadableAccentColor());
                    }

                    // Next button — simple accent tint on transparent
                    if (btnNext != null) {
                        btnNext.setColorFilter(tokens.getReadableAccentColor());
                    }

                    // Progress line color
                    if (progressLine != null) {
                        progressLine.setBackgroundColor(tokens.getAccentColor());
                    }

                    if (miniAudioWaveView != null) {
                        miniAudioWaveView.setDesignTokens(tokens);
                    }
                });

        // Tap the mini-player → open Now Playing.
        // MiniPlayerFragment lives outside the nav graph (in mini_player_container),
        // so we must reach the NavHostFragment via the host activity's FragmentManager.
        rootView.setOnClickListener(v -> {
            NavHostFragment navHostFragment = (NavHostFragment)
                    requireActivity().getSupportFragmentManager()
                            .findFragmentById(R.id.nav_host_fragment);
            if (navHostFragment != null) {
                navHostFragment.getNavController().navigate(R.id.nowPlayingFragment);
            }
        });

        // Attach press animation to the entire mini-player (respects reduced-motion)
        LiquidGlassRenderer.attachPressAnimation(rootView);

        btnPlayPause.setOnClickListener(v -> {
            Boolean isPlaying = playbackViewModel.getIsPlaying().getValue();
            if (Boolean.TRUE.equals(isPlaying)) {
                playbackViewModel.pause();
            } else {
                playbackViewModel.resume();
            }
        });

        btnNext.setOnClickListener(v -> playbackViewModel.skipToNext());
    }

    private void updateSong(@Nullable Song song) {
        if (song == null) return;
        tvTitle.setText(song.getTitle());
        tvArtist.setText(song.getArtist());
        ArtworkHelper.loadSongArtWithCrossfade(requireContext(), song, ivArtwork);
    }

    private void updateProgressLine(@Nullable Long pos, @Nullable Long duration) {
        if (pos == null || duration == null || duration <= 0) return;
        float fraction = Math.max(0f, Math.min(1f, (float) (long) pos / duration));
        if (rootView != null && progressLine != null) {
            int totalWidth = rootView.getWidth();
            if (totalWidth > 0) {
                ViewGroup.LayoutParams lp = progressLine.getLayoutParams();
                lp.width = (int) (totalWidth * fraction);
                progressLine.setLayoutParams(lp);
            }
        }
    }
}
