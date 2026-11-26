package com.psthetech.swara.ui.deck;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.psthetech.swara.R;
import com.psthetech.swara.audio.spectrum.SpectrumTap;
import com.psthetech.swara.data.repository.ArtworkRepository;
import com.psthetech.swara.domain.model.ColorTheme;
import com.psthetech.swara.domain.model.MorphismStyle;
import com.psthetech.swara.domain.model.Song;
import com.psthetech.swara.ui.theme.DesignTokens;
import com.psthetech.swara.ui.theme.MorphismThemeManager;
import com.psthetech.swara.ui.viewmodel.PlaybackViewModel;
import com.psthetech.swara.ui.widget.AppleScrubberView;

/**
 * Deck standby: a dim, black player with the reel and live bars. It keeps the screen on and may
 * show over the lock screen, so it offers playback controls only; nothing here opens the rest of
 * the app or changes the library.
 *
 * <p>This is not the system's Always On Display, which apps cannot draw on. It closes itself (see
 * {@link StandbyPolicy}), the power button still turns the screen off, and everything stops while
 * the screen is off.
 */
public class DeckStandbyActivity extends AppCompatActivity {

    private static final float BRIGHTNESS = 0.1f;
    private static final float SHIFT_DP = 12f;
    private static final long TICK_MS = 1000L;
    private static final String STATE_TOUCH = "last_touch";
    private static final String STATE_PAUSED = "paused_since";
    private static final String STATE_OPENED = "opened";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::onTick;

    private PlaybackViewModel playback;
    private View content;
    private DeckReelView reel;
    private DeckTimeView time;
    private DeckSpectrumView spectrum;
    private DeckFaderView fader;

    private long openedAt;
    private long lastTouchAt;
    private long pausedSince = -1;
    private long positionMs;
    private long durationMs;
    private long songId = -1;
    private boolean scrubbing;
    @Nullable private CustomTarget<Bitmap> artworkTarget;

    @NonNull
    public static Intent intent(@NonNull Context context) {
        return new Intent(context, DeckStandbyActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle saved) {
        super.onCreate(saved);
        showOverLockScreen();
        setContentView(R.layout.activity_deck_standby);
        goImmersive();

        long now = SystemClock.elapsedRealtime();
        openedAt = saved != null ? saved.getLong(STATE_OPENED, now) : now;
        lastTouchAt = saved != null ? saved.getLong(STATE_TOUCH, now) : now;
        pausedSince = saved != null ? saved.getLong(STATE_PAUSED, -1) : -1;

        content = findViewById(R.id.standbyContent);
        reel = findViewById(R.id.deckReel);
        time = findViewById(R.id.deckTime);
        spectrum = findViewById(R.id.deckSpectrum);
        fader = findViewById(R.id.scrubber);
        spectrum.setTap(SpectrumTap.shared());
        applyColors();
        keepClearOfCutout();

        playback = new ViewModelProvider(this).get(PlaybackViewModel.class);
        bindControls();
        observePlayback();
    }

    @SuppressWarnings("deprecation") // the window flag is the only option before API 27
    private void showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.screenBrightness = BRIGHTNESS;
        getWindow().setAttributes(attributes);
    }

