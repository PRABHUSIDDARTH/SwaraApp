package com.psthetech.swara.ui.nowplaying;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.queue.QueueFragment;
import com.psthetech.swara.ui.viewmodel.FavoritesViewModel;
import com.psthetech.swara.ui.viewmodel.PlaybackViewModel;
import com.psthetech.swara.util.ArtworkHelper;
import com.psthetech.swara.util.EqualizerManager;
import com.psthetech.swara.util.SleepTimerManager;
import com.psthetech.swara.util.TimeFormatter;

public class NowPlayingFragment extends BottomSheetDialogFragment {

    private PlaybackViewModel playbackViewModel;
    private FavoritesViewModel favoritesViewModel;

    private ImageView ivArtwork;
    private TextView tvTitle;
    private TextView tvArtist;
    private SeekBar seekBar;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private ImageView btnPlayPause;
    private ImageView btnPrevious;
    private ImageView btnNext;
    private ImageView btnShuffle;
    private ImageView btnRepeat;
    private ImageView btnFavorite;
    private ImageView btnQueue;
    private ImageView btnCollapse;
    private ImageView btnSleepTimer;
    private ImageView btnEqualizer;

    private boolean isUserSeeking = false;
    private Song currentSong;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bsd = (BottomSheetDialog) d;
            View bottomSheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
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
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_now_playing, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);
        favoritesViewModel = new ViewModelProvider(requireActivity()).get(FavoritesViewModel.class);

        ivArtwork = view.findViewById(R.id.ivArtwork);
        tvTitle = view.findViewById(R.id.tvTitle);
        tvArtist = view.findViewById(R.id.tvArtist);
        seekBar = view.findViewById(R.id.seekBar);
        tvCurrentTime = view.findViewById(R.id.tvCurrentTime);
        tvTotalTime = view.findViewById(R.id.tvTotalTime);
        btnPlayPause = view.findViewById(R.id.btnPlayPause);
        btnPrevious = view.findViewById(R.id.btnPrevious);
        btnNext = view.findViewById(R.id.btnNext);
        btnShuffle = view.findViewById(R.id.btnShuffle);
        btnRepeat = view.findViewById(R.id.btnRepeat);
        btnFavorite   = view.findViewById(R.id.btnFavorite);
        btnQueue      = view.findViewById(R.id.btnQueue);
        btnCollapse   = view.findViewById(R.id.btnCollapse);
        btnSleepTimer = view.findViewById(R.id.btnSleepTimer);
        btnEqualizer  = view.findViewById(R.id.btnEqualizer);

        if (btnCollapse != null) btnCollapse.setOnClickListener(v -> dismiss());

        ivArtwork.setOnClickListener(v -> {
            if (currentSong != null && getActivity() instanceof com.psthetech.swara.ui.MainActivity) {
                ((com.psthetech.swara.ui.MainActivity) getActivity()).promptEditArtwork(currentSong);
            }
        });

        setupListeners();
        observeViewModel();
    }

    private void setupListeners() {
        btnPlayPause.setOnClickListener(v -> playbackViewModel.togglePlayPause());
        btnPrevious.setOnClickListener(v -> playbackViewModel.skipToPrevious());
        btnNext.setOnClickListener(v -> playbackViewModel.skipToNext());
        btnShuffle.setOnClickListener(v -> playbackViewModel.toggleShuffle());
        btnRepeat.setOnClickListener(v -> playbackViewModel.toggleRepeatMode());

        btnFavorite.setOnClickListener(v -> {
            if (currentSong != null) {
                favoritesViewModel.toggleFavorite(currentSong);
            }
        });

        btnQueue.setOnClickListener(v -> {
            QueueFragment queueFragment = new QueueFragment();
            queueFragment.show(getParentFragmentManager(), "QueueFragment");
        });

        if (btnSleepTimer != null) {
            btnSleepTimer.setOnClickListener(v -> {
                SleepTimerDialog dialog = SleepTimerDialog.newInstance(
                        () -> playbackViewModel.pause());
                dialog.show(getParentFragmentManager(), SleepTimerDialog.TAG);
            });

            // Highlight the button when a timer is active
            SleepTimerManager.getInstance().getActive().observe(getViewLifecycleOwner(), isActive -> {
                if (isActive != null) {
                    com.psthetech.swara.ui.theme.DesignTokens tokens =
                            com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance().getCurrentTokens();
                    if (tokens != null) {
                        btnSleepTimer.setColorFilter(
                                Boolean.TRUE.equals(isActive)
                                        ? tokens.getAccentColor()
                                        : tokens.getIconSecondaryColor());
                    }
                }
            });
        }

        if (btnEqualizer != null) {
            btnEqualizer.setOnClickListener(v -> {
                if (getActivity() != null) {
                    EqualizerManager.openSystemEqualizer(
                            getActivity(), playbackViewModel.getAudioSessionId());
                }
            });
        }

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    tvCurrentTime.setText(TimeFormatter.formatMs(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                isUserSeeking = false;
                playbackViewModel.seekTo(sb.getProgress());
            }
        });
    }

    private void observeViewModel() {
        playbackViewModel.getCurrentSong().observe(getViewLifecycleOwner(), song -> {
            this.currentSong = song;
            if (song != null) {
                tvTitle.setText(song.getTitle());
                tvArtist.setText(song.getArtist());
                if (song.getDuration() > 0) {
                    seekBar.setMax((int) song.getDuration());
                    tvTotalTime.setText(TimeFormatter.formatMs(song.getDuration()));
                }
                ArtworkHelper.loadNowPlayingArt(requireContext(), song, ivArtwork);
                checkIsFavorite(song.getId());
            }
        });

        playbackViewModel.getDurationMs().observe(getViewLifecycleOwner(), duration -> {
            if (duration != null && duration > 0) {
                seekBar.setMax(duration.intValue());
                tvTotalTime.setText(TimeFormatter.formatMs(duration));
            }
        });

        playbackViewModel.getIsPlaying().observe(getViewLifecycleOwner(), isPlaying -> {
            btnPlayPause.setImageResource(Boolean.TRUE.equals(isPlaying) ? R.drawable.ic_pause : R.drawable.ic_play);
            btnPlayPause.setContentDescription(getString(Boolean.TRUE.equals(isPlaying) ? R.string.pause : R.string.play));
        });

        playbackViewModel.getCurrentPosition().observe(getViewLifecycleOwner(), position -> {
            if (!isUserSeeking && position != null) {
                long pos = Math.max(0, position);
                int max = seekBar.getMax();
                if (max > 0) {
                    pos = Math.min(pos, max);
                }
                seekBar.setProgress((int) pos);
                tvCurrentTime.setText(TimeFormatter.formatMs(pos));
            }
        });

        playbackViewModel.getShuffleMode().observe(getViewLifecycleOwner(), enabled -> {
            boolean active = Boolean.TRUE.equals(enabled);
            btnShuffle.setAlpha(active ? 1.0f : 0.4f);
        });

        playbackViewModel.getRepeatMode().observe(getViewLifecycleOwner(), mode -> {
            if (mode != null) {
                switch (mode) {
                    case 0:
                        btnRepeat.setImageResource(R.drawable.ic_repeat);
                        btnRepeat.setAlpha(0.4f);
                        break;
                    case 1:
                        btnRepeat.setImageResource(R.drawable.ic_repeat_one);
                        btnRepeat.setAlpha(1.0f);
                        break;
                    case 2:
                        btnRepeat.setImageResource(R.drawable.ic_repeat);
                        btnRepeat.setAlpha(1.0f);
                        break;
                }
            }
        });

        favoritesViewModel.getFavoriteSongIds().observe(getViewLifecycleOwner(), ids -> {
            if (currentSong != null) {
                checkIsFavorite(currentSong.getId());
            }
        });

        // Observe Morphism design tokens for Now Playing styling
        com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                .getDesignTokens().observe(getViewLifecycleOwner(), tokens -> {
                    if (tokens == null || getView() == null) return;
                    View view = getView();
                    view.setBackground(tokens.createAmbientDrawable());
                    ((TextView) view.findViewById(R.id.nowPlayingHeader)).setTextColor(tokens.getSecondaryTextColor());
                    ivArtwork.setBackground(tokens.createSurfaceVariantDrawable(requireContext()));
                    tvTitle.setTextColor(tokens.getPrimaryTextColor());
                    tvArtist.setTextColor(tokens.getSecondaryTextColor());
                    tvCurrentTime.setTextColor(tokens.getSecondaryTextColor());
                    tvTotalTime.setTextColor(tokens.getSecondaryTextColor());

                    btnPlayPause.setBackground(tokens.createSurfaceVariantDrawable(requireContext()));
                    btnPlayPause.setColorFilter(tokens.getPrimaryTextColor());
                    seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(tokens.getAccentColor()));
                    seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(tokens.getPrimaryTextColor()));
                    seekBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(tokens.getSurfaceVariantColor()));
                    btnShuffle.setColorFilter(tokens.getAccentColor());
                    btnRepeat.setColorFilter(tokens.getAccentColor());
                    btnPrevious.setColorFilter(tokens.getAccentColor());
                    btnNext.setColorFilter(tokens.getAccentColor());
                    btnFavorite.setColorFilter(tokens.getAccentColor());
                    btnQueue.setColorFilter(tokens.getAccentColor());
                    if (btnCollapse != null) btnCollapse.setColorFilter(tokens.getPrimaryTextColor());
                });
    }

    private void checkIsFavorite(long songId) {
        favoritesViewModel.isFavorite(songId).observe(getViewLifecycleOwner(), isFav -> {
            boolean favorite = Boolean.TRUE.equals(isFav);
            btnFavorite.setImageResource(favorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
            btnFavorite.setAlpha(favorite ? 1.0f : 0.7f);
        });
    }
}
