package com.psthetech.swara.ui.deck;

import android.graphics.Bitmap;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import com.psthetech.swara.R;
import com.psthetech.swara.audio.spectrum.SpectrumTap;
import com.psthetech.swara.domain.model.KorokaeState;
import com.psthetech.swara.ui.theme.DesignTokens;

/**
 * Shows player state on an inflated Deck layout. Now Playing keeps all the actions and hands the
 * look of each state to this class, so the standard player's colour code is never run on Deck
 * views. State is remembered, so a theme change can repaint everything.
 */
public final class DeckBinder {

    private final View root;
    private final DeckReelView reel;
    private final DeckTimeView time;
    private final DeckSpectrumView spectrum;
    @Nullable private DeckPalette palette;

    private boolean shuffle;
    private boolean repeat;
    private boolean favorite;
    private boolean sleeping;
    private boolean vibe;
    @Nullable private KorokaeState.Status korokae;

    private long positionMs;
    private long durationMs;
    private long shownPositionSecond = -1;
    private long shownDurationSecond = -1;

    private DeckBinder(@NonNull View root, @NonNull DeckReelView reel) {
        this.root = root;
        this.reel = reel;
        time = root.findViewById(R.id.deckTime);
        spectrum = root.findViewById(R.id.deckSpectrum);
        spectrum.setTap(SpectrumTap.shared());
    }

    /** Returns null if {@code root} is not a Deck layout. */
    @Nullable
    public static DeckBinder from(@NonNull View root) {
        DeckReelView reel = root.findViewById(R.id.deckReel);
        return reel == null ? null : new DeckBinder(root, reel);
    }

    @NonNull
    public DeckReelView reel() {
        return reel;
    }

    public void applyTokens(@NonNull DesignTokens tokens) {
        palette = DeckStyler.apply(root, tokens);
        render();
    }

    public void setPlaying(boolean playing) {
        reel.setPlaying(playing);
        spectrum.setPlaying(playing);
    }

    /** Call from onStart and onStop: nothing animates or taps audio while stopped. */
    public void setHostStarted(boolean started) {
        reel.setHostStarted(started);
        spectrum.setHostStarted(started);
    }

    public void onUserSeek() {
        spectrum.onUserSeek();
    }

    public void onTrackChanged() {
        reel.resetRotation();
        spectrum.onTrackChanged();
    }

    public void setSongTitle(@NonNull String title) {
        reel.setContentDescription(root.getResources().getString(R.string.cd_deck_reel_song, title));
    }

    public void setArtwork(@Nullable Bitmap bitmap) {
        reel.setArtworkBitmap(bitmap);
    }

    public void setPosition(long ms) {
        positionMs = ms;
        updateTime();
    }

    public void setDuration(long ms) {
        durationMs = ms;
        updateTime();
    }

    private void updateTime() {
        // The position arrives twice a second; the readout and its description change once.
        if (positionMs / 1000 == shownPositionSecond && durationMs / 1000 == shownDurationSecond) {
            return;
        }
        shownPositionSecond = positionMs / 1000;
        shownDurationSecond = durationMs / 1000;
        time.setTimes(positionMs, durationMs);
    }

    public void setShuffle(boolean on) {
        shuffle = on;
        render();
    }

    public void setRepeat(boolean on) {
        repeat = on;
        render();
    }

    public void setFavorite(boolean on) {
        favorite = on;
        render();
    }

    public void setSleepTimer(boolean on) {
        sleeping = on;
        render();
    }

    public void setVibe(boolean on) {
        vibe = on;
        render();
    }

    public void setKorokae(@Nullable KorokaeState.Status status) {
        korokae = status;
        render();
    }

    private void render() {
        DeckPalette p = palette;
        if (p == null) return;
        lamp(R.id.btnShuffle, shuffle);
        lamp(R.id.btnRepeat, repeat);

        ImageView heart = root.findViewById(R.id.btnFavorite);
        heart.setColorFilter(favorite ? p.accentText : p.textSecondary);

        toggle(R.id.btnSleepTimer, sleeping, p);
        toggle(R.id.btnVibe, vibe, p);
        boolean karaoke = korokae == KorokaeState.Status.ACTIVE
                || korokae == KorokaeState.Status.PROCESSING;
        toggle(R.id.btnKorokae, karaoke, p);
        ImageView mic = root.findViewById(R.id.ivKorokaeIcon);
        mic.setAlpha(korokae == KorokaeState.Status.FAILED ? 0.4f : 1f);
    }

    /** A key that stays on: lamp, accent icon and a spoken state. */
    private void toggle(int id, boolean on, DeckPalette p) {
        lamp(id, on);
        View key = root.findViewById(id);
        int color = on ? p.accentText : p.icon;
        if (key instanceof ImageView) {
            ((ImageView) key).setColorFilter(color);
        } else if (id == R.id.btnVibe) {
            ((ImageView) root.findViewById(R.id.ivVibeIcon)).setColorFilter(color);
        } else {
            ((ImageView) root.findViewById(R.id.ivKorokaeIcon)).setColorFilter(color);
        }
        ViewCompat.setStateDescription(key,
                root.getResources().getString(on ? R.string.deck_state_on : R.string.deck_state_off));
    }

    private void lamp(int id, boolean on) {
        View key = root.findViewById(id);
        if (key instanceof DeckKeyView) {
            ((DeckKeyView) key).setLit(on);
        } else if (key instanceof DeckKeyLayout) {
            ((DeckKeyLayout) key).setLit(on);
        }
    }
}