    private void goImmersive() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat bars =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        bars.hide(WindowInsetsCompat.Type.systemBars());
    }

    private void keepClearOfCutout() {
        final int left = content.getPaddingLeft();
        final int top = content.getPaddingTop();
        final int right = content.getPaddingRight();
        final int bottom = content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.standbyRoot), (v, insets) -> {
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            content.setPadding(left + cutout.left, top + cutout.top,
                    right + cutout.right, bottom + cutout.bottom);
            return insets;
        });
    }

    /** Always the dark Deck colours of the chosen theme, on a black screen. */
    private void applyColors() {
        DesignTokens current = MorphismThemeManager.getInstance().getCurrentTokens();
        ColorTheme theme = current != null ? current.getColorTheme() : ColorTheme.SWARA;
        DeckPalette p = new DeckPalette(
                new DesignTokens(this, theme, MorphismStyle.LIQUID_GLASS, true));
        DeckStyler.paint(findViewById(R.id.standbyRoot), p);
        ((TextView) findViewById(R.id.tvTitle)).setTextColor(p.text);
        ((TextView) findViewById(R.id.tvArtist)).setTextColor(p.accentText);
    }

    private void bindControls() {
        findViewById(R.id.btnStandbyClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnPlayPause).setOnClickListener(v -> playback.togglePlayPause());
        findViewById(R.id.btnPrevious).setOnClickListener(v -> {
            spectrum.onUserSeek();
            playback.skipToPrevious();
        });
        findViewById(R.id.btnNext).setOnClickListener(v -> {
            spectrum.onUserSeek();
            playback.skipToNext();
        });
        reel.setSeekListener(deltaSeconds -> {
            if (durationMs <= 0) return;
            seek(Math.max(0, Math.min(durationMs, positionMs + (long) (deltaSeconds * 1000f))));
        });
        fader.setOnScrubListener(new AppleScrubberView.OnScrubListener() {
            @Override
            public void onScrubStart(AppleScrubberView view) {
                scrubbing = true;
            }

            @Override
            public void onScrubMove(AppleScrubberView view, long ms) {
                time.setTimes(ms, durationMs);
            }

            @Override
            public void onScrubEnd(AppleScrubberView view, long ms) {
                scrubbing = false;
                seek(ms);
            }
        });
    }

    private void seek(long ms) {
        spectrum.onUserSeek();
        playback.seekTo(ms);
        showPosition(ms);
    }

    private void observePlayback() {
        playback.getCurrentSong().observe(this, this::showSong);
        playback.getIsPlaying().observe(this, value -> {
            boolean playing = Boolean.TRUE.equals(value);
            if (playing) {
                pausedSince = -1;
            } else if (pausedSince < 0) {
                pausedSince = SystemClock.elapsedRealtime();
            }
            ImageView key = findViewById(R.id.btnPlayPause);
            key.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
            key.setContentDescription(getString(playing ? R.string.pause : R.string.play));
            reel.setPlaying(playing);
            spectrum.setPlaying(playing);
        });
        playback.getDurationMs().observe(this, value -> {
            durationMs = value != null ? Math.max(0, value) : 0;
            fader.setMax(durationMs);
            showPosition(positionMs);
        });
        playback.getCurrentPosition().observe(this, value -> {
            if (value != null && !scrubbing) showPosition(value);
        });
    }

    private void showPosition(long ms) {
        positionMs = Math.max(0, ms);
        fader.setProgress(positionMs);
        time.setTimes(positionMs, durationMs);
    }

    private void showSong(@Nullable Song song) {
        ((TextView) findViewById(R.id.tvTitle))
                .setText(song != null ? song.getTitle() : getString(R.string.widget_no_song));
        ((TextView) findViewById(R.id.tvArtist)).setText(song != null ? song.getArtist() : "");
        long id = song != null ? song.getId() : -1;
        if (id == songId) return;
        songId = id;
        reel.resetRotation();
        spectrum.onTrackChanged();
        reel.setContentDescription(song != null
                ? getString(R.string.cd_deck_reel_song, song.getTitle())
                : getString(R.string.cd_deck_reel));
        loadArtwork(song);
    }

    private void loadArtwork(@Nullable Song song) {
        clearArtwork();
        if (song == null) return;
        java.io.File custom = new ArtworkRepository(this).getCustomArtworkStore()
                .getArtworkFile(song.getId());
        android.net.Uri album = ArtworkRepository.getAlbumArtUri(song.getAlbumId());
        Object source = custom.exists() ? custom
                : album != null ? album : R.drawable.ic_artwork_fallback;
        artworkTarget = new CustomTarget<Bitmap>(768, 768) {
            @Override
            public void onResourceReady(@NonNull Bitmap resource,
                                        @Nullable Transition<? super Bitmap> transition) {
                reel.setArtworkBitmap(resource);
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
                reel.setArtworkBitmap(null); // Glide may reuse the bitmap
            }
        };
        Glide.with(this).asBitmap().load(source).centerCrop()
                .error(Glide.with(this).asBitmap().load(R.drawable.ic_artwork_fallback))
                .into(artworkTarget);
    }

    private void clearArtwork() {
        reel.setArtworkBitmap(null);
        if (artworkTarget != null) {
            Glide.with(this).clear(artworkTarget);
            artworkTarget = null;
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        lastTouchAt = SystemClock.elapsedRealtime();
    }

    @Override
    protected void onStart() {
        super.onStart();
        reel.setHostStarted(true);
        spectrum.setHostStarted(true);
        onTick(); // after a long screen-off the time limit may already have passed
    }

    @Override
    protected void onStop() {
        // Also runs when the screen turns off.
        handler.removeCallbacks(tick);
        reel.setHostStarted(false);
        spectrum.setHostStarted(false);
        super.onStop();
    }

    private void onTick() {
        handler.removeCallbacks(tick);
        long now = SystemClock.elapsedRealtime();
        if (StandbyPolicy.shouldClose(now, lastTouchAt, pausedSince)) {
            finish();
            return;
        }
        long minute = (now - openedAt) / 60_000L;
        int shift = Math.round(SHIFT_DP * getResources().getDisplayMetrics().density);
        content.setTranslationX(StandbyPolicy.shiftX(minute, shift));
        content.setTranslationY(StandbyPolicy.shiftY(minute, shift));
        handler.postDelayed(tick, TICK_MS);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putLong(STATE_OPENED, openedAt);
        out.putLong(STATE_TOUCH, lastTouchAt);
        out.putLong(STATE_PAUSED, pausedSince);
    }

    @Override
    protected void onDestroy() {
        // Only let go of the bitmap. Glide cancels this activity's loads itself, and
        // Glide.with() throws here because the activity already counts as destroyed.
        reel.setArtworkBitmap(null);
        artworkTarget = null;
        super.onDestroy();
    }
}
