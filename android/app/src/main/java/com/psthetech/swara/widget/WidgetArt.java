package com.psthetech.swara.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.psthetech.swara.R;

/**
 * Bitmap helpers for the home-screen widget. RemoteViews cannot clip or tint
 * arbitrary shapes, so artwork rounding, the themed placeholder and the small
 * live level glyph are pre-rendered here.
 */
final class WidgetArt {

    /** Fixed artwork edge in px; keeps the RemoteViews parcel small. */
    static final int ART_SIZE_PX = 192;
    /** Continuous-corner approximation: ~22% of the edge, like Apple artwork tiles. */
    private static final float CORNER_FRACTION = 0.16f;

    private WidgetArt() {}

    /** Center-crops to a square and rounds corners. Never recycles the source. */
    @NonNull
    static Bitmap roundedSquare(@NonNull Bitmap src) {
        int size = ART_SIZE_PX;
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);

        float scale = Math.max(size / (float) src.getWidth(), size / (float) src.getHeight());
        float dx = (size - src.getWidth() * scale) / 2f;
        float dy = (size - src.getHeight() * scale) / 2f;
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        m.postTranslate(dx, dy);

        BitmapShader shader = new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        shader.setLocalMatrix(m);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(shader);
        float r = size * CORNER_FRACTION;
        canvas.drawRoundRect(new RectF(0, 0, size, size), r, r, p);
        return out;
    }

    /** Theme-tinted placeholder tile: accent gradient with a music-note glyph. */
    @NonNull
    static Bitmap placeholder(@NonNull Context context, @ColorInt int accent, @ColorInt int onAccent) {
        int size = ART_SIZE_PX;
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, size, size,
                lighten(accent, 0.18f), darken(accent, 0.28f), Shader.TileMode.CLAMP));
        float r = size * CORNER_FRACTION;
        canvas.drawRoundRect(new RectF(0, 0, size, size), r, r, p);

        Drawable note = ContextCompat.getDrawable(context, R.drawable.ic_widget_note);
        if (note != null) {
            note = note.mutate();
            note.setTint(onAccent);
            note.setAlpha(0xD9);
            int inset = Math.round(size * 0.28f);
            note.setBounds(inset, inset, size - inset, size - inset);
            note.draw(canvas);
        }
        return out;
    }

    /**
     * Three rounded level bars. Uneven heights while playing, a low flat line when
     * paused, so the state reads at a glance without animation.
     */
    @NonNull
    static Bitmap levelGlyph(@ColorInt int color, boolean playing, float seed) {
        int w = 42, h = 33;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);

        float barW = 9f, gap = (w - 3 * barW) / 2f;
        float[] levels = playing ? levelsFor(seed) : new float[]{0.22f, 0.22f, 0.22f};
        for (int i = 0; i < 3; i++) {
            float bh = Math.max(barW, h * levels[i]);
            float left = i * (barW + gap);
            canvas.drawRoundRect(new RectF(left, h - bh, left + barW, h), barW / 2f, barW / 2f, p);
        }
        return out;
    }

    /**
     * Rounded capsule seekbar: inactive track, accent fill and (on the full card)
     * a small knob at the playhead. Rendered at a fixed px size; the ImageView
     * stretches it horizontally, so the knob is kept small to stay round enough.
     */
    @NonNull
    static Bitmap seekBar(float fraction, @ColorInt int fill, @ColorInt int track, boolean withKnob) {
        int w = 600, h = withKnob ? 24 : 12;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float barH = withKnob ? 10f : 12f;
        float top = (h - barH) / 2f;
        float r = barH / 2f;
        p.setColor(track);
        c.drawRoundRect(new RectF(0, top, w, top + barH), r, r, p);
        float f = WidgetWaveformRenderer.clampProgress(fraction);
        float x = seekFillWidth(f, w);
        if (x > 0f) {
            p.setColor(fill);
            c.drawRoundRect(new RectF(0, top, Math.max(barH, x), top + barH), r, r, p);
        }
        if (withKnob) {
            float kx = Math.max(h / 2f, Math.min(w - h / 2f, x));
            p.setColor(fill);
            c.drawRoundRect(new RectF(kx - 5f, 0, kx + 5f, h), 5f, 5f, p);
        }
        return out;
    }

    /** Filled width in px for a fraction of the track. Package-private for tests. */
    static float seekFillWidth(float fraction, int width) {
        return WidgetWaveformRenderer.clampProgress(fraction) * Math.max(0, width);
    }

    /** Deterministic pseudo-random bar heights in [0.35, 1]. Package-private for tests. */
    static float[] levelsFor(float seed) {
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) {
            double v = Math.abs(Math.sin(seed * 2.3 + i * 1.7));
            out[i] = (float) (0.35 + 0.65 * v);
        }
        return out;
    }

    @ColorInt
    static int withAlpha(@ColorInt int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    @ColorInt
    private static int lighten(@ColorInt int c, float amt) {
        return Color.rgb(
                Math.round(Color.red(c) + (255 - Color.red(c)) * amt),
                Math.round(Color.green(c) + (255 - Color.green(c)) * amt),
                Math.round(Color.blue(c) + (255 - Color.blue(c)) * amt));
    }

    @ColorInt
    private static int darken(@ColorInt int c, float amt) {
        return Color.rgb(
                Math.round(Color.red(c) * (1 - amt)),
                Math.round(Color.green(c) * (1 - amt)),
                Math.round(Color.blue(c) * (1 - amt)));
    }

    /** Widget palette resolved from the app's design tokens. */
    static final class Palette {
        @ColorInt int surface, stroke, title, subtitle, eyebrow, glyph, secondaryGlyph, accent, onAccent, track;
        /** Multipliers on the alpha ramps baked into the glow / stroke drawables. */
        int glowAlpha, strokeAlpha;
        boolean night;

        @NonNull
        static Palette from(@Nullable com.psthetech.swara.ui.theme.DesignTokens t) {
            Palette p = new Palette();
            if (t == null) {
                p.night = true;
                p.surface = 0xFF1C1C1E;
                p.accent = 0xFFD4AF5A;
                p.onAccent = 0xFF1A1A1A;
                p.title = 0xFFFFFFFF;
                p.subtitle = 0x99EBEBF5;
                p.eyebrow = p.accent;
                p.glyph = p.title;
            } else {
                p.night = t.isNightMode();
                p.surface = p.night ? t.getSurfaceColor() : t.getSurfaceElevatedColor();
                p.accent = t.getAccentColor();
                p.onAccent = t.getOnAccentColor();
                p.title = t.getTextPrimaryColor();
                p.subtitle = t.getTextSecondaryColor();
                p.eyebrow = t.getReadableAccentColor();
                p.glyph = t.getTextPrimaryColor();
            }
            // Accent light wash: stronger on dark glass, whisper-quiet on light.
            p.secondaryGlyph = withAlpha(p.title, p.night ? 0x99 : 0x8C);
            p.track = withAlpha(p.title, p.night ? 0x40 : 0x26);
            p.glowAlpha = p.night ? 0xFF : 0x8C;
            p.stroke = p.night ? Color.WHITE : Color.BLACK;
            p.strokeAlpha = p.night ? 0xC0 : 0x70;
            return p;
        }
    }
}
