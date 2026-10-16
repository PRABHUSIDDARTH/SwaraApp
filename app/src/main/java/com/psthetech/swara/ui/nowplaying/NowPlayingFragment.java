package com.psthetech.swara.ui.nowplaying;

import android.graphics.Bitmap;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import androidx.lifecycle.ViewModelProvider;

import com.psthetech.swara.R;
import com.psthetech.swara.util.AudioOutputManager;
import com.psthetech.swara.data.repository.ArtworkRepository;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.nowplaying.AudioOutputBottomSheet;
import com.psthetech.swara.ui.queue.QueueFragment;
import com.psthetech.swara.ui.nowplaying.SleepTimerDialog;
import com.psthetech.swara.ui.viewmodel.FavoritesViewModel;
import com.psthetech.swara.ui.viewmodel.PlaybackViewModel;
import com.psthetech.swara.util.ArtworkHelper;
import com.psthetech.swara.util.EqualizerManager;
import com.psthetech.swara.util.SleepTimerManager;
import com.psthetech.swara.util.TimeFormatter;

/**
 * NowPlayingFragment — Apple Music inspired Now Playing sheet.
 *
 * ARCHITECTURE:
 *  - Observes PlaybackViewModel (the ONLY playback source of truth)
 *  - CircularArtworkView: rotating artwork disc (spins only while playing) with a progress ring
 *  - Artwork-tinted, blurred backdrop (RenderEffect on API 31+, tinted scrim everywhere)
 *  - AppleScrubberView provides flat direct-manipulation seeking via PlaybackViewModel.seekTo()
 */
public class NowPlayingFragment extends BottomSheetDialogFragment {

    private PlaybackViewModel playbackViewModel;
    private FavoritesViewModel favoritesViewModel;

    // ── Artwork ───────────────────────────────────────────────────────────────────
    private com.psthetech.swara.ui.widget.CircularArtworkView circularArtworkView;
    private ImageView ivBackdrop;
    private View viewBackdropScrim;
    private int artworkTint = android.graphics.Color.TRANSPARENT;

    // ── Song info ─────────────────────────────────────────────────────────────────
    private TextView tvTitle;
    private TextView tvArtist;
    private TextView tvAlbum;

    // ── Horizontal seek bar ───────────────────────────────────────────────────────
    private com.psthetech.swara.ui.widget.AppleScrubberView scrubber;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;

    // ── Playback controls ─────────────────────────────────────────────────────────
    private ImageView btnPlayPause;
    private ImageView btnPrevious;
    private ImageView btnNext;
    private ImageView btnShuffle;
    private ImageView btnRepeat;
    private ImageView btnFavorite;
    private ImageView btnQueue;
    private ImageView btnCollapse;
    private ImageView btnSleepTimer;
    private ImageView btnShare;
    private ImageView btnShareCard;
    private ImageView btnEqualizer;
    private ImageView btnAudioOutput;

    // ── Korokae Mode controls ──────────────────────────────────────────────────
    private View btnKorokae;
    private ImageView ivKorokaeIcon;
    private TextView tvKorokaeLabel;
    @Nullable private android.animation.ValueAnimator korokaeAnimator;

    // ── State ─────────────────────────────────────────────────────────────────────
    private boolean isUserSeeking = false;
    private Song currentSong;
    private long lastSongId = -1;

    // ── Glide target for circular artwork bitmap loading ─────────────────────────
    @Nullable private CustomTarget<Bitmap> artworkTarget;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bsd = (BottomSheetDialog) d;
            View bottomSheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                // Full-height sheet whose own content draws the dark artwork material.
                bottomSheet.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                android.view.ViewGroup.LayoutParams lp = bottomSheet.getLayoutParams();
                lp.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
                bottomSheet.setLayoutParams(lp);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                behavior.setHideable(true);
                behavior.setDraggable(true);
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

