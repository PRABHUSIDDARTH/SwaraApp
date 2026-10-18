package com.psthetech.swara.ui.playlists;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.psthetech.swara.R;
import com.psthetech.swara.data.db.entity.Playlist;
import com.psthetech.swara.data.db.entity.PlaylistSong;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.adapter.PlaylistAdapter;
import com.psthetech.swara.ui.viewmodel.PlaybackViewModel;
import com.psthetech.swara.ui.viewmodel.PlaylistViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlaylistsFragment extends Fragment implements PlaylistAdapter.OnPlaylistClickListener {

    private PlaylistViewModel playlistViewModel;
    private PlaybackViewModel playbackViewModel;
    private RecyclerView recyclerView;
    private View layoutEmpty;
    private View btnNewPlaylist;
    private PlaylistAdapter playlistAdapter;

    // Snapshot of current playlists (needed for menu operations)
    private List<Playlist> currentPlaylists = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_playlists, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        playlistViewModel = new ViewModelProvider(requireActivity()).get(PlaylistViewModel.class);
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        recyclerView   = view.findViewById(R.id.recyclerView);
        layoutEmpty    = view.findViewById(R.id.layoutEmpty);
        btnNewPlaylist = view.findViewById(R.id.btnNewPlaylist);

        playlistAdapter = new PlaylistAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(playlistAdapter);

        if (btnNewPlaylist != null) {
            btnNewPlaylist.setOnClickListener(v -> showCreatePlaylistDialog());
        }

        observeData();
    }

    private void observeData() {
        playlistViewModel.getAllPlaylists().observe(getViewLifecycleOwner(), playlists -> {
            currentPlaylists = playlists != null ? playlists : new ArrayList<>();
            if (currentPlaylists.isEmpty()) {
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                playlistAdapter.submitList(currentPlaylists);
            }
        });

        // Load song counts for all playlists and push to adapter
        // Using a manual approach since we need counts across all playlists.
        // We observe each playlist's songs live and aggregate in the adapter.
        // Simplified: update adapter counts whenever playlist list changes.
        observeSongCounts();
    }

    /**
     * Loads song counts for visible playlists.
     * Calls getPlaylistSongsLive() for each playlist and aggregates into a map.
     * This re-subscribes when the playlists list changes.
     */
    private void observeSongCounts() {
        playlistViewModel.getAllPlaylists().observe(getViewLifecycleOwner(), playlists -> {
            if (playlists == null || playlists.isEmpty()) return;

            final Map<Long, Integer> counts = new HashMap<>();
            final int[] pending = {playlists.size()};

            for (Playlist p : playlists) {
                playlistViewModel.getSongsForPlaylist(p.id)
                        .observe(getViewLifecycleOwner(), songs -> {
                            counts.put(p.id, songs != null ? songs.size() : 0);
                            // Update adapter each time any count updates
                            playlistAdapter.setSongCounts(new HashMap<>(counts));
                        });
            }
        });
    }

    private void showCreatePlaylistDialog() {
        EditText input = new EditText(getContext());
        input.setHint(R.string.playlist_name_hint);
        int margin = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(margin, margin, margin, margin);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.create_playlist)
                .setView(input)
                .setPositiveButton(R.string.create, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        playlistViewModel.createPlaylist(name);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onPlaylistClick(Playlist playlist) {
        Bundle bundle = new Bundle();
        bundle.putLong("playlistId", playlist.id);
        bundle.putString("playlistName", playlist.name);
        Navigation.findNavController(requireView())
                .navigate(R.id.action_playlists_to_detail, bundle);
    }

    @Override
    public void onPlaylistMenuClick(View anchorView, Playlist playlist) {
        PopupMenu popup = new PopupMenu(requireContext(), anchorView);
        popup.getMenu().add(0, 1, 0, R.string.play);
        popup.getMenu().add(0, 2, 1, R.string.rename);
        popup.getMenu().add(0, 3, 2, R.string.delete);

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: playPlaylist(playlist); return true;
                case 2: showRenameDialog(playlist); return true;
                case 3: confirmDelete(playlist); return true;
            }
            return false;
        });
        popup.show();
    }

    private void playPlaylist(Playlist playlist) {
        // Load songs on background, then play
        com.psthetech.swara.SwaraApplication.getInstance().getDbExecutor().execute(() -> {
            List<PlaylistSong> psongs = playlistViewModel.getAllPlaylists().getValue() != null
                    ? new ArrayList<>() : new ArrayList<>();
            // Fetch directly via repository (blocking OK on bg thread)
            List<PlaylistSong> entries =
                    new com.psthetech.swara.data.repository.PlaylistRepository(
                            com.psthetech.swara.data.db.AppDatabase.getInstance(
                                    requireContext().getApplicationContext()))
                            .getPlaylistSongsBlocking(playlist.id);

            List<Song> songs = new ArrayList<>();
            for (PlaylistSong ps : entries) {
                songs.add(new Song(ps.songId, ps.title, ps.artist, ps.album,
                        ps.albumId, ps.duration, ps.position, 0, 0));
            }

            if (!songs.isEmpty()) {
                requireActivity().runOnUiThread(() ->
                        playbackViewModel.playSongs(songs, 0));
            }
        });
    }

    private void showRenameDialog(Playlist playlist) {
        EditText input = new EditText(getContext());
        input.setText(playlist.name);
        input.selectAll();
        int margin = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(margin, margin, margin, margin);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.rename_playlist)
                .setView(input)
                .setPositiveButton(R.string.rename, (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        playlistViewModel.renamePlaylist(playlist.id, newName);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDelete(Playlist playlist) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.confirm_delete_playlist)
                .setMessage(playlist.name)
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        playlistViewModel.deletePlaylist(playlist))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
