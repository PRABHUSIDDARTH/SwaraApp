package com.psthetech.swara.ui.playlists;

import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;
import com.psthetech.swara.R;
import com.psthetech.swara.data.repository.PlaylistArtworkStore;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.adapter.SongAdapter;
import com.psthetech.swara.ui.playlists.AddToPlaylistDialog;
import com.psthetech.swara.ui.viewmodel.FavoritesViewModel;
import com.psthetech.swara.ui.viewmodel.LibraryViewModel;
import com.psthetech.swara.ui.viewmodel.PlaybackViewModel;
import com.psthetech.swara.ui.viewmodel.PlaylistViewModel;
import com.psthetech.swara.util.PlaylistArtworkHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import com.psthetech.swara.data.repository.ShuffleEngine;

/**
 * Playlist detail screen.
 *
 * Features:
 *  - Play / Shuffle all songs
 *  - Add songs from library (via "Add songs" bottom sheet / picker)
 *  - Remove individual songs (context menu)
 *  - Drag-to-reorder songs (ItemTouchHelper)
 *  - Rename playlist (overflow)
 *  - Delete playlist (overflow → confirmation → navigate up)
 *  - Add a song to another playlist (context menu → AddToPlaylistDialog)
 *
 * Mix mode (nav arg {@code mixKey} set): shows a "For you" mix read-only — play, shuffle and
 * per-song actions only — plus "+ Add as playlist", which saves it as a real playlist and
 * replaces this screen with that playlist, where editing is available.
 */
public class PlaylistDetailFragment extends Fragment implements SongAdapter.Listener {

    private PlaylistViewModel playlistViewModel;
    private PlaybackViewModel playbackViewModel;
    private FavoritesViewModel favoritesViewModel;
    private LibraryViewModel libraryViewModel;

    private TextView playlistTitle;
    private TextView playlistMeta;
    private ShapeableImageView ivPlaylistDetailArtwork;
    private RecyclerView recyclerView;
    private View layoutEmpty;
    private SongAdapter songAdapter;

    private long playlistId = -1;
    private String playlistName = "Playlist";
    private com.psthetech.swara.data.db.entity.Playlist currentPlaylist;
    private List<Song> currentSongs = new ArrayList<>();

    /** Non-null when showing a "For you" mix instead of a playlist. */
    @Nullable
    private String mixKey;
    private Button btnSaveAsPlaylist;
    /** Small title in the sticky top bar; fades in as the hero header collapses. */
    private TextView tvCollapsedTitle;
    /** Bumped on every mix update so a stale background resolve never overwrites a newer one. */
    private int mixResolveGeneration = 0;

    // Tracks the last shuffle order for repeat-avoidance:
    // pressing Shuffle twice on the same playlist must produce a different ordering.
    private List<Song> lastShuffleOrder = null;

    // Image picker — registered before fragment is started
    private ActivityResultLauncher<PickVisualMediaRequest> pickMedia;
    private ActivityResultLauncher<String> pickMediaFallback;

