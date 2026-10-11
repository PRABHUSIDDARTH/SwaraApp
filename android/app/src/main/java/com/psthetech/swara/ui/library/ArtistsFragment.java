package com.psthetech.swara.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.psthetech.swara.R;
import com.psthetech.swara.domain.model.Artist;
import com.psthetech.swara.ui.adapter.ArtistAdapter;
import com.psthetech.swara.ui.viewmodel.LibraryViewModel;

public class ArtistsFragment extends Fragment implements ArtistAdapter.OnArtistClickListener {

    private LibraryViewModel libraryViewModel;
    private RecyclerView recyclerView;
    private View layoutEmpty;
    private ArtistAdapter artistAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_songs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        libraryViewModel = new ViewModelProvider(requireActivity()).get(LibraryViewModel.class);

        recyclerView = view.findViewById(R.id.recyclerView);
        layoutEmpty = view.findViewById(R.id.layoutEmpty);

        artistAdapter = new ArtistAdapter(this);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        recyclerView.setAdapter(artistAdapter);

        com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                .getDesignTokens().observe(getViewLifecycleOwner(), tokens -> {
                    if (tokens == null || getView() == null) return;
                    getView().setBackground(tokens.createAmbientDrawable());
                    View headerBar = getView().findViewById(R.id.headerBar);
                    if (headerBar != null) headerBar.setVisibility(View.GONE);
                    if (artistAdapter != null) artistAdapter.notifyDataSetChanged();
                });

        libraryViewModel.getArtists().observe(getViewLifecycleOwner(), artists -> {
            if (artists == null || artists.isEmpty()) {
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                if (layoutEmpty != null) layoutEmpty.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                artistAdapter.submitList(artists);
            }
        });
    }

    @Override
    public void onArtistClick(Artist artist) {
        Bundle bundle = new Bundle();
        bundle.putString("artistName", artist.getName());
        bundle.putString("canonicalKey", artist.getCanonicalKey());
        Navigation.findNavController(requireView()).navigate(R.id.action_library_to_artistDetail, bundle);
    }
}
