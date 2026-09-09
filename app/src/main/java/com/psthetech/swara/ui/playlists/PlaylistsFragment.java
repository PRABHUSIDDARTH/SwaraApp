package com.psthetech.swara.ui.playlists;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.psthetech.swara.R;
import com.psthetech.swara.data.db.entity.Playlist;
import com.psthetech.swara.ui.adapter.PlaylistAdapter;
import com.psthetech.swara.ui.viewmodel.PlaylistViewModel;

public class PlaylistsFragment extends Fragment implements PlaylistAdapter.OnPlaylistClickListener {

    private PlaylistViewModel playlistViewModel;
    private RecyclerView recyclerView;
    private View layoutEmpty;
    private View btnNewPlaylist;
    private PlaylistAdapter playlistAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_playlists, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        playlistViewModel = new ViewModelProvider(requireActivity()).get(PlaylistViewModel.class);

        recyclerView = view.findViewById(R.id.recyclerView);
        layoutEmpty = view.findViewById(R.id.layoutEmpty);
        btnNewPlaylist = view.findViewById(R.id.btnNewPlaylist);

        playlistAdapter = new PlaylistAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(playlistAdapter);

        if (btnNewPlaylist != null) {
            btnNewPlaylist.setOnClickListener(v -> showCreatePlaylistDialog());
        }

        playlistViewModel.getAllPlaylists().observe(getViewLifecycleOwner(), playlists -> {
            if (playlists == null || playlists.isEmpty()) {
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                playlistAdapter.submitList(playlists);
            }
        });
    }

    private void showCreatePlaylistDialog() {
        EditText input = new EditText(getContext());
        input.setHint(R.string.playlist_name);
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
        Navigation.findNavController(requireView()).navigate(R.id.action_playlists_to_detail, bundle);
    }

    @Override
    public void onPlaylistMenuClick(View anchorView, Playlist playlist) {
        new AlertDialog.Builder(requireContext())
                .setTitle(playlist.name)
                .setItems(new CharSequence[]{getString(R.string.delete)}, (dialog, which) -> {
                    if (which == 0) {
                        playlistViewModel.deletePlaylist(playlist);
                    }
                })
                .show();
    }
}
