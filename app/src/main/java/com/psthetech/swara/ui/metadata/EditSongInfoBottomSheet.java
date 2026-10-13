package com.psthetech.swara.ui.metadata;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.imageview.ShapeableImageView;
import com.psthetech.swara.R;
import com.psthetech.swara.data.repository.SongMetadataRepository;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.domain.model.SongMetadata;
import com.psthetech.swara.ui.theme.DesignTokens;
import com.psthetech.swara.ui.theme.MorphismThemeManager;
import com.psthetech.swara.ui.viewmodel.LibraryViewModel;
import com.psthetech.swara.ui.viewmodel.PlaybackViewModel;
import com.psthetech.swara.util.ArtworkHelper;
import com.psthetech.swara.util.MetadataNormalization;

public class EditSongInfoBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "EditSongInfoBottomSheet";
    private static final String ARG_SONG_ID = "arg_song_id";
    private static final String ARG_SONG_TITLE = "arg_song_title";
    private static final String ARG_SONG_ARTIST = "arg_song_artist";
    private static final String ARG_SONG_ALBUM = "arg_song_album";
    private static final String ARG_ALBUM_ID = "arg_album_id";
    private static final String ARG_DURATION = "arg_duration";
    private static final String ARG_TRACK_NUMBER = "arg_track_number";
    private static final String ARG_YEAR = "arg_year";
    private static final String ARG_DATE_ADDED = "arg_date_added";

    private Song initialSong;
    private SongMetadata originalMetadata;
    private SongMetadata pendingSaveMetadata;
    private SongMetadataRepository repository;

    private ActivityResultLauncher<IntentSenderRequest> promptLauncher;

    // UI Elements
    private ShapeableImageView ivEditorArtwork;
    private TextView tvPreviewTitle;
    private TextView tvPreviewSubtitle;
    private TextView tvFormatBadge;
    private EditText etTitle;
    private EditText etArtist;
    private EditText etAlbum;
    private EditText etAlbumArtist;
    private EditText etGenre;
    private EditText etYear;
    private EditText etTrack;
    private EditText etDisc;
    private EditText etComposer;
    private EditText etComment;
    private TextView btnCancelEdit;
    private TextView btnSaveEdit;
    private ProgressBar editorProgressBar;

    public static EditSongInfoBottomSheet newInstance(@NonNull Song song) {
        EditSongInfoBottomSheet fragment = new EditSongInfoBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_SONG_ID, song.getId());
        args.putString(ARG_SONG_TITLE, song.getTitle());
        args.putString(ARG_SONG_ARTIST, song.getArtist());
        args.putString(ARG_SONG_ALBUM, song.getAlbum());
        args.putLong(ARG_ALBUM_ID, song.getAlbumId());
        args.putLong(ARG_DURATION, song.getDuration());
        args.putInt(ARG_TRACK_NUMBER, song.getTrackNumber());
        args.putInt(ARG_YEAR, song.getYear());
        args.putLong(ARG_DATE_ADDED, song.getDateAdded());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            initialSong = new Song(
                    getArguments().getLong(ARG_SONG_ID),
                    getArguments().getString(ARG_SONG_TITLE),
                    getArguments().getString(ARG_SONG_ARTIST),
                    getArguments().getString(ARG_SONG_ALBUM),
                    getArguments().getLong(ARG_ALBUM_ID),
                    getArguments().getLong(ARG_DURATION),
                    getArguments().getInt(ARG_TRACK_NUMBER),
                    getArguments().getInt(ARG_YEAR),
                    getArguments().getLong(ARG_DATE_ADDED)
            );
        }

        repository = new SongMetadataRepository(requireContext());

        // Register Scoped Storage intent launcher for Android 10/11+ write permission prompts
        promptLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // User approved permission -> retry saving
                        if (pendingSaveMetadata != null && originalMetadata != null) {
                            executeSave(originalMetadata, pendingSaveMetadata);
                        }
                    } else {
                        Toast.makeText(requireContext(), "Permission denied to edit audio file", Toast.LENGTH_SHORT).show();
                        setLoading(false);
                    }
                }
        );
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_edit_song_info, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        applyDesignTokens(view);

        if (initialSong != null) {
            // Initial fast populate from Song model
            tvPreviewTitle.setText(initialSong.getTitle());
            tvPreviewSubtitle.setText(initialSong.getArtist());
            etTitle.setText(MetadataNormalization.getEditableValue(initialSong.getTitle()));
            etArtist.setText(MetadataNormalization.getEditableValue(initialSong.getArtist()));
            etAlbum.setText(MetadataNormalization.getEditableValue(initialSong.getAlbum()));
            if (initialSong.getYear() > 0) {
                etYear.setText(String.valueOf(initialSong.getYear()));
            }
            if (initialSong.getTrackNumber() > 0) {
                etTrack.setText(String.valueOf(initialSong.getTrackNumber()));
            }
            ArtworkHelper.loadSongArt(requireContext(), initialSong, ivEditorArtwork);

            // Fetch complete extended metadata asynchronously
            setLoading(true);
            repository.loadSongMetadata(initialSong, new SongMetadataRepository.MetadataLoadCallback() {
                @Override
                public void onLoaded(@NonNull SongMetadata metadata) {
                    originalMetadata = metadata;
                    populateMetadata(metadata);
                    setLoading(false);
                }

                @Override
                public void onError(@NonNull String error) {
                    setLoading(false);
                }
            });
        }

        btnCancelEdit.setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btnCloseEditor).setOnClickListener(v -> dismiss());
        btnSaveEdit.setOnClickListener(v -> onSaveClicked());
    }

    private void initViews(View v) {
        ivEditorArtwork = v.findViewById(R.id.ivEditorArtwork);
        tvPreviewTitle = v.findViewById(R.id.tvPreviewTitle);
        tvPreviewSubtitle = v.findViewById(R.id.tvPreviewSubtitle);
        tvFormatBadge = v.findViewById(R.id.tvFormatBadge);
        etTitle = v.findViewById(R.id.etTitle);
        etArtist = v.findViewById(R.id.etArtist);
        etAlbum = v.findViewById(R.id.etAlbum);
        etAlbumArtist = v.findViewById(R.id.etAlbumArtist);
        etGenre = v.findViewById(R.id.etGenre);
        etYear = v.findViewById(R.id.etYear);
        etTrack = v.findViewById(R.id.etTrack);
        etDisc = v.findViewById(R.id.etDisc);
        etComposer = v.findViewById(R.id.etComposer);
        etComment = v.findViewById(R.id.etComment);
        btnCancelEdit = v.findViewById(R.id.btnCancelEdit);
        btnSaveEdit = v.findViewById(R.id.btnSaveEdit);
        editorProgressBar = v.findViewById(R.id.editorProgressBar);
    }

    private void populateMetadata(SongMetadata m) {
        tvPreviewTitle.setText(m.getTitle());
        tvPreviewSubtitle.setText(m.getArtist() + (m.getAlbum().isEmpty() ? "" : " • " + m.getAlbum()));

        String mime = m.getMimeType();
        if (mime != null && !mime.isEmpty()) {
            String format = mime.contains("/") ? mime.substring(mime.lastIndexOf('/') + 1) : mime;
            tvFormatBadge.setText(format);
        } else {
            tvFormatBadge.setText("AUDIO");
        }

        etTitle.setText(MetadataNormalization.getEditableValue(m.getTitle()));
        etArtist.setText(MetadataNormalization.getEditableValue(m.getArtist()));
        etAlbum.setText(MetadataNormalization.getEditableValue(m.getAlbum()));
        etAlbumArtist.setText(MetadataNormalization.getEditableValue(m.getAlbumArtist()));
        etGenre.setText(MetadataNormalization.getEditableValue(m.getGenre()));
        etYear.setText(m.getYear() > 0 ? String.valueOf(m.getYear()) : "");
        etTrack.setText(m.getTrackNumber() > 0 ? String.valueOf(m.getTrackNumber()) : "");
        etDisc.setText(m.getDiscNumber() > 0 ? String.valueOf(m.getDiscNumber()) : "");
        etComposer.setText(MetadataNormalization.getEditableValue(m.getComposer()));
        etComment.setText(MetadataNormalization.getEditableValue(m.getComment()));
    }

    private void applyDesignTokens(View v) {
        DesignTokens tokens = MorphismThemeManager.getInstance().getCurrentTokens();
        if (tokens == null) return;

        float density = getResources().getDisplayMetrics().density;

        // Container background
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        int surfaceColor = tokens.isNightMode() ? tokens.getSurfaceElevatedColor() : tokens.getSurfaceColor();
        bg.setColor(surfaceColor);
        bg.setCornerRadii(new float[]{
                tokens.getCornerRadiusDp() * density, tokens.getCornerRadiusDp() * density,
                tokens.getCornerRadiusDp() * density, tokens.getCornerRadiusDp() * density,
                0, 0, 0, 0
        });
        v.setBackground(bg);

        // Header and labels
        ((TextView) v.findViewById(R.id.tvEditorHeader)).setTextColor(tokens.getTextPrimaryColor());
        ((ImageView) v.findViewById(R.id.btnCloseEditor)).setColorFilter(tokens.getTextSecondaryColor());

        tvPreviewTitle.setTextColor(tokens.getTextPrimaryColor());
        tvPreviewSubtitle.setTextColor(tokens.getTextSecondaryColor());
        tvFormatBadge.setTextColor(tokens.getAccentColor());

        // Card preview surface
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setShape(GradientDrawable.RECTANGLE);
        cardBg.setColor(tokens.isNightMode() ? tokens.getSurfaceColor() : tokens.getSurfaceVariantColor());
        cardBg.setCornerRadius(12 * density);
        cardBg.setStroke(Math.max(1, Math.round(density)), tokens.getStrokeColor());
        v.findViewById(R.id.cardPreview).setBackground(cardBg);

        int[] labelIds = {
                R.id.lblTitle, R.id.lblArtist, R.id.lblAlbum, R.id.lblAlbumArtist,
                R.id.lblGenre, R.id.lblYear, R.id.lblTrack, R.id.lblDisc,
                R.id.lblComposer, R.id.lblComment
        };
        for (int lid : labelIds) {
            TextView lbl = v.findViewById(lid);
            if (lbl != null) lbl.setTextColor(tokens.getTextSecondaryColor());
        }

        // Input containers styling
        int[] containerIds = {
                R.id.containerTitle, R.id.containerArtist, R.id.containerAlbum,
                R.id.containerAlbumArtist, R.id.containerGenre, R.id.containerYear,
                R.id.containerTrack, R.id.containerDisc, R.id.containerComposer,
                R.id.containerComment
        };

        for (int cid : containerIds) {
            View container = v.findViewById(cid);
            if (container != null) {
                GradientDrawable inputBg = new GradientDrawable();
                inputBg.setShape(GradientDrawable.RECTANGLE);
                inputBg.setColor(tokens.getSearchBackgroundColor());
                inputBg.setCornerRadius(10 * density);
                inputBg.setStroke(Math.max(1, Math.round(density)), tokens.getStrokeColor());
                container.setBackground(inputBg);

                if (container instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) container;
                    for (int i = 0; i < group.getChildCount(); i++) {
                        View child = group.getChildAt(i);
                        if (child instanceof EditText) {
                            EditText et = (EditText) child;
                            et.setTextColor(tokens.getTextPrimaryColor());
                            et.setHintTextColor(tokens.getSearchHintColor());
                            et.setOnFocusChangeListener((view1, hasFocus) -> {
                                if (hasFocus) {
                                    inputBg.setStroke(Math.max(1, Math.round(1.5f * density)), tokens.getAccentColor());
                                } else {
                                    inputBg.setStroke(Math.max(1, Math.round(density)), tokens.getStrokeColor());
                                }
                            });
                        }
                    }
                }
            }
        }

        // Cancel Button
        btnCancelEdit.setTextColor(tokens.getTextSecondaryColor());

        // Save Button
        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setShape(GradientDrawable.RECTANGLE);
        saveBg.setColor(tokens.getAccentColor());
        saveBg.setCornerRadius(tokens.getCornerRadiusDp() * density);
        btnSaveEdit.setBackground(saveBg);
        btnSaveEdit.setTextColor(tokens.getButtonTextColor());

        editorProgressBar.setIndeterminateTintList(
                android.content.res.ColorStateList.valueOf(tokens.getAccentColor()));
    }

    private void onSaveClicked() {
        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        if (title.isEmpty()) {
            Toast.makeText(requireContext(), R.string.field_title_required, Toast.LENGTH_SHORT).show();
            etTitle.requestFocus();
            return;
        }

        int year = MetadataNormalization.parseYear(etYear.getText() != null ? etYear.getText().toString() : null);
        if (year == -1) {
            Toast.makeText(requireContext(), R.string.invalid_year, Toast.LENGTH_SHORT).show();
            etYear.requestFocus();
            return;
        }

        int track = MetadataNormalization.parseTrackNumber(etTrack.getText() != null ? etTrack.getText().toString() : null);
        if (track == -1) {
            Toast.makeText(requireContext(), R.string.invalid_track, Toast.LENGTH_SHORT).show();
            etTrack.requestFocus();
            return;
        }

        int disc = MetadataNormalization.parseDiscNumber(etDisc.getText() != null ? etDisc.getText().toString() : null);
        if (disc == -1) {
            Toast.makeText(requireContext(), R.string.invalid_disc, Toast.LENGTH_SHORT).show();
            etDisc.requestFocus();
            return;
        }

        String artist = etArtist.getText() != null ? etArtist.getText().toString().trim() : "";
        String album = etAlbum.getText() != null ? etAlbum.getText().toString().trim() : "";
        String albumArtist = etAlbumArtist.getText() != null ? etAlbumArtist.getText().toString().trim() : "";
        String genre = etGenre.getText() != null ? etGenre.getText().toString().trim() : "";
        String composer = etComposer.getText() != null ? etComposer.getText().toString().trim() : "";
        String comment = etComment.getText() != null ? etComment.getText().toString().trim() : "";

        if (originalMetadata == null) {
            originalMetadata = new SongMetadata.Builder()
                    .setSongId(initialSong.getId())
                    .setTitle(initialSong.getTitle())
                    .setArtist(initialSong.getArtist())
                    .setAlbum(initialSong.getAlbum())
                    .setAlbumId(initialSong.getAlbumId())
                    .setDuration(initialSong.getDuration())
                    .setYear(initialSong.getYear())
                    .setTrackNumber(initialSong.getTrackNumber())
                    .setDateAdded(initialSong.getDateAdded())
                    .build();
        }

        pendingSaveMetadata = new SongMetadata.Builder(originalMetadata)
                .setTitle(title)
                .setArtist(artist)
                .setAlbum(album)
                .setAlbumArtist(albumArtist)
                .setGenre(genre)
                .setYear(year)
                .setTrackNumber(track)
                .setDiscNumber(disc)
                .setComposer(composer)
                .setComment(comment)
                .build();

        executeSave(originalMetadata, pendingSaveMetadata);
    }

    private void executeSave(SongMetadata original, SongMetadata updated) {
        setLoading(true);
        repository.saveSongMetadata(original, updated, result -> {
            if (!isAdded()) return;

            switch (result.getStatus()) {
                case SUCCESS:
                case PARTIAL_SUCCESS:
                    Toast.makeText(requireContext(), R.string.metadata_saved, Toast.LENGTH_SHORT).show();
                    Song updatedSong = updated.toSong();

                    // Update Playback in-place (Notification, ExoPlayer, Queue, Now Playing)
                    PlaybackViewModel playbackVm = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);
                    playbackVm.updateSongMetadataInPlace(updatedSong);

                    // Update Library list & groupings
                    LibraryViewModel libraryVm = new ViewModelProvider(requireActivity()).get(LibraryViewModel.class);
                    libraryVm.onSongMetadataUpdated(updatedSong);

                    dismiss();
                    break;

                case SYSTEM_PROMPT_REQUIRED:
                    if (result.getIntentSenderRequest() != null) {
                        promptLauncher.launch(result.getIntentSenderRequest());
                    } else {
                        Toast.makeText(requireContext(), R.string.metadata_save_failed, Toast.LENGTH_SHORT).show();
                        setLoading(false);
                    }
                    break;

                case FAILED:
                case UNSUPPORTED_FORMAT:
                case FILE_NOT_FOUND:
                default:
                    String msg = result.getMessage() != null ? result.getMessage() : getString(R.string.metadata_save_failed);
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                    setLoading(false);
                    break;
            }
        });
    }

    private void setLoading(boolean loading) {
        if (editorProgressBar != null) {
            editorProgressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (btnSaveEdit != null) {
            btnSaveEdit.setEnabled(!loading);
            btnSaveEdit.setAlpha(loading ? 0.6f : 1.0f);
        }
    }
}