    @Nullable
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Register image picker launchers before the fragment starts
        pickMedia = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                uri -> {
                    if (uri != null && playlistId > 0) {
                        onArtworkPicked(uri);
                    }
                });
        pickMediaFallback = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && playlistId > 0) {
                        onArtworkPicked(uri);
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_playlist_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            playlistId   = getArguments().getLong("playlistId", -1);
            playlistName = getArguments().getString("playlistName", "Playlist");
            mixKey       = getArguments().getString("mixKey", null);
        }

        playlistViewModel = new ViewModelProvider(requireActivity()).get(PlaylistViewModel.class);
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);
        favoritesViewModel = new ViewModelProvider(requireActivity()).get(FavoritesViewModel.class);
        libraryViewModel   = new ViewModelProvider(requireActivity()).get(LibraryViewModel.class);

        playlistTitle = view.findViewById(R.id.playlistTitle);
        playlistMeta  = view.findViewById(R.id.playlistMeta);
        ivPlaylistDetailArtwork = view.findViewById(R.id.ivPlaylistDetailArtwork);
        recyclerView  = view.findViewById(R.id.recyclerView);
        layoutEmpty   = view.findViewById(R.id.layoutEmpty);

        tvCollapsedTitle = view.findViewById(R.id.tvCollapsedTitle);
        setTitleText(playlistName);
        setupCollapsingHeader(view);

        // --- Navigation ---
        view.findViewById(R.id.backButton)
                .setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        // --- Overflow menu (Rename / Delete) ---
        ImageView btnOverflow = view.findViewById(R.id.btnPlaylistOverflow);
        if (btnOverflow != null) {
            btnOverflow.setOnClickListener(this::showOverflowMenu);
        }

        // --- Play all ---
        Button btnPlay = view.findViewById(R.id.btnPlayAll);
        if (btnPlay != null) {
            btnPlay.setOnClickListener(v -> playAll(false));
        }

        // --- Shuffle ---
        Button btnShuffle = view.findViewById(R.id.btnShuffle);
        if (btnShuffle != null) {
            btnShuffle.setOnClickListener(v -> playAll(true));
        }

        // --- Add Songs ---
        ImageView btnAddSongs = view.findViewById(R.id.btnAddSongs);
        if (btnAddSongs != null) {
            btnAddSongs.setOnClickListener(v -> showAddSongsSheet());
        }

        // --- Hero artwork tap → change artwork ---
        if (ivPlaylistDetailArtwork != null) {
            ivPlaylistDetailArtwork.setOnClickListener(v -> showArtworkOptions());
        }
        ImageView editBadge = view.findViewById(R.id.ivArtworkEditBadge);
        if (editBadge != null) {
            editBadge.setOnClickListener(v -> showArtworkOptions());
        }

        // --- Save mix as playlist (mix mode only) ---
        btnSaveAsPlaylist = view.findViewById(R.id.btnSaveAsPlaylist);
        if (btnSaveAsPlaylist != null) {
            btnSaveAsPlaylist.setOnClickListener(v -> saveMixAsPlaylist());
        }

        // --- Song list ---
        songAdapter = new SongAdapter(this);
        songAdapter.setShowRemoveFromPlaylist(!isMixMode());
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(songAdapter);

        if (isMixMode()) {
            // A mix is rebuilt daily, so editing is only offered once it is saved as a playlist.
            if (btnOverflow != null) btnOverflow.setVisibility(View.GONE);
            if (btnAddSongs != null) btnAddSongs.setVisibility(View.GONE);
            if (editBadge != null) editBadge.setVisibility(View.GONE);
            if (ivPlaylistDetailArtwork != null) {
                ivPlaylistDetailArtwork.setOnClickListener(null);
                ivPlaylistDetailArtwork.setClickable(false);
            }
            if (btnSaveAsPlaylist != null) {
                btnSaveAsPlaylist.setVisibility(View.VISIBLE);
                btnSaveAsPlaylist.setEnabled(false); // enabled once songs resolve
            }
        } else {
            // Drag-to-reorder
            ItemTouchHelper touchHelper = new ItemTouchHelper(new ReorderCallback());
            touchHelper.attachToRecyclerView(recyclerView);
        }

        // Observe design tokens
        com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                .getDesignTokens().observe(getViewLifecycleOwner(), this::applyDesignTokens);

        observeData();
    }

    /** Sets the playlist name on both the hero title and the sticky top-bar title. */
    private void setTitleText(String name) {
        if (playlistTitle != null) playlistTitle.setText(name);
        if (tvCollapsedTitle != null) tvCollapsedTitle.setText(name);
    }

    /**
     * Fades the hero title out and the top-bar title in as the header collapses, and paints the
     * sticky bar opaque once collapsed so the scrolled-away header never shows through it.
     */
    private void setupCollapsingHeader(View root) {
        com.google.android.material.appbar.AppBarLayout appBar = root.findViewById(R.id.appBar);
        if (appBar == null) return;
        appBar.addOnOffsetChangedListener((bar, verticalOffset) -> {
            int range = bar.getTotalScrollRange();
            float collapsed = range > 0 ? Math.min(1f, -verticalOffset / (float) range) : 0f;
            // Small title appears over the last 30% of the collapse; hero title fades early.
            if (tvCollapsedTitle != null) {
                tvCollapsedTitle.setAlpha(Math.max(0f, (collapsed - 0.7f) / 0.3f));
            }
            if (playlistTitle != null) {
                playlistTitle.setAlpha(Math.max(0f, 1f - collapsed * 1.5f));
            }
        });
    }

    /** Styles a MaterialButton as an outlined pill (setBackground is ignored by MaterialButton). */
    private static void styleOutlinedButton(Button button, int textColor, int fillColor,
                                            int strokeColor, float density, float cornerPx) {
        button.setTextColor(textColor);
        if (button instanceof com.google.android.material.button.MaterialButton) {
            com.google.android.material.button.MaterialButton mb =
                    (com.google.android.material.button.MaterialButton) button;
            mb.setBackgroundTintList(android.content.res.ColorStateList.valueOf(fillColor));
            mb.setStrokeColor(android.content.res.ColorStateList.valueOf(strokeColor));
            mb.setStrokeWidth(Math.max(1, Math.round(density)));
            mb.setCornerRadius(Math.round(cornerPx));
        } else {
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            bg.setColor(fillColor);
            bg.setStroke(Math.max(1, Math.round(density)), strokeColor);
            bg.setCornerRadius(cornerPx);
            button.setBackground(bg);
        }
    }

    private void applyDesignTokens(com.psthetech.swara.ui.theme.DesignTokens tokens) {
        View v = getView();
        if (v == null || tokens == null) return;
        v.setBackground(tokens.createAmbientDrawable());
        float density = v.getContext().getResources().getDisplayMetrics().density;

        com.google.android.material.appbar.CollapsingToolbarLayout collapsing =
                v.findViewById(R.id.collapsingHeader);
        if (collapsing != null) {
            collapsing.setContentScrimColor(tokens.getBackgroundColor());
            collapsing.setStatusBarScrimColor(tokens.getBackgroundColor());
        }
        View actions = v.findViewById(R.id.layoutPlaylistActions);
        if (actions != null) actions.setBackgroundColor(tokens.getBackgroundColor());
        if (tvCollapsedTitle != null) tvCollapsedTitle.setTextColor(tokens.getTextPrimaryColor());

        if (playlistTitle != null) playlistTitle.setTextColor(tokens.getTextPrimaryColor());
        if (playlistMeta != null) playlistMeta.setTextColor(tokens.getTextSecondaryColor());

        // Back button
        ImageView backBtn = v.findViewById(R.id.backButton);
        if (backBtn != null) backBtn.setColorFilter(tokens.getTextPrimaryColor());

        // Overflow button
        ImageView overflow = v.findViewById(R.id.btnPlaylistOverflow);
        if (overflow != null) overflow.setColorFilter(tokens.getIconSecondaryColor());

        // Add songs button
        ImageView addSongs = v.findViewById(R.id.btnAddSongs);
        if (addSongs != null) {
            android.graphics.drawable.GradientDrawable addBg = new android.graphics.drawable.GradientDrawable();
            addBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            addBg.setColor(tokens.isNightMode() ? tokens.getSurfaceElevatedColor() : tokens.getSurfaceColor());
            addBg.setStroke(Math.max(1, Math.round(density)), tokens.getStrokeColor());
            addSongs.setBackground(addBg);
            addSongs.setColorFilter(tokens.getAccentColor());
        }

        // Artwork frame & edit badge (Artwork itself remains authentic, never tinted)
        if (ivPlaylistDetailArtwork != null) {
            ivPlaylistDetailArtwork.setColorFilter(null);
            ivPlaylistDetailArtwork.setStrokeColor(android.content.res.ColorStateList.valueOf(tokens.getStrokeColor()));
            ivPlaylistDetailArtwork.setStrokeWidth(Math.max(1, Math.round(density)));
        }
        ImageView editBadge = v.findViewById(R.id.ivArtworkEditBadge);
        if (editBadge != null) {
            android.graphics.drawable.GradientDrawable editBg = new android.graphics.drawable.GradientDrawable();
            editBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            editBg.setColor(tokens.getAccentColor());
            editBadge.setBackground(editBg);
            editBadge.setColorFilter(tokens.getButtonTextColor());
        }

        // Primary Play action vs Secondary Shuffle action hierarchy
        Button btnPlay = v.findViewById(R.id.btnPlayAll);
        if (btnPlay != null) {
            btnPlay.setTextColor(tokens.getButtonTextColor());
            android.graphics.drawable.GradientDrawable playBg = new android.graphics.drawable.GradientDrawable();
            playBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            playBg.setColor(tokens.getAccentColor());
            playBg.setCornerRadius(tokens.getCornerRadiusDp() * density);
            btnPlay.setBackground(playBg);
        }

        Button btnShuffle = v.findViewById(R.id.btnShuffle);
        int ghostFill = tokens.isNightMode() ? tokens.getSurfaceElevatedColor() : tokens.getSurfaceColor();
        float cornerPx = tokens.getCornerRadiusDp() * density;
        if (btnShuffle != null) {
            styleOutlinedButton(btnShuffle, tokens.getTextPrimaryColor(), ghostFill,
                    tokens.getStrokeColor(), density, cornerPx);
        }

        if (btnSaveAsPlaylist != null) {
            styleOutlinedButton(btnSaveAsPlaylist, tokens.getAccentColor(), ghostFill,
                    tokens.getAccentColor(), density, cornerPx);
        }

        // Empty state styling
        if (layoutEmpty instanceof ViewGroup) {
            ViewGroup emptyGroup = (ViewGroup) layoutEmpty;
            for (int i = 0; i < emptyGroup.getChildCount(); i++) {
                View child = emptyGroup.getChildAt(i);
                if (child instanceof android.widget.TextView) {
                    android.widget.TextView tv = (android.widget.TextView) child;
                    if (tv.getText().equals(getString(R.string.playlist_empty_title))) {
                        tv.setTextColor(tokens.getTextPrimaryColor());
                    } else {
                        tv.setTextColor(tokens.getTextSecondaryColor());
                    }
                } else if (child instanceof ImageView) {
                    ((ImageView) child).setColorFilter(tokens.getTextTertiaryColor());
                }
            }
        }

        if (songAdapter != null) songAdapter.notifyDataSetChanged();
    }

    private void observeData() {
        favoritesViewModel.getFavoriteSongIds().observe(getViewLifecycleOwner(), ids -> {
            if (ids != null) songAdapter.setFavorites(new HashSet<>(ids));
        });

        playbackViewModel.getCurrentSong().observe(getViewLifecycleOwner(), song -> {
            if (songAdapter != null) {
                songAdapter.setCurrentPlayingSongId(song != null ? song.getId() : -1L);
            }
        });

        if (isMixMode()) {
            observeMix();
            return;
        }

        // Observe playlist metadata (name, custom artwork path)
        playlistViewModel.getPlaylists().observe(getViewLifecycleOwner(), playlists -> {
            if (playlists == null) return;
            for (com.psthetech.swara.data.db.entity.Playlist p : playlists) {
                if (p.id == playlistId) {
                    currentPlaylist = p;
                    playlistName = p.name;
                    setTitleText(playlistName);
                    loadHeroArtwork();
                    break;
                }
            }
        });

        // Load hero artwork initially
        loadHeroArtwork();

        // When songs change, reload artwork (collage may need regenerating)
        playlistViewModel.getSongsForPlaylist(playlistId).observe(getViewLifecycleOwner(), songs -> {
            bindSongs(songs);

            // Invalidate collage if needed (songs changed)
            PlaylistArtworkStore store = playlistViewModel.getPlaylistArtworkStore();
            if (!store.hasCustomArtwork(playlistId)) {
                PlaylistArtworkHelper.invalidatePlaylistCollage(
                        requireContext(), playlistId, store);
                loadHeroArtwork();
            }
        });
    }

    /** Shows {@code songs} in the list and updates the track count, duration and empty state. */
    private void bindSongs(@Nullable List<Song> songs) {
        currentSongs = songs != null ? songs : new ArrayList<>();
        songAdapter.submitList(new ArrayList<>(currentSongs));

        if (playlistMeta != null) {
            int count = currentSongs.size();
            long totalMs = 0;
            for (Song s : currentSongs) {
                totalMs += s.getDuration();
            }
            String durationStr = formatTotalDuration(totalMs);
            playlistMeta.setText(count + (count == 1 ? " track" : " tracks")
                    + (durationStr.isEmpty() ? "" : " • " + durationStr));
        }

        if (layoutEmpty != null) {
            layoutEmpty.setVisibility(currentSongs.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    // ===== "For you" mix mode =====

    private boolean isMixMode() {
        return mixKey != null;
    }

    /** Follows the stored mix, so a daily rebuild while the screen is open updates the list. */
    private void observeMix() {
        com.psthetech.swara.data.db.AppDatabase.getInstance(requireContext().getApplicationContext())
                .mixDao().byKeyLive(mixKey).observe(getViewLifecycleOwner(), mix -> {
                    if (mix == null) {
                        bindMixSongs(new ArrayList<>());
                        return;
                    }
                    if (mix.title != null) {
                        playlistName = mix.title;
                        setTitleText(playlistName);
                    }
                    resolveMixSongs(com.psthetech.swara.ui.adapter.MixAdapter.parseIds(mix.songIds));
                });
    }

    /** Resolves mix ids to songs: canonical cache first, then MediaStore off the main thread. */
    private void resolveMixSongs(List<Long> ids) {
        final int generation = ++mixResolveGeneration;
        List<Song> cached = MixSongs.resolveInOrder(ids,
                com.psthetech.swara.data.repository.MusicRepository::getCanonicalSong);
        if (cached.size() == new HashSet<>(ids).size()) {
            bindMixSongs(cached);
            return;
        }
        final android.content.Context appCtx = requireContext().getApplicationContext();
        com.psthetech.swara.SwaraApplication.getInstance().getIoExecutor().execute(() -> {
            com.psthetech.swara.data.repository.MusicRepository repo =
                    new com.psthetech.swara.data.repository.MusicRepository(appCtx);
            List<Song> resolved = MixSongs.resolveInOrder(ids, id -> {
                Song s = com.psthetech.swara.data.repository.MusicRepository.getCanonicalSong(id);
                return s != null ? s : repo.getSongById(id);
            });
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (isAdded() && getView() != null && generation == mixResolveGeneration) {
                    bindMixSongs(resolved);
                }
            });
        });
    }

    private void bindMixSongs(List<Song> songs) {
        bindSongs(songs);
        if (btnSaveAsPlaylist != null) btnSaveAsPlaylist.setEnabled(!currentSongs.isEmpty());
        if (ivPlaylistDetailArtwork != null) {
            com.psthetech.swara.util.ArtworkHelper.loadSongArt(requireContext(),
                    currentSongs.isEmpty() ? null : currentSongs.get(0), ivPlaylistDetailArtwork);
        }
    }

    /** Saves the mix as a persistent playlist, then swaps this screen for that playlist. */
    private void saveMixAsPlaylist() {
        if (currentSongs.isEmpty()) return;
        final List<Song> songs = new ArrayList<>(currentSongs);
        com.psthetech.swara.ui.theme.ThemedDialogHelper.showSaveAsPlaylistDialog(
                requireContext(),
                playlistName,
                name -> {
                    String trimmed = name != null ? name.trim() : "";
                    final String finalName = trimmed.isEmpty() ? playlistName : trimmed;
                    if (btnSaveAsPlaylist != null) btnSaveAsPlaylist.setEnabled(false);
                    playlistViewModel.createPlaylistFromSongs(finalName, songs, newId -> {
                        android.content.Context ctx = getContext();
                        if (ctx != null) {
                            android.widget.Toast.makeText(ctx,
                                    getString(R.string.saved_as_playlist, finalName),
                                    android.widget.Toast.LENGTH_SHORT).show();
                        }
                        if (!isAdded() || getView() == null) return;
                        Bundle args = new Bundle();
                        args.putLong("playlistId", newId);
                        args.putString("playlistName", finalName);
                        Navigation.findNavController(requireView()).navigate(
                                R.id.playlistDetailFragment, args,
                                new androidx.navigation.NavOptions.Builder()
                                        .setPopUpTo(R.id.playlistDetailFragment, true)
                                        .build());
                    });
                });
    }

    // ===== Artwork =====

    private void loadHeroArtwork() {
        if (ivPlaylistDetailArtwork == null || playlistId <= 0) return;
        PlaylistArtworkStore store = playlistViewModel.getPlaylistArtworkStore();
        com.psthetech.swara.data.db.entity.Playlist target = currentPlaylist;
        if (target == null) {
            target = new com.psthetech.swara.data.db.entity.Playlist(playlistName, 0, 0);
            target.id = playlistId;
        }
        PlaylistArtworkHelper.loadPlaylistArt(
                requireContext(), target, store, ivPlaylistDetailArtwork);
    }

    private void showArtworkOptions() {
        PlaylistArtworkStore store = playlistViewModel.getPlaylistArtworkStore();
        boolean hasCustom = store.hasCustomArtwork(playlistId);

        String[] options = hasCustom
                ? new String[]{getString(R.string.change_artwork), getString(R.string.remove_artwork)}
                : new String[]{getString(R.string.change_artwork)};

        com.psthetech.swara.ui.theme.ThemedDialogHelper.showItemPickerDialog(
                requireContext(),
                getString(R.string.playlist_artwork),
                options,
                which -> {
                    if (which == 0) {
                        launchImagePicker();
                    } else if (which == 1 && hasCustom) {
                        playlistViewModel.removePlaylistArtwork(playlistId, this::loadHeroArtwork);
                    }
                });
    }

    private void onArtworkPicked(Uri uri) {
        playlistViewModel.setPlaylistArtwork(playlistId, uri, this::loadHeroArtwork);
    }

    private void launchImagePicker() {
        if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(requireContext())) {
            pickMedia.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        } else {
            pickMediaFallback.launch("image/*");
        }
    }

    // ===== Playback =====

    private void playAll(boolean shuffle) {
        if (currentSongs.isEmpty()) return;

        final List<Song> queue;
        if (shuffle) {
            // ShuffleEngine.shuffleAvoidRepeat: Fisher-Yates + repeat-avoidance.
            // Each call produces a fresh permutation different from lastShuffleOrder.
            queue = ShuffleEngine.shuffleAvoidRepeat(currentSongs, lastShuffleOrder);
            lastShuffleOrder = queue;
        } else {
            queue = new ArrayList<>(currentSongs);
        }

        // ExoPlayer is the sole playback authority. We pass the already-randomized
        // list as explicit ordered MediaItems. Shuffle mode flag is not needed because
        // the randomization already happened client-side.
        playbackViewModel.playSongs(queue, 0);
    }

    // ===== Add Songs =====

    private void showAddSongsSheet() {
        // Load all library songs, then show a checklist dialog
        libraryViewModel.getSongs().observe(getViewLifecycleOwner(), allSongs -> {
            if (allSongs == null || allSongs.isEmpty()) return;
            showSongPickerDialog(allSongs);
        });
        // Ensure songs are loaded
        if (libraryViewModel.getSongs().getValue() == null ||
                libraryViewModel.getSongs().getValue().isEmpty()) {
            libraryViewModel.loadSongs();
        }
    }

    private void showSongPickerDialog(List<Song> allSongs) {
        // Build display strings
        CharSequence[] labels = new CharSequence[allSongs.size()];
        boolean[] checked = new boolean[allSongs.size()];
        for (int i = 0; i < allSongs.size(); i++) {
            Song s = allSongs.get(i);
            labels[i] = s.getTitle() + "\n" + s.getArtist();
            // Pre-check songs already in playlist
            for (Song existing : currentSongs) {
                if (existing.getId() == s.getId()) {
                    checked[i] = true;
                    break;
                }
            }
        }

        List<Song> toAdd = new ArrayList<>();

        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.add_songs)
                        .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                            if (isChecked) {
                                toAdd.add(allSongs.get(which));
                            } else {
                                toAdd.remove(allSongs.get(which));
                            }
                        })
                        .setPositiveButton(R.string.done, (dialog, which) -> {
                            if (!toAdd.isEmpty()) {
                                playlistViewModel.addSongsToPlaylist(playlistId, toAdd);
                            }
                        })
                        .setNegativeButton(R.string.cancel, null);

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            com.psthetech.swara.ui.theme.DesignTokens tokens =
                    com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance().getCurrentTokens();
            if (tokens != null) {
                if (dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE) != null) {
                    dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                            .setTextColor(tokens.getAccentColor());
                }
                if (dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE) != null) {
                    dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE)
                            .setTextColor(tokens.getTextSecondaryColor());
                }
            }
        });
        dialog.show();
    }

    // ===== Overflow menu =====

    private void showOverflowMenu(View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 1, 0, R.string.rename_playlist);
        popup.getMenu().add(0, 2, 1, R.string.delete_playlist);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: showRenameDialog(); return true;
                case 2: confirmDelete(); return true;
            }
            return false;
        });
        popup.show();
    }

    private void showRenameDialog() {
        com.psthetech.swara.ui.theme.ThemedDialogHelper.showRenamePlaylistDialog(
                requireContext(),
                playlistName,
                newName -> {
                    playlistName = newName;
                    setTitleText(newName);
                    playlistViewModel.renamePlaylist(playlistId, newName);
                });
    }

    private void confirmDelete() {
        com.psthetech.swara.ui.theme.ThemedDialogHelper.showConfirmationDialog(
                requireContext(),
                getString(R.string.confirm_delete_playlist),
                playlistName,
                getString(R.string.delete),
                () -> {
                    playlistViewModel.deletePlaylist(playlistId);
                    Navigation.findNavController(requireView()).navigateUp();
                });
    }

    // ===== SongAdapter.Listener =====

    @Override
    public void onSongClick(Song song, int position) {
        playbackViewModel.playSongs(currentSongs, position);
    }

    @Override
    public void onFavoriteToggle(Song song, boolean currentlyFavorite) {
        favoritesViewModel.toggleFavorite(song);
    }

    @Override
    public void onPlayNext(Song song) {
        playbackViewModel.playNext(song);
    }

    @Override
    public void onStartVibe(Song song) {
        playbackViewModel.startVibeFrom(song);
    }

    @Override
    public void onAddToQueue(Song song) {
        playbackViewModel.addToQueue(song);
    }

    @Override
    public void onAddToPlaylist(Song song) {
        AddToPlaylistDialog.show(requireActivity(), requireView(), song, playlistViewModel);
    }

    @Override
    public void onRemoveFromPlaylist(Song song) {
        if (playlistId != -1) {
            playlistViewModel.removeSongFromPlaylist(playlistId, song.getId());
        }
    }

    @Override
    public void onEditArtwork(Song song) {
        if (getActivity() instanceof com.psthetech.swara.ui.MainActivity) {
            ((com.psthetech.swara.ui.MainActivity) getActivity()).promptEditArtwork(song);
        }
    }

    @Override
    public void onEditSongInfo(Song song) {
        if (getActivity() instanceof com.psthetech.swara.ui.MainActivity) {
            ((com.psthetech.swara.ui.MainActivity) getActivity()).promptEditSongInfo(song);
        }
    }

    @Override
    public void onDeleteSong(Song song) {
        if (getActivity() instanceof com.psthetech.swara.ui.MainActivity) {
            ((com.psthetech.swara.ui.MainActivity) getActivity()).promptDeleteSong(song);
        }
    }

    // ===== Drag-to-reorder =====

    private class ReorderCallback extends ItemTouchHelper.SimpleCallback {

        ReorderCallback() {
            super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView rv,
                              @NonNull RecyclerView.ViewHolder from,
                              @NonNull RecyclerView.ViewHolder to) {
            int fromPos = from.getAdapterPosition();
            int toPos   = to.getAdapterPosition();

            if (fromPos < 0 || toPos < 0 || fromPos >= currentSongs.size()
                    || toPos >= currentSongs.size()) {
                return false;
            }

            // Update local list for instant visual feedback
            List<Song> mutable = new ArrayList<>(currentSongs);
            Collections.swap(mutable, fromPos, toPos);
            currentSongs = mutable;
            songAdapter.submitList(new ArrayList<>(currentSongs));

            // Persist new positions
            Song moved = currentSongs.get(toPos);
            playlistViewModel.reorderSong(playlistId, moved.getId(), toPos);

            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int dir) {}
    }

    private static String formatTotalDuration(long totalMs) {
        if (totalMs <= 0) return "";
        long totalSecs = totalMs / 1000;
        long hours = totalSecs / 3600;
        long mins = (totalSecs % 3600) / 60;
        long secs = totalSecs % 60;

        if (hours > 0) {
            return String.format(java.util.Locale.getDefault(), "%d hr %d min", hours, mins);
        } else if (mins > 0) {
            return String.format(java.util.Locale.getDefault(), "%d min %d sec", mins, secs);
        } else {
            return String.format(java.util.Locale.getDefault(), "%d sec", secs);
        }
    }
}
