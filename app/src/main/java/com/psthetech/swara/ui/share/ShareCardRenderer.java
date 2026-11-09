package com.psthetech.swara.ui.share;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Renders a 9:16 "now playing" share card (fits Instagram/WhatsApp stories and chats).
 *
 * Layout: blurred, artwork-tinted backdrop; the artwork as a vinyl-style disc with a thin
 * ring (matching the Now Playing screen); title, artist and a small Swara footer.
 */
public final class ShareCardRenderer {

    public static final int WIDTH = 1080;
    public static final int HEIGHT = 1920;

    private ShareCardRenderer() { }

    @NonNull
    public static Bitmap render(@Nullable Bitmap artwork, @NonNull String title, @NonNull String artist,
                                @NonNull String footer, @ColorInt int tint) {
        Bitmap out = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        drawBackdrop(c, p, artwork, tint);

        float cx = WIDTH / 2f;
        float cy = 820f;
        float discR = 360f;

        // Soft glow and drop shadow behind the disc
        p.setShader(new RadialGradient(cx, cy, discR * 1.6f,
                new int[]{withAlpha(tint, 120), withAlpha(tint, 0)}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, discR * 1.6f, p);
        p.setShader(null);
        p.setColor(Color.argb(90, 0, 0, 0));
        c.drawCircle(cx, cy + 18f, discR, p);

        // Artwork disc
        if (artwork != null) {
            BitmapShader shader = new BitmapShader(artwork, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float d = discR * 2f;
            float scale = Math.max(d / artwork.getWidth(), d / artwork.getHeight());
            Matrix m = new Matrix();
            m.setScale(scale, scale);
            m.postTranslate(cx - artwork.getWidth() * scale / 2f, cy - artwork.getHeight() * scale / 2f);
            shader.setLocalMatrix(m);
            p.setShader(shader);
        } else {
            p.setColor(Color.rgb(44, 44, 46));
        }
        c.drawCircle(cx, cy, discR, p);
        p.setShader(null);

        // Vinyl spindle hole and rings
        p.setColor(Color.argb(200, 20, 20, 22));
        c.drawCircle(cx, cy, 26f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3f);
        p.setColor(Color.argb(70, 255, 255, 255));
        c.drawCircle(cx, cy, 26f, p);
        p.setColor(Color.argb(60, 255, 255, 255));
        c.drawCircle(cx, cy, discR, p);
        p.setStrokeWidth(8f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(Color.argb(60, 255, 255, 255));
        c.drawCircle(cx, cy, discR + 38f, p);
        p.setColor(Color.argb(235, 255, 255, 255));
        c.drawArc(cx - discR - 38f, cy - discR - 38f, cx + discR + 38f, cy + discR + 38f,
                -90f, 130f, false, p);
        p.setStyle(Paint.Style.FILL);

        // Text
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextAlign(Paint.Align.CENTER);
        float maxText = WIDTH - 160f;

        tp.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        tp.setTextSize(68f);
        tp.setLetterSpacing(-0.015f);
        tp.setColor(Color.WHITE);
        c.drawText(TextUtils.ellipsize(title, tp, maxText, TextUtils.TruncateAt.END).toString(),
                cx, 1390f, tp);

        tp.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        tp.setTextSize(46f);
        tp.setLetterSpacing(0f);
        tp.setColor(Color.argb(180, 255, 255, 255));
        c.drawText(TextUtils.ellipsize(artist, tp, maxText, TextUtils.TruncateAt.END).toString(),
                cx, 1462f, tp);

        // Footer pill
        tp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tp.setTextSize(32f);
        tp.setLetterSpacing(0.04f);
        Rect bounds = new Rect();
        tp.getTextBounds(footer, 0, footer.length(), bounds);
        float pillW = bounds.width() + 80f;
        float pillTop = HEIGHT - 230f;
        p.setColor(Color.argb(46, 255, 255, 255));
        c.drawRoundRect(cx - pillW / 2f, pillTop, cx + pillW / 2f, pillTop + 76f, 38f, 38f, p);
        tp.setColor(Color.argb(220, 255, 255, 255));
        c.drawText(footer, cx, pillTop + 38f - (tp.descent() + tp.ascent()) / 2f, tp);

        return out;
    }

    private static void drawBackdrop(Canvas c, Paint p, @Nullable Bitmap artwork, @ColorInt int tint) {
        float[] hsv = new float[3];
        Color.colorToHSV(tint, hsv);
        hsv[1] = Math.min(hsv[1], 0.6f);
        hsv[2] = 0.30f;
        int top = Color.HSVToColor(hsv);
        hsv[2] = 0.08f;
        int bottom = Color.HSVToColor(hsv);
        c.drawColor(bottom);

        if (artwork != null) {
            // Cheap, strong blur: shrink to a few pixels and scale back up with filtering.
            Bitmap tiny = Bitmap.createScaledBitmap(artwork, 12, 21, true);
            Bitmap blurred = Bitmap.createScaledBitmap(tiny, WIDTH / 8, HEIGHT / 8, true);
            p.setAlpha(150);
            c.drawBitmap(blurred, null, new Rect(0, 0, WIDTH, HEIGHT), p);
            p.setAlpha(255);
            tiny.recycle();
            blurred.recycle();
        }

        p.setShader(new LinearGradient(0, 0, 0, HEIGHT,
                new int[]{withAlpha(top, 170), withAlpha(bottom, 235)}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, WIDTH, HEIGHT, p);
        p.setShader(null);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
