package com.psthetech.swara.ui.search;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.adapter.SongAdapter;
import com.psthetech.swara.ui.viewmodel.FavoritesViewModel;
import com.psthetech.swara.ui.viewmodel.LibraryViewModel;
import com.psthetech.swara.ui.viewmodel.PlaybackViewModel;

import java.util.HashSet;
import java.util.List;

public class SearchFragment extends Fragment implements SongAdapter.Listener {

    private LibraryViewModel libraryViewModel;
    private PlaybackViewModel playbackViewModel;
    private FavoritesViewModel favoritesViewModel;

    private TextInputEditText etSearch;
    private RecyclerView recyclerView;
    private View layoutPrompt;
    private View tvNoResults;

    private SongAdapter songAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        libraryViewModel = new ViewModelProvider(requireActivity()).get(LibraryViewModel.class);
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);
        favoritesViewModel = new ViewModelProvider(requireActivity()).get(FavoritesViewModel.class);

        etSearch = view.findViewById(R.id.etSearch);
        recyclerView = view.findViewById(R.id.recyclerView);
        layoutPrompt = view.findViewById(R.id.layoutPrompt);
        tvNoResults = view.findViewById(R.id.tvNoResults);

        songAdapter = new SongAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(songAdapter);

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String query = s.toString().trim();
                    libraryViewModel.searchSongs(query);
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        observeData();
    }

    private void observeData() {
        favoritesViewModel.getFavoriteSongIds().observe(getViewLifecycleOwner(), ids -> {
            if (ids != null) {
                songAdapter.setFavorites(new HashSet<>(ids));
            }
        });

        libraryViewModel.getSearchResults().observe(getViewLifecycleOwner(), songs -> {
            String query = etSearch != null ? etSearch.getText().toString().trim() : "";
            if (query.isEmpty()) {
                if (layoutPrompt != null) layoutPrompt.setVisibility(View.VISIBLE);
                if (tvNoResults != null) tvNoResults.setVisibility(View.GONE);
                recyclerView.setVisibility(View.GONE);
            } else if (songs == null || songs.isEmpty()) {
                if (layoutPrompt != null) layoutPrompt.setVisibility(View.GONE);
                if (tvNoResults != null) tvNoResults.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                if (layoutPrompt != null) layoutPrompt.setVisibility(View.GONE);
                if (tvNoResults != null) tvNoResults.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                songAdapter.submitList(songs);
            }
        });
    }

    @Override
    public void onSongClick(Song song, int position) {
        List<Song> songs = songAdapter.getCurrentList();
        playbackViewModel.playSongs(songs, position);
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
    }

    @Override
    public void onRemoveFromPlaylist(Song song) {
    }
}
