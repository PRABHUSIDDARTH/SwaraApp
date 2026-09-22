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

        if (playlistTitle != null) playlistTitle.setText(playlistName);

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

        // --- Song list ---
        songAdapter = new SongAdapter(this);
        songAdapter.setShowRemoveFromPlaylist(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(songAdapter);

        // Drag-to-reorder
        ItemTouchHelper touchHelper = new ItemTouchHelper(new ReorderCallback());
        touchHelper.attachToRecyclerView(recyclerView);

        // Observe design tokens
        com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                .getDesignTokens().observe(getViewLifecycleOwner(), this::applyDesignTokens);

        observeData();
    }

    private void applyDesignTokens(com.psthetech.swara.ui.theme.DesignTokens tokens) {
        View v = getView();
        if (v == null || tokens == null) return;
        v.setBackground(tokens.createAmbientDrawable());
        float density = v.getContext().getResources().getDisplayMetrics().density;

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
        if (btnShuffle != null) {
            btnShuffle.setTextColor(tokens.getTextPrimaryColor());
            android.graphics.drawable.GradientDrawable shuffleBg = new android.graphics.drawable.GradientDrawable();
            shuffleBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            shuffleBg.setColor(tokens.isNightMode() ? tokens.getSurfaceElevatedColor() : tokens.getSurfaceColor());
            shuffleBg.setStroke(Math.max(1, Math.round(density)), tokens.getStrokeColor());
            shuffleBg.setCornerRadius(tokens.getCornerRadiusDp() * density);
            btnShuffle.setBackground(shuffleBg);
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
        // Observe playlist metadata (name, custom artwork path)
        playlistViewModel.getPlaylists().observe(getViewLifecycleOwner(), playlists -> {
            if (playlists == null) return;
            for (com.psthetech.swara.data.db.entity.Playlist p : playlists) {
                if (p.id == playlistId) {
                    currentPlaylist = p;
                    playlistName = p.name;
                    if (playlistTitle != null) playlistTitle.setText(playlistName);
                    loadHeroArtwork();
                    break;
                }
            }
        });

        // Load hero artwork initially
        loadHeroArtwork();

        // When songs change, reload artwork (collage may need regenerating)
        playlistViewModel.getSongsForPlaylist(playlistId).observe(getViewLifecycleOwner(), songs -> {
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

            // Empty state
            if (layoutEmpty != null) {
                layoutEmpty.setVisibility(currentSongs.isEmpty() ? View.VISIBLE : View.GONE);
            }

            // Invalidate collage if needed (songs changed)
            PlaylistArtworkStore store = playlistViewModel.getPlaylistArtworkStore();
            if (!store.hasCustomArtwork(playlistId)) {
                PlaylistArtworkHelper.invalidatePlaylistCollage(
                        requireContext(), playlistId, store);
                loadHeroArtwork();
            }
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
                    if (playlistTitle != null) playlistTitle.setText(newName);
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