        // Find views
        final float sheetRadius = 14f * getResources().getDisplayMetrics().density;
        view.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline outline) {
                // Extend below the bottom edge so only the top corners are rounded.
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight() + Math.round(sheetRadius), sheetRadius);
            }
        });
        view.setClipToOutline(true);

        circularArtworkView = view.findViewById(R.id.circularArtworkView);
        ivBackdrop = view.findViewById(R.id.ivBackdrop);
        viewBackdropScrim = view.findViewById(R.id.viewBackdropScrim);
        if (ivBackdrop != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ivBackdrop.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                    90f, 90f, android.graphics.Shader.TileMode.CLAMP));
        }

        tvTitle = view.findViewById(R.id.tvTitle);
        tvArtist = view.findViewById(R.id.tvArtist);
        tvAlbum = view.findViewById(R.id.tvAlbum);
        scrubber = view.findViewById(R.id.scrubber);
        tvTitle.setSelected(true); // enables marquee for long titles
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
        btnShare      = view.findViewById(R.id.btnShare);
        btnShareCard  = view.findViewById(R.id.btnShareCard);
        btnEqualizer  = view.findViewById(R.id.btnEqualizer);
        btnAudioOutput = view.findViewById(R.id.btnAudioOutput);
        btnKorokae    = view.findViewById(R.id.btnKorokae);
        ivKorokaeIcon = view.findViewById(R.id.ivKorokaeIcon);
        tvKorokaeLabel = view.findViewById(R.id.tvKorokaeLabel);

        if (btnKorokae != null) {
            com.psthetech.swara.ui.glass.LiquidGlassRenderer.attachPressAnimation(btnKorokae);
            btnKorokae.setOnClickListener(v -> playbackViewModel.toggleKorokaeMode());
        }

        if (btnCollapse != null) btnCollapse.setOnClickListener(v -> dismiss());

        // Artwork tap = edit artwork; dragging around the ring seeks (retro "scratch").
        if (circularArtworkView != null) {
            circularArtworkView.setOnClickListener(v -> {
                if (currentSong != null && getActivity() instanceof com.psthetech.swara.ui.MainActivity) {
                    ((com.psthetech.swara.ui.MainActivity) getActivity()).promptEditArtwork(currentSong);
                }
            });
            circularArtworkView.setSeekListener(new com.psthetech.swara.ui.widget.CircularArtworkView.SeekListener() {
                @Override
                public void onSeekDelta(float deltaSeconds) {
                    Long currentPos = playbackViewModel.getCurrentPositionMs().getValue();
                    Long duration = playbackViewModel.getDurationMs().getValue();
                    if (currentPos == null || duration == null || duration <= 0) return;
                    long newPos = Math.max(0, Math.min(currentPos + (long) (deltaSeconds * 1000f), duration));
                    playbackViewModel.seekTo(newPos);
                    tvCurrentTime.setText(TimeFormatter.formatMs(newPos));
                    if (scrubber != null) scrubber.setProgress(newPos);
                    circularArtworkView.setProgress((float) newPos / (float) duration);
                }

                @Override
                public void onSeekEnd() { }
            });
        }

        // Attach micro-interaction animations to all buttons
        com.psthetech.swara.ui.glass.LiquidGlassRenderer.attachPressAnimation(btnPlayPause);
        com.psthetech.swara.ui.glass.LiquidGlassRenderer.attachPressAnimation(btnPrevious);
        com.psthetech.swara.ui.glass.LiquidGlassRenderer.attachPressAnimation(btnNext);
        com.psthetech.swara.ui.glass.LiquidGlassRenderer.attachPressAnimation(btnShuffle);
        com.psthetech.swara.ui.glass.LiquidGlassRenderer.attachPressAnimation(btnRepeat);
        com.psthetech.swara.ui.glass.LiquidGlassRenderer.attachPressAnimation(btnFavorite);
        com.psthetech.swara.ui.glass.LiquidGlassRenderer.attachPressAnimation(btnQueue);
        if (btnSleepTimer != null) com.psthetech.swara.ui.glass.LiquidGlassRenderer.attachPressAnimation(btnSleepTimer);
        if (btnEqualizer != null) com.psthetech.swara.ui.glass.LiquidGlassRenderer.attachPressAnimation(btnEqualizer);
        if (btnAudioOutput != null) com.psthetech.swara.ui.glass.LiquidGlassRenderer.attachPressAnimation(btnAudioOutput);
        if (btnCollapse != null) com.psthetech.swara.ui.glass.LiquidGlassRenderer.attachPressAnimation(btnCollapse);
        if (btnShare != null) com.psthetech.swara.ui.glass.LiquidGlassRenderer.attachPressAnimation(btnShare);
        if (btnShareCard != null) com.psthetech.swara.ui.glass.LiquidGlassRenderer.attachPressAnimation(btnShareCard);

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
                boolean wasFavorite = Boolean.TRUE.equals(favoritesViewModel.isFavorite(currentSong.getId()).getValue());
                favoritesViewModel.toggleFavorite(currentSong);
                com.psthetech.swara.ui.theme.DesignTokens tokens =
                        com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance().getCurrentTokens();
                if (!wasFavorite) {
                    com.psthetech.swara.util.FavoriteAnimationHelper.animateFavoriteAdd(btnFavorite,
                            tokens != null ? tokens.getFavoriteActiveColor() : 0xFFC9A84C);
                } else {
                    com.psthetech.swara.util.FavoriteAnimationHelper.animateFavoriteRemove(btnFavorite);
                }
            }
        });

        if (btnShare != null) {
            // Tap: send the song file via Quick Share / Nearby. Long-press: full share sheet.
            btnShare.setOnClickListener(v -> {
                if (getActivity() != null) {
                    com.psthetech.swara.ui.share.SongShareHelper.shareSongNearby(requireActivity(), currentSong);
                }
            });
            btnShare.setOnLongClickListener(v -> {
                if (getActivity() != null) {
                    com.psthetech.swara.ui.share.SongShareHelper.shareSongWithChooser(requireActivity(), currentSong);
                }
                return true;
            });
        }

        if (btnShareCard != null) {
            btnShareCard.setOnClickListener(v -> {
                if (currentSong == null) return;
                com.psthetech.swara.ui.share.ShareCardBottomSheet.newInstance()
                        .show(getParentFragmentManager(), com.psthetech.swara.ui.share.ShareCardBottomSheet.TAG);
            });
        }

        btnQueue.setOnClickListener(v -> {
            QueueFragment queueFragment = new QueueFragment();
            queueFragment.show(getParentFragmentManager(), "QueueFragment");
        });

        if (btnSleepTimer != null) {
            btnSleepTimer.setOnClickListener(v -> {
                SleepTimerDialog dialog = SleepTimerDialog.newInstance();
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
                                        : android.graphics.Color.argb(166, 255, 255, 255));
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

        if (btnAudioOutput != null) {
            btnAudioOutput.setOnClickListener(v -> {
                AudioOutputBottomSheet sheet = AudioOutputBottomSheet.newInstance();
                sheet.show(getParentFragmentManager(), AudioOutputBottomSheet.TAG);
            });
        }

        scrubber.setOnScrubListener(new com.psthetech.swara.ui.widget.AppleScrubberView.OnScrubListener() {
            @Override
            public void onScrubStart(com.psthetech.swara.ui.widget.AppleScrubberView view) {
                isUserSeeking = true;
            }

            @Override
            public void onScrubMove(com.psthetech.swara.ui.widget.AppleScrubberView view, long positionMs) {
                tvCurrentTime.setText(TimeFormatter.formatMs(positionMs));
            }

            @Override
            public void onScrubEnd(com.psthetech.swara.ui.widget.AppleScrubberView view, long positionMs) {
                isUserSeeking = false;
                long max = view.getMax();
                long target = Math.max(0, max > 0 ? Math.min(positionMs, max) : positionMs);
                playbackViewModel.seekTo(target);
            }
        });
    }

    private void observeViewModel() {
        playbackViewModel.getCurrentSong().observe(getViewLifecycleOwner(), song -> {
            boolean isSongChange = (song == null && currentSong != null)
                    || (song != null && (currentSong == null || song.getId() != lastSongId));

            this.currentSong = song;

            if (song != null) {
                tvTitle.setText(song.getTitle());
                tvArtist.setText(song.getArtist());

                if (tvAlbum != null) {
                    String album = song.getAlbum();
                    if (album != null && !album.trim().isEmpty() && !album.equalsIgnoreCase("<unknown>")) {
                        tvAlbum.setText(album);
                        tvAlbum.setVisibility(View.VISIBLE);
                    } else {
                        tvAlbum.setVisibility(View.GONE);
                    }
                }

                if (song.getDuration() > 0 && scrubber != null) {
                    scrubber.setMax(song.getDuration());
                    tvTotalTime.setText(TimeFormatter.formatMs(song.getDuration()));
                }

                if (isSongChange) {
                    lastSongId = song.getId();
                    if (circularArtworkView != null) circularArtworkView.resetRotation();
                    loadArtwork(song);
                    if (circularArtworkView != null) {
                        circularArtworkView.setPlaying(Boolean.TRUE.equals(playbackViewModel.getIsPlaying().getValue()));
                    }
                }

                checkIsFavorite(song.getId());
            }
        });

        playbackViewModel.getDurationMs().observe(getViewLifecycleOwner(), duration -> {
            if (duration != null && duration > 0 && scrubber != null) {
                scrubber.setMax(duration);
                tvTotalTime.setText(TimeFormatter.formatMs(duration));
            }
        });

        playbackViewModel.getIsPlaying().observe(getViewLifecycleOwner(), isPlaying -> {
            boolean playing = Boolean.TRUE.equals(isPlaying);
            btnPlayPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
            btnPlayPause.setContentDescription(getString(playing ? R.string.pause : R.string.play));

            if (circularArtworkView != null) circularArtworkView.setPlaying(playing);
        });

        playbackViewModel.getCurrentPosition().observe(getViewLifecycleOwner(), position -> {
            if (!isUserSeeking && position != null && scrubber != null) {
                long pos = Math.max(0, position);
                long max = scrubber.getMax();
                if (max > 0) {
                    pos = Math.min(pos, max);
                }
                scrubber.setProgress(pos);
                tvCurrentTime.setText(TimeFormatter.formatMs(pos));
                if (circularArtworkView != null && max > 0) {
                    circularArtworkView.setProgress((float) pos / (float) max);
                }
            }
        });

        playbackViewModel.getShuffleMode().observe(getViewLifecycleOwner(), enabled -> {
            boolean active = Boolean.TRUE.equals(enabled);
            btnShuffle.setAlpha(active ? 1.0f : 0.45f);
            btnShuffle.setContentDescription(getString(active ? R.string.shuffle_on : R.string.shuffle_off));
        });

        // Repeat state
        playbackViewModel.getRepeatMode().observe(getViewLifecycleOwner(), mode -> {
            if (mode != null) {
                switch (mode) {
                    case 0:
                        btnRepeat.setImageResource(R.drawable.ic_repeat);
                        btnRepeat.setAlpha(0.4f);
                        btnRepeat.setContentDescription(getString(R.string.repeat_off));
                        break;
                    case 1:
                        btnRepeat.setImageResource(R.drawable.ic_repeat_one);
                        btnRepeat.setAlpha(1.0f);
                        btnRepeat.setContentDescription(getString(R.string.repeat_one));
                        break;
                    case 2:
                        btnRepeat.setImageResource(R.drawable.ic_repeat);
                        btnRepeat.setAlpha(1.0f);
                        btnRepeat.setContentDescription(getString(R.string.repeat_all));
                        break;
                }
            }
        });

        // Favorites
        favoritesViewModel.getFavoriteSongIds().observe(getViewLifecycleOwner(), ids -> {
            if (currentSong != null) checkIsFavorite(currentSong.getId());
        });

        // Audio output
        AudioOutputManager.getInstance(requireContext()).getCurrentOutput().observe(getViewLifecycleOwner(), device -> {
            if (btnAudioOutput != null && device != null) {
                btnAudioOutput.setImageResource(device.getIconResId());
                btnAudioOutput.setContentDescription(device.getDisplayName());
            }
        });

        // Theme tokens → update circular artwork colors + all UI
        com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                .getDesignTokens().observe(getViewLifecycleOwner(), tokens -> {
                    if (tokens == null || getView() == null) return;
                    applyNowPlayingColors(tokens);
                    if (btnKorokae != null) {
                        updateKorokaeUi(playbackViewModel.getKorokaeState().getValue());
                    }
                });

        // Korokae Mode (Karaoke / Instrumental Playback)
        playbackViewModel.getKorokaeState().observe(getViewLifecycleOwner(), this::updateKorokaeUi);
    }

    private void updateKorokaeUi(@Nullable com.psthetech.swara.domain.model.KorokaeState state) {
        if (btnKorokae == null || ivKorokaeIcon == null || tvKorokaeLabel == null || getContext() == null) return;
        com.psthetech.swara.ui.theme.DesignTokens tokens =
                com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance().getCurrentTokens();

        // Now Playing is always a dark material, so the inactive tone is a fixed light grey.
        int activeColor = tokens != null ? tokens.getAccentColor() : 0xFFD4AF5A;
        int inactiveColor = android.graphics.Color.argb(200, 255, 255, 255);

        if (state == null || state.getStatus() == com.psthetech.swara.domain.model.KorokaeState.Status.OFF) {
            if (korokaeAnimator != null) {
                korokaeAnimator.cancel();
                korokaeAnimator = null;
            }
            ivKorokaeIcon.setScaleX(1.0f);
            ivKorokaeIcon.setScaleY(1.0f);
            ivKorokaeIcon.setColorFilter(inactiveColor);
            tvKorokaeLabel.setTextColor(inactiveColor);
            tvKorokaeLabel.setText(R.string.karaoke_mode);
            btnKorokae.setAlpha(0.7f);

        } else if (state.getStatus() == com.psthetech.swara.domain.model.KorokaeState.Status.PROCESSING) {
            ivKorokaeIcon.setColorFilter(activeColor);
            tvKorokaeLabel.setTextColor(activeColor);
            tvKorokaeLabel.setText(R.string.karaoke_loading);
            btnKorokae.setAlpha(1.0f);

        } else if (state.getStatus() == com.psthetech.swara.domain.model.KorokaeState.Status.ACTIVE) {
            ivKorokaeIcon.setColorFilter(activeColor);
            tvKorokaeLabel.setTextColor(activeColor);
            tvKorokaeLabel.setText(R.string.karaoke_mode);
            btnKorokae.setAlpha(1.0f);

            // Subtle scale bounce animation on activation
            if (korokaeAnimator != null) korokaeAnimator.cancel();
            korokaeAnimator = android.animation.ValueAnimator.ofFloat(1.0f, 1.14f, 1.0f);
            korokaeAnimator.setDuration(260L);
            korokaeAnimator.addUpdateListener(anim -> {
                float val = (float) anim.getAnimatedValue();
                ivKorokaeIcon.setScaleX(val);
                ivKorokaeIcon.setScaleY(val);
            });
            korokaeAnimator.start();

        } else if (state.getStatus() == com.psthetech.swara.domain.model.KorokaeState.Status.FAILED) {
            if (korokaeAnimator != null) {
                korokaeAnimator.cancel();
                korokaeAnimator = null;
            }
            ivKorokaeIcon.setScaleX(1.0f);
            ivKorokaeIcon.setScaleY(1.0f);
            ivKorokaeIcon.setColorFilter(inactiveColor);
            tvKorokaeLabel.setTextColor(inactiveColor);
            tvKorokaeLabel.setText(R.string.karaoke_unavailable);
            btnKorokae.setAlpha(0.6f);

            String msg = state.getMessage();
            if (msg != null && !msg.isEmpty() && isResumed()) {
                com.google.android.material.snackbar.Snackbar.make(requireView(), msg, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    // ── Colours ──────────────────────────────────────────────────────────────────

    /**
     * Now Playing always renders as a dark, artwork-tinted material (like Apple Music),
     * so foreground colours are fixed light tones independent of the app's light/dark mode.
     */
    private void applyNowPlayingColors(@NonNull com.psthetech.swara.ui.theme.DesignTokens tokens) {
        if (getView() == null) return;
        int base = artworkTint != android.graphics.Color.TRANSPARENT ? artworkTint : tokens.getAccentColor();
        // Deep, desaturated tint of the artwork colour; keeps white text >= 7:1.
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(base, hsv);
        hsv[1] = Math.min(hsv[1], 0.55f);
        hsv[2] = 0.20f;
        int top = android.graphics.Color.HSVToColor(hsv);
        hsv[2] = 0.08f;
        int bottom = android.graphics.Color.HSVToColor(hsv);
        if (viewBackdropScrim != null) {
            android.graphics.drawable.GradientDrawable scrim = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{withAlpha(top, 200), withAlpha(bottom, 235)});
            viewBackdropScrim.setBackground(scrim);
        }
        getView().setBackgroundColor(bottom);

        int primary = android.graphics.Color.WHITE;
        int secondary = android.graphics.Color.argb(166, 255, 255, 255);
        int tertiary = android.graphics.Color.argb(140, 255, 255, 255);

        TextView header = getView().findViewById(R.id.nowPlayingHeader);
        if (header != null) header.setTextColor(secondary);
        tvTitle.setTextColor(primary);
        tvArtist.setTextColor(secondary);
        if (tvAlbum != null) tvAlbum.setTextColor(tertiary);
        tvCurrentTime.setTextColor(tertiary);
        tvTotalTime.setTextColor(tertiary);
        if (scrubber != null) {
            scrubber.setColors(android.graphics.Color.argb(56, 255, 255, 255),
                    android.graphics.Color.argb(225, 255, 255, 255));
        }

        if (circularArtworkView != null) {
            // Quiet white ring on the dark material; glow picks up the artwork colour.
            circularArtworkView.setThemeColors(
                    withAlpha(base, 70),
                    android.graphics.Color.argb(225, 255, 255, 255),
                    android.graphics.Color.argb(46, 255, 255, 255),
                    android.graphics.Color.argb(40, 255, 255, 255),
                    android.graphics.Color.WHITE,
                    true);
        }

        btnPlayPause.setBackground(null);
        btnPlayPause.setBackgroundResource(R.drawable.ripple_circle);
        btnPlayPause.setColorFilter(primary);
        btnPrevious.setColorFilter(primary);
        btnNext.setColorFilter(primary);
        btnShuffle.setColorFilter(primary);
        btnRepeat.setColorFilter(primary);
        btnQueue.setColorFilter(secondary);
        if (btnShare != null) btnShare.setColorFilter(secondary);
        if (btnShareCard != null) btnShareCard.setColorFilter(secondary);
        if (btnCollapse != null) btnCollapse.setColorFilter(secondary);
        if (btnAudioOutput != null) btnAudioOutput.setColorFilter(secondary);
        if (btnEqualizer != null) btnEqualizer.setColorFilter(secondary);
        if (btnSleepTimer != null) {
            boolean timerActive = Boolean.TRUE.equals(SleepTimerManager.getInstance().getActive().getValue());
            btnSleepTimer.setColorFilter(timerActive ? tokens.getAccentColor() : secondary);
        }
        tintFavorite(tokens);
        updateKorokaeUi(playbackViewModel.getKorokaeState().getValue());
    }

    private boolean isFavoriteState = false;

    private void tintFavorite(@Nullable com.psthetech.swara.ui.theme.DesignTokens tokens) {
        if (btnFavorite == null) return;
        int active = tokens != null ? tokens.getAccentColor() : 0xFFD4AF5A;
        btnFavorite.setColorFilter(isFavoriteState ? active : android.graphics.Color.argb(166, 255, 255, 255));
    }

    private static int withAlpha(int color, int alpha) {
        return android.graphics.Color.argb(alpha, android.graphics.Color.red(color),
                android.graphics.Color.green(color), android.graphics.Color.blue(color));
    }

    // ── Artwork Loading ──────────────────────────────────────────────────────────

    /** Loads artwork through the same priority pipeline used elsewhere (custom > MediaStore > fallback). */
    private void loadArtwork(Song song) {
        if (getContext() == null || circularArtworkView == null) return;

        if (artworkTarget != null) {
            try {
                Glide.with(requireContext()).clear(artworkTarget);
            } catch (Exception ignored) { /* context may be detached */ }
            artworkTarget = null;
        }

        ArtworkRepository repo = new ArtworkRepository(requireContext());
        Object artworkSource;
        java.io.File customFile = repo.getCustomArtworkStore().getArtworkFile(song.getId());
        if (customFile.exists()) {
            artworkSource = customFile;
        } else {
            android.net.Uri albumUri = ArtworkRepository.getAlbumArtUri(song.getAlbumId());
            artworkSource = (albumUri != null) ? albumUri : R.drawable.ic_artwork_fallback;
        }

        final int targetSize = 768;
        artworkTarget = new CustomTarget<Bitmap>(targetSize, targetSize) {
            @Override
            public void onResourceReady(@NonNull Bitmap resource,
                                        @Nullable Transition<? super Bitmap> transition) {
                if (circularArtworkView != null) circularArtworkView.setArtworkBitmap(resource);
                if (ivBackdrop != null) ivBackdrop.setImageBitmap(resource);
                extractArtworkTint();
            }

            @Override
            public void onLoadFailed(@Nullable android.graphics.drawable.Drawable errorDrawable) {
                if (circularArtworkView != null) circularArtworkView.setArtworkBitmap(null);
                if (ivBackdrop != null) ivBackdrop.setImageDrawable(null);
            }

            @Override
            public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                // Glide cleared the resource; leave the views as they are.
            }
        };

        Glide.with(requireContext())
                .asBitmap()
                .load(artworkSource)
                .apply(new RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .error(R.drawable.ic_artwork_fallback)
                        .centerCrop())
                .error(Glide.with(requireContext())
                        .asBitmap()
                        .load(R.drawable.ic_artwork_fallback))
                .into(artworkTarget);
    }

    /** Tints the backdrop with the artwork's dominant colour. */
    private void extractArtworkTint() {
        if (getContext() == null) return;
        com.psthetech.swara.ui.theme.DesignTokens tokens =
                com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance().getCurrentTokens();
        int fallback = tokens != null ? tokens.getAccentColor() : 0xFFD4AF5A;
        ArtworkHelper.extractArtworkColor(requireContext(), currentSong, fallback, color -> {
            if (getView() == null || tokens == null) return;
            artworkTint = color;
            applyNowPlayingColors(tokens);
        });
    }

    private void checkIsFavorite(long songId) {
        favoritesViewModel.isFavorite(songId).observe(getViewLifecycleOwner(), isFav -> {
            boolean favorite = Boolean.TRUE.equals(isFav);
            btnFavorite.setImageResource(favorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
            btnFavorite.setAlpha(1.0f);
            isFavoriteState = favorite;
            tintFavorite(com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance().getCurrentTokens());
        });
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────────

    @Override
    public void onDestroyView() {
        // Cancel Glide load to avoid bitmap delivery to a destroyed view
        if (artworkTarget != null && getContext() != null) {
            try {
                Glide.with(requireContext()).clear(artworkTarget);
            } catch (Exception ignored) { /* detached */ }
            artworkTarget = null;
        }
        if (circularArtworkView != null) circularArtworkView.pauseRotation();
        if (korokaeAnimator != null) {
            korokaeAnimator.cancel();
            korokaeAnimator = null;
        }
        super.onDestroyView();
    }
}
