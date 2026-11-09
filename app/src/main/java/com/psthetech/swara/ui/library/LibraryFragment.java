package com.psthetech.swara.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.psthetech.swara.R;
import com.psthetech.swara.ui.adapter.LibraryPagerAdapter;

public class LibraryFragment extends Fragment {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tabLayout = view.findViewById(R.id.tabLayout);
        viewPager = view.findViewById(R.id.viewPager);

        LibraryPagerAdapter adapter = new LibraryPagerAdapter(this);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText(R.string.tab_songs);
                    break;
                case 1:
                    tab.setText(R.string.tab_albums);
                    break;
                case 2:
                    tab.setText(R.string.tab_artists);
                    break;
            }
        }).attach();

        com.psthetech.swara.ui.theme.MorphismThemeManager.getInstance()
                .getDesignTokens().observe(getViewLifecycleOwner(), tokens -> {
                    if (tokens == null || getView() == null) return;
                    view.setBackground(tokens.createAmbientDrawable());
                    tabLayout.setBackground(tokens.createGlassPillDrawable(requireContext()));

                    float density = requireContext().getResources().getDisplayMetrics().density;
                    int accent = tokens.getAccentColor();
                    int r = android.graphics.Color.red(accent);
                    int g = android.graphics.Color.green(accent);
                    int b = android.graphics.Color.blue(accent);
                    int pillAlpha = tokens.isNightMode() ? 100 : 55;

                    android.graphics.drawable.GradientDrawable pill =
                            new android.graphics.drawable.GradientDrawable();
                    pill.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                    pill.setColor(android.graphics.Color.argb(pillAlpha, r, g, b));
                    pill.setCornerRadius(17f * density);

                    int readableAccent = tokens.getReadableAccentColor();
                    int sr = android.graphics.Color.red(readableAccent);
                    int sg = android.graphics.Color.green(readableAccent);
                    int sb = android.graphics.Color.blue(readableAccent);
                    pill.setStroke(Math.max(1, Math.round(1f * density)),
                            android.graphics.Color.argb(tokens.isNightMode() ? 160 : 120, sr, sg, sb));

                    android.graphics.drawable.InsetDrawable insetPill =
                            new android.graphics.drawable.InsetDrawable(
                                    pill,
                                    Math.round(-14f * density),
                                    Math.round(5f * density),
                                    Math.round(-14f * density),
                                    Math.round(5f * density)
                            );
                    tabLayout.setSelectedTabIndicator(insetPill);
                    tabLayout.setTabTextColors(tokens.getTextSecondaryColor(), tokens.getReadableAccentColor());
                });
    }
}
