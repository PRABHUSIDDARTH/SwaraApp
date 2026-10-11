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
import com.psthetech.swara.ui.theme.DesignTokens;
import com.psthetech.swara.ui.theme.MorphismThemeManager;
import com.psthetech.swara.ui.theme.ThemedDialogHelper;
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
        playlistAdapter.setArtworkStore(playlistViewModel.getPlaylistArtworkStore());
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(playlistAdapter);

        if (btnNewPlaylist != null) {
            btnNewPlaylist.setOnClickListener(v -> showCreatePlaylistDialog());
        }

        // Observe design tokens for live theme updates
        com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                .getDesignTokens().observe(getViewLifecycleOwner(), tokens -> {
                    if (tokens == null || getView() == null) return;
                    getView().setBackground(tokens.createAmbientDrawable());
                    // Empty state
                    android.widget.TextView tvEmptyTitle =
                            getView().findViewById(R.id.tvEmptyTitle);
                    if (tvEmptyTitle != null) tvEmptyTitle.setTextColor(tokens.getTextPrimaryColor());
                    android.widget.TextView tvEmptySubtitle =
                            getView().findViewById(R.id.tvEmptySubtitle);
                    if (tvEmptySubtitle != null) tvEmptySubtitle.setTextColor(tokens.getTextSecondaryColor());
                    // New playlist button
                    if (btnNewPlaylist instanceof android.widget.TextView) {
                        ((android.widget.TextView) btnNewPlaylist).setTextColor(tokens.getButtonTextColor());
                    }
                    if (btnNewPlaylist != null) {
                        android.graphics.drawable.GradientDrawable bg =
                                new android.graphics.drawable.GradientDrawable();
                        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                        bg.setColor(tokens.getAccentColor());
                        float dp = getResources().getDisplayMetrics().density;
                        bg.setCornerRadius(tokens.getCornerRadiusDp() * dp);
                        btnNewPlaylist.setBackground(bg);
                    }
                    if (playlistAdapter != null) playlistAdapter.notifyDataSetChanged();
                });

        observeData();
    }

    private boolean pendingNewPlaylistAnimation = false;

    private void observeData() {
        playlistViewModel.getAllPlaylists().observe(getViewLifecycleOwner(), playlists -> {
            currentPlaylists = playlists != null ? playlists : new ArrayList<>();
            if (currentPlaylists.isEmpty()) {
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                playlistAdapter.submitList(currentPlaylists, () -> {
                    if (pendingNewPlaylistAnimation) {
                        pendingNewPlaylistAnimation = false;
                        recyclerView.scrollToPosition(0);
                        recyclerView.postDelayed(() -> {
                            RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(0);
                            if (vh != null) {
                                DesignTokens tokens = MorphismThemeManager.getInstance().getCurrentTokens();
                                ThemedDialogHelper.animateNewItemAppearance(vh.itemView, tokens);
                            }
                        }, 50);
                    }
                });
            }
        });

        // Load song counts for all playlists reactively
        playlistViewModel.getSongCountsMapLive().observe(getViewLifecycleOwner(), counts -> {
            if (playlistAdapter != null && counts != null) {
                playlistAdapter.setSongCounts(counts);
            }
        });
    }

    private void showCreatePlaylistDialog() {
        com.psthetech.swara.ui.theme.ThemedDialogHelper.showCreatePlaylistDialog(requireContext(), name -> {
            pendingNewPlaylistAnimation = true;
            playlistViewModel.createPlaylist(name);
        });
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
                Song canonical = com.psthetech.swara.data.repository.MusicRepository.getCanonicalSong(ps.songId);
                long dur = ps.duration > 0 ? ps.duration : (canonical != null ? canonical.getDuration() : 0);
                long albumId = ps.albumId != 0 ? ps.albumId : (canonical != null ? canonical.getAlbumId() : 0);
                songs.add(new Song(ps.songId, ps.title, ps.artist, ps.album,
                        albumId, dur, ps.position, canonical != null ? canonical.getYear() : 0, 0));
            }

            if (!songs.isEmpty()) {
                requireActivity().runOnUiThread(() ->
                        playbackViewModel.playSongs(songs, 0));
            }
        });
    }

    private void showRenameDialog(Playlist playlist) {
        com.psthetech.swara.ui.theme.ThemedDialogHelper.showRenamePlaylistDialog(
                requireContext(),
                playlist.name,
                newName -> playlistViewModel.renamePlaylist(playlist.id, newName));
    }

    private void confirmDelete(Playlist playlist) {
        com.psthetech.swara.ui.theme.ThemedDialogHelper.showConfirmationDialog(
                requireContext(),
                getString(R.string.confirm_delete_playlist),
                playlist.name,
                getString(R.string.delete),
                () -> playlistViewModel.deletePlaylist(playlist));
    }
}
