package com.psthetech.swara.ui.search;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.psthetech.swara.R;
import com.psthetech.swara.data.db.entity.Playlist;
import com.psthetech.swara.domain.model.Album;
import com.psthetech.swara.domain.model.Artist;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.adapter.SearchResultsAdapter;
import com.psthetech.swara.ui.playlists.AddToPlaylistDialog;
import com.psthetech.swara.ui.viewmodel.PlaylistViewModel;
import com.psthetech.swara.ui.viewmodel.SearchViewModel;

public class SearchFragment extends Fragment implements SearchResultsAdapter.Listener {

    private SearchViewModel searchViewModel;
    private PlaylistViewModel playlistViewModel;

    private TextInputEditText etSearch;
    private RecyclerView recyclerView;
    private View layoutPrompt;
    private TextView tvNoResults;
    private View progressBar;

    private SearchResultsAdapter searchAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        searchViewModel   = new ViewModelProvider(this).get(SearchViewModel.class);
        playlistViewModel = new ViewModelProvider(requireActivity()).get(PlaylistViewModel.class);
        com.psthetech.swara.ui.viewmodel.PlaybackViewModel playbackViewModel =
                new ViewModelProvider(requireActivity()).get(com.psthetech.swara.ui.viewmodel.PlaybackViewModel.class);

        com.google.android.material.textfield.TextInputLayout tilSearch = view.findViewById(R.id.tilSearch);
        etSearch     = view.findViewById(R.id.etSearch);
        recyclerView = view.findViewById(R.id.recyclerView);
        layoutPrompt = view.findViewById(R.id.layoutPrompt);
        tvNoResults  = view.findViewById(R.id.tvNoResults);
        progressBar  = view.findViewById(R.id.progressBar);

        searchAdapter = new SearchResultsAdapter(this);
        searchAdapter.setPlaylistArtworkStore(playlistViewModel.getPlaylistArtworkStore());
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(searchAdapter);

        // --- Observe current playing song for glow highlight ---
        playbackViewModel.getCurrentSong().observe(getViewLifecycleOwner(), song -> {
            long currentId = (song != null) ? song.getId() : -1L;
            searchAdapter.setCurrentPlayingSongId(currentId);
        });

        // --- Text watcher — debounce is inside SearchViewModel ---
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void afterTextChanged(Editable s) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    searchViewModel.search(s.toString());
                }
            });
        }

        com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                .getDesignTokens().observe(getViewLifecycleOwner(), tokens -> {
                    if (tokens == null || getView() == null) return;
                    view.setBackgroundColor(tokens.getBackgroundColor());
                    com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                            .applyToSearchInput(tilSearch, etSearch, tokens);

                    // Apply glass pill to search bar container
                    android.view.View glassContainer = view.findViewById(R.id.searchGlassContainer);
                    if (glassContainer != null) {
                        com.psthetech.swara.ui.glass.LiquidGlassRenderer.applyGlassPill(glassContainer, tokens);
                    }

                    if (layoutPrompt != null) {
                        TextView promptTitle = layoutPrompt.findViewById(R.id.tvPromptTitle);
                        TextView promptSub = layoutPrompt.findViewById(R.id.tvPromptSubtitle);
                        android.widget.ImageView promptIcon = layoutPrompt.findViewById(R.id.ivPromptIcon);
                        if (promptTitle != null) promptTitle.setTextColor(tokens.getTextPrimaryColor());
                        if (promptSub != null) promptSub.setTextColor(tokens.getTextSecondaryColor());
                        if (promptIcon != null) promptIcon.setColorFilter(tokens.getTextTertiaryColor());
                    }
                    if (tvNoResults != null) tvNoResults.setTextColor(tokens.getTextSecondaryColor());
                    if (progressBar != null && progressBar instanceof ProgressBar) {
                        ((ProgressBar) progressBar).setIndeterminateTintList(
                                android.content.res.ColorStateList.valueOf(tokens.getAccentColor()));
                    }
                    searchAdapter.notifyDataSetChanged();
                });

        observeData();
    }

    private void observeData() {
        // Loading spinner
        searchViewModel.getIsSearching().observe(getViewLifecycleOwner(), searching -> {
            if (progressBar != null) {
                progressBar.setVisibility(Boolean.TRUE.equals(searching) ? View.VISIBLE : View.GONE);
            }
        });

        // Results
        searchViewModel.getSearchResults().observe(getViewLifecycleOwner(), results -> {
            String query = etSearch != null ? etSearch.getText().toString().trim() : "";

            if (query.isEmpty()) {
                showPrompt();
                return;
            }

            if (results == null || results.isEmpty()) {
                showNoResults(query);
            } else {
                showResults(results);
            }
        });
    }

    private void showPrompt() {
        if (layoutPrompt != null) layoutPrompt.setVisibility(View.VISIBLE);
        if (tvNoResults != null) tvNoResults.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
    }

    private void showNoResults(String query) {
        if (layoutPrompt != null) layoutPrompt.setVisibility(View.GONE);
        if (tvNoResults != null) {
            tvNoResults.setText(getString(R.string.search_no_results, query));
            tvNoResults.setVisibility(View.VISIBLE);
        }
        recyclerView.setVisibility(View.GONE);
    }

    private void showResults(com.psthetech.swara.domain.model.SearchResults results) {
        if (layoutPrompt != null) layoutPrompt.setVisibility(View.GONE);
        if (tvNoResults != null) tvNoResults.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        searchAdapter.submitResults(results);
    }

    // ===== SearchResultsAdapter.Listener =====

    @Override
    public void onSongClick(Song song) {
        // Play the tapped song (single-song queue)
        new ViewModelProvider(requireActivity())
                .get(com.psthetech.swara.ui.viewmodel.PlaybackViewModel.class)
                .playSong(song);
    }

    @Override
    public void onAlbumClick(Album album) {
        Bundle args = new Bundle();
        args.putLong("albumId", album.getId());
        args.putString("albumTitle", album.getTitle());
        Navigation.findNavController(requireView())
                .navigate(R.id.action_search_to_albumDetail, args);
    }

    @Override
    public void onArtistClick(Artist artist) {
        Bundle args = new Bundle();
        args.putString("artistName", artist.getName());
        args.putString("canonicalKey", artist.getCanonicalKey());
        Navigation.findNavController(requireView())
                .navigate(R.id.action_search_to_artistDetail, args);
    }

    @Override
    public void onPlaylistClick(Playlist playlist) {
        Bundle args = new Bundle();
        args.putLong("playlistId", playlist.id);
        args.putString("playlistName", playlist.name);
        Navigation.findNavController(requireView())
                .navigate(R.id.action_search_to_playlistDetail, args);
    }

    @Override
    public void onSongAddToPlaylist(Song song) {
        AddToPlaylistDialog.show(requireActivity(), requireView(), song, playlistViewModel);
    }
}
