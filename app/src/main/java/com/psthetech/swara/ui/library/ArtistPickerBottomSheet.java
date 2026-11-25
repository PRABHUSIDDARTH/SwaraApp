package com.psthetech.swara.ui.library;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.psthetech.swara.R;
import com.psthetech.swara.SwaraApplication;
import com.psthetech.swara.data.repository.ArtistAliasRepository;
import com.psthetech.swara.data.repository.MusicRepository;
import com.psthetech.swara.domain.model.Artist;
import com.psthetech.swara.ui.theme.DesignTokens;
import com.psthetech.swara.ui.theme.MorphismThemeManager;
import com.psthetech.swara.util.ArtistIdentityHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Searchable list of merged artists (current one excluded). Host fragment implements Listener. */
public class ArtistPickerBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "ArtistPickerBottomSheet";
    private static final String ARG_EXCLUDE = "excludeKey";

    public interface Listener {
        void onArtistPicked(@NonNull String pickedKey, @NonNull String pickedName);
    }

    public static ArtistPickerBottomSheet newInstance(@NonNull String excludeKey) {
        ArtistPickerBottomSheet s = new ArtistPickerBottomSheet();
        Bundle b = new Bundle();
        b.putString(ARG_EXCLUDE, excludeKey);
        s.setArguments(b);
        return s;
    }

    private final List<Artist> all = new ArrayList<>();
    private final List<Artist> shown = new ArrayList<>();
    private RecyclerView.Adapter<Holder> adapter;
    private DesignTokens tokens;
    private TextView title;
    private EditText search;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_artist_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        title = view.findViewById(R.id.tvPickerTitle);
        search = view.findViewById(R.id.etPickerSearch);
        RecyclerView rv = view.findViewById(R.id.rvPickerArtists);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RecyclerView.Adapter<Holder>() {
            @NonNull
            @Override
            public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new Holder(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_artist_picker, parent, false));
            }

            @Override
            public void onBindViewHolder(@NonNull Holder h, int position) {
                Artist a = shown.get(position);
                h.name.setText(a.getName());
                String count = getResources().getQuantityString(R.plurals.artist_picker_songs,
                        a.getSongCount(), a.getSongCount());
                h.count.setText(count);
                h.itemView.setContentDescription(getString(R.string.artist_picker_row_cd,
                        a.getName(), a.getSongCount()));
                if (tokens != null) {
                    h.name.setTextColor(tokens.getTextPrimaryColor());
                    h.count.setTextColor(tokens.getTextSecondaryColor());
                }
                h.itemView.setOnClickListener(v -> {
                    androidx.fragment.app.Fragment host = getParentFragment();
                    if (host instanceof Listener) {
                        ((Listener) host).onArtistPicked(a.getCanonicalKey(), a.getName());
                    }
                    dismiss();
                });
            }

            @Override
            public int getItemCount() {
                return shown.size();
            }
        };
        rv.setAdapter(adapter);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { filter(s.toString()); }
        });

        MorphismThemeManager.getInstance().getDesignTokens().observe(getViewLifecycleOwner(), t -> {
            if (t == null || getView() == null) return;
            tokens = t;
            getView().setBackground(t.createAmbientDrawable());
            title.setTextColor(t.getTextPrimaryColor());
            search.setTextColor(t.getTextPrimaryColor());
            search.setHintTextColor(t.getTextTertiaryColor());
            search.setBackground(t.createCardDrawable(requireContext()));
            search.setPadding(dp(16), 0, dp(16), 0);
            adapter.notifyDataSetChanged();
        });

        load();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void load() {
        final String exclude = requireArguments().getString(ARG_EXCLUDE, "");
        final android.content.Context app = requireContext().getApplicationContext();
        SwaraApplication.getInstance().getIoExecutor().execute(() -> {
            List<Artist> built = ArtistIdentityHelper.buildArtists(
                    MusicRepository.getCachedLibrarySongs(),
                    ArtistAliasRepository.get(app).resolver());
            List<Artist> out = new ArrayList<>();
            for (Artist a : built) {
                if (!a.getCanonicalKey().equals(exclude)) out.add(a);
            }
            View v = getView();
            if (v == null) return;
            v.post(() -> {
                if (getView() == null) return;
                all.clear();
                all.addAll(out);
                filter(search.getText().toString());
            });
        });
    }

    private void filter(String q) {
        String needle = q.trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (Artist a : all) {
            if (needle.isEmpty() || a.getName().toLowerCase(Locale.ROOT).contains(needle)) shown.add(a);
        }
        adapter.notifyDataSetChanged();
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView count;
        Holder(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.tvPickerName);
            count = v.findViewById(R.id.tvPickerCount);
        }
    }
}
