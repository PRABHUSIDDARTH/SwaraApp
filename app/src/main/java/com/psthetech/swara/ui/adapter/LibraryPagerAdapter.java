package com.psthetech.swara.ui.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.psthetech.swara.ui.library.AlbumsFragment;
import com.psthetech.swara.ui.library.ArtistsFragment;
import com.psthetech.swara.ui.library.SongsFragment;

public class LibraryPagerAdapter extends FragmentStateAdapter {

    public LibraryPagerAdapter(@NonNull Fragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new SongsFragment();
            case 1:
                return new AlbumsFragment();
            case 2:
                return new ArtistsFragment();
            default:
                return new SongsFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
