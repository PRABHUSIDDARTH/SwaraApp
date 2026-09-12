package com.psthetech.swara.ui.playlists;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.adapter.SongAdapter;
import com.psthetech.swara.ui.playlists.AddToPlaylistDialog;
import com.psthetech.swara.ui.viewmodel.FavoritesViewModel;
import com.psthetech.swara.ui.viewmodel.LibraryViewModel;
import com.psthetech.swara.ui.viewmodel.PlaybackViewModel;
import com.psthetech.swara.ui.viewmodel.PlaylistViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

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
    private RecyclerView recyclerView;
    private View layoutEmpty;
    private SongAdapter songAdapter;

    private long playlistId = -1;
    private String playlistName = "Playlist";
    private List<Song> currentSongs = new ArrayList<>();

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

        // --- Song list ---
        songAdapter = new SongAdapter(this);
        songAdapter.setShowRemoveFromPlaylist(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(songAdapter);

        // Drag-to-reorder
        ItemTouchHelper touchHelper = new ItemTouchHelper(new ReorderCallback());
        touchHelper.attachToRecyclerView(recyclerView);

        observeData();
    }

    private void observeData() {
        favoritesViewModel.getFavoriteSongIds().observe(getViewLifecycleOwner(), ids -> {
            if (ids != null) songAdapter.setFavorites(new HashSet<>(ids));
        });

        playlistViewModel.getSongsForPlaylist(playlistId).observe(getViewLifecycleOwner(), songs -> {
            currentSongs = songs != null ? songs : new ArrayList<>();
            songAdapter.submitList(new ArrayList<>(currentSongs));

            if (playlistMeta != null) {
                int count = currentSongs.size();
                long totalMs = 0;
                for (Song s : currentSongs) {
                    totalMs += s.getDurationMs();
                }
                String durationStr = formatTotalDuration(totalMs);
                playlistMeta.setText(count + (count == 1 ? " track" : " tracks")
                        + (durationStr.isEmpty() ? "" : " • " + durationStr));
            }

            // Empty state
            if (layoutEmpty != null) {
                layoutEmpty.setVisibility(currentSongs.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
    }

    // ===== Playback =====

    private void playAll(boolean shuffle) {
        if (currentSongs.isEmpty()) return;
        List<Song> queue = new ArrayList<>(currentSongs);
        if (shuffle) {
            Collections.shuffle(queue);
        }
        playbackViewModel.playSongs(queue, 0);
        if (shuffle) {
            // Notify PlaybackViewModel that we want shuffle mode on
            // (ExoPlayer remains authority; we just set the flag after)
            playbackViewModel.getShuffleEnabled().observeForever(
                    new androidx.lifecycle.Observer<Boolean>() {
                        @Override
                        public void onChanged(Boolean enabled) {
                            if (!Boolean.TRUE.equals(enabled)) {
                                playbackViewModel.toggleShuffle();
                            }
                            playbackViewModel.getShuffleEnabled().removeObserver(this);
                        }
                    });
        }
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

        new AlertDialog.Builder(requireContext())
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
                .setNegativeButton(R.string.cancel, null)
                .show();
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
        EditText input = new EditText(getContext());
        input.setText(playlistName);
        input.selectAll();
        int margin = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(margin, margin, margin, margin);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.rename_playlist)
                .setView(input)
                .setPositiveButton(R.string.rename, (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        playlistName = newName;
                        if (playlistTitle != null) playlistTitle.setText(newName);
                        playlistViewModel.renamePlaylist(playlistId, newName);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.confirm_delete_playlist)
                .setMessage(playlistName)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    playlistViewModel.deletePlaylist(playlistId);
                    Navigation.findNavController(requireView()).navigateUp();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
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
