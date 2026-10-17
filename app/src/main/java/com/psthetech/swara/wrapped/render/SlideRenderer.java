package com.psthetech.swara.wrapped.render;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;

import com.psthetech.swara.R;
import com.psthetech.swara.wrapped.Day;
import com.psthetech.swara.wrapped.MoodBucket;
import com.psthetech.swara.wrapped.Period;
import com.psthetech.swara.wrapped.Personality;
import com.psthetech.swara.wrapped.SlidePlan;
import com.psthetech.swara.wrapped.SlideType;
import com.psthetech.swara.wrapped.SongInfo;
import com.psthetech.swara.wrapped.TitleCleaner;
import com.psthetech.swara.wrapped.WrappedStats;

import java.text.DateFormatSymbols;
import java.text.NumberFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Draws one Rhythm Pop Wrapped slide. The single drawing path for the story screen, the shared
 * PNG and the video frames: everything is laid out on a 1080×1920 logical canvas and scaled.
 * {@code progress} (0..1) drives the slide's entry animation; 1 is the settled frame.
 */
public final class SlideRenderer {

    public static final int W = 1080;
    public static final int H = 1920;
    private static final float PAD = 88f;

    private final Context ctx;
    private final Resources res;
    private final Typeface black = Typeface.create("sans-serif-black", Typeface.NORMAL);
    private final Typeface condensed = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

    public SlideRenderer(@NonNull Context context) {
        this.ctx = context.getApplicationContext();
        this.res = ctx.getResources();
    }

    /** Renders the settled frame at full resolution (for sharing). */
    @NonNull
    public static Bitmap renderFinalFrame(@NonNull Context context, @NonNull SlideType t,
                                          @NonNull WrappedStats s, @Nullable Bitmap art) {
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        new SlideRenderer(context).draw(new Canvas(out), W, H, t, s, 1f, art);
        return out;
    }

    public void draw(@NonNull Canvas c, int widthPx, int heightPx, @NonNull SlideType t,
                     @NonNull WrappedStats s, float progress, @Nullable Bitmap art) {
        float p = clamp01(progress);
        c.save();
        c.scale(widthPx / (float) W, heightPx / (float) H);
        int[] pair = RhythmPopPalette.pair(t.ordinal());
        int ink = RhythmPopPalette.textOn(pair);
        background(c, pair, p);
        switch (t) {
            case INTRO: intro(c, s, ink, p); break;
            case MINUTES: minutes(c, s, ink, p); break;
            case TOP_SONGS: topSongs(c, s, ink, p, art); break;
            case TOP_ARTISTS: topArtists(c, s, ink, p); break;
            case TOP_ALBUM: topAlbum(c, s, ink, p, art); break;
            case MOOD_MIX: moodMix(c, s, ink, p); break;
            case MOOD_OF_PERIOD: moodOf(c, s, ink, p); break;
            case VIBE: vibe(c, s, ink, p); break;
            case HABITS: habits(c, s, ink, p); break;
            case REPEAT_SKIP: repeatSkip(c, s, ink, p); break;
            case DISCOVERY: discovery(c, s, ink, p); break;
            case PERSONALITY: personality(c, s, ink, p); break;
            case SUMMARY: summary(c, s, ink, p, art); break;
        }
        footer(c, ink);
        c.restore();
    }

    // ===== Frame =====

    private void background(Canvas c, int[] pair, float p) {
        fill.setShader(new LinearGradient(0, 0, W * 0.6f, H, pair[0], pair[1], Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, fill);
        fill.setShader(null);
        // Two soft blobs that drift as the slide settles.
        float drift = ease(p) * 60f;
        fill.setColor(0x33FFFFFF);
        c.drawCircle(W * 0.92f - drift, H * 0.12f + drift, 300f, fill);
        fill.setColor(0x22000000);
        c.drawCircle(W * 0.08f + drift, H * 0.62f - drift * 0.5f, 240f, fill);
    }

    private void tag(Canvas c, String label, int ink, float y) {
        text.setTypeface(condensed);
        text.setTextSize(44f);
        float w = text.measureText(label);
        RectF r = new RectF(PAD, y - 58f, PAD + w + 56f, y + 22f);
        fill.setColor(ink == RhythmPopPalette.INK ? 0xFF160A26 : 0xFFFFFFFF);
        c.drawRoundRect(r, 40f, 40f, fill);
        text.setColor(ink == RhythmPopPalette.INK ? 0xFFFFFFFF : 0xFF160A26);
        c.drawText(label, PAD + 28f, y, text);
    }

    private void footer(Canvas c, int ink) {
        text.setTypeface(condensed);
        text.setTextSize(34f);
        text.setLetterSpacing(0.18f);
        text.setColor(withAlpha(ink, 0.8f));
        String f = str(R.string.wrapped_footer);
        c.drawText(f, W - PAD - text.measureText(f), H - 70f, text);
        text.setLetterSpacing(0f);
    }

    /** The slide's one bold element: an oversized word or number bleeding off the bottom-left. */
    private void giant(Canvas c, String s, int ink, float p, float baseline) {
        text.setTypeface(black);
        text.setLetterSpacing(-0.04f);
        float size = 520f;
        text.setTextSize(size);
        // Bleeds off the left edge only; every digit stays visible on the right.
        float maxW = W - PAD - (PAD - 24f);
        while (text.measureText(s) > maxW && size > 120f) {
            size -= 10f;
            text.setTextSize(size);
        }
        text.setColor(ink);
        float rise = (1f - ease(p)) * 160f;
        c.drawText(s, PAD - 24f, baseline + rise, text);
        text.setLetterSpacing(0f);
    }

    private float paragraph(Canvas c, String s, int ink, float size, Typeface tf, float y, float alpha) {
        text.setTypeface(tf);
        text.setTextSize(size);
        text.setColor(withAlpha(ink, alpha));
        String line = TextUtils.ellipsize(s, text, W - 2 * PAD, TextUtils.TruncateAt.END).toString();
        c.drawText(line, PAD, y, text);
        return y + size * 1.25f;
    }

    /** Wraps up to {@code maxLines} lines (ellipsizing the last); returns the y below the block. */
    private float wrapped(Canvas c, String s, int ink, float size, Typeface tf, float top, float alpha, int maxLines) {
        text.setTypeface(tf);
        text.setTextSize(size);
        text.setColor(withAlpha(ink, alpha));
        android.text.StaticLayout layout = android.text.StaticLayout.Builder
                .obtain(s, 0, s.length(), text, (int) (W - 2 * PAD))
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setLineSpacing(0f, 0.95f)
                .build();
        c.save();
        c.translate(PAD, top);
        layout.draw(c);
        c.restore();
        return top + layout.getHeight();
    }

    /** Draws {@code s} at up to {@code size}, shrinking until it fits {@code maxW}. */
    private void fitted(Canvas c, String s, float size, float minSize, float x, float y, float maxW) {
        text.setTextSize(size);
        while (text.measureText(s) > maxW && size > minSize) {
            size -= 4f;
            text.setTextSize(size);
        }
        c.drawText(TextUtils.ellipsize(s, text, maxW, TextUtils.TruncateAt.END).toString(), x, y, text);
    }

    /** Ranked list; rows slide in 120 ms apart over the slide's ~900 ms entrance. */
    private void ranked(Canvas c, List<String> rows, int ink, float p, float top, float rowH) {
        for (int i = 0; i < rows.size(); i++) {
            float local = clamp01((p * 900f - i * 120f) / 420f);
            if (local <= 0) continue;
            float y = top + i * rowH + (1f - ease(local)) * 50f;
            text.setTypeface(black);
            text.setTextSize(rowH * 0.62f);
            text.setColor(withAlpha(ink, ease(local)));
            String n = String.valueOf(i + 1);
            c.drawText(n, PAD, y, text);
            text.setTypeface(condensed);
            text.setTextSize(rowH * 0.46f);
            float x = PAD + rowH * 0.75f;
            String line = TextUtils.ellipsize(rows.get(i), text, W - x - PAD, TextUtils.TruncateAt.END).toString();
            c.drawText(line, x, y - rowH * 0.06f, text);
        }
    }

    private void artwork(Canvas c, @Nullable Bitmap art, RectF box, float p, int ink) {
        float scale = 0.92f + 0.08f * ease(p);
        float cx = box.centerX(), cy = box.centerY();
        RectF r = new RectF(cx - box.width() / 2 * scale, cy - box.height() / 2 * scale,
                cx + box.width() / 2 * scale, cy + box.height() / 2 * scale);
        if (art == null) {
            fill.setColor(withAlpha(ink, 0.18f));
            c.drawRoundRect(r, 44f, 44f, fill);
            return;
        }
        BitmapShader sh = new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        float sc = Math.max(r.width() / art.getWidth(), r.height() / art.getHeight());
        m.setScale(sc, sc);
        m.postTranslate(r.left + (r.width() - art.getWidth() * sc) / 2, r.top + (r.height() - art.getHeight() * sc) / 2);
        sh.setLocalMatrix(m);
        fill.setShader(sh);
        c.drawRoundRect(r, 44f, 44f, fill);
        fill.setShader(null);
    }

    // ===== Slides =====

    private void intro(Canvas c, WrappedStats s, int ink, float p) {
        float y = wrapped(c, str(R.string.wrapped_intro_title, periodName(s.period)), ink, 104f, black, 420f, ease(p), 3);
        paragraph(c, str(R.string.wrapped_intro_sub), ink, 54f, medium, y + 70f, 0.85f * ease(p));
        giant(c, periodShort(s.period), ink, p, H - 230f);
    }

    private void minutes(Canvas c, WrappedStats s, int ink, float p) {
        tag(c, str(R.string.wrapped_tag_minutes), ink, 300f);
        long minutes = s.totalMs / 60_000L;
        long shown = Math.round(minutes * ease(p));
        paragraph(c, str(R.string.wrapped_minutes_unit), ink, 72f, condensed, 1310f, 1f);
        int movies = (int) Math.max(1, minutes / 150);
        paragraph(c, plural(R.plurals.wrapped_movies, movies, movies), ink, 50f, medium, 1395f, 0.85f * ease(p));
        giant(c, number(shown), ink, p, 1150f);
    }

    private void topSongs(Canvas c, WrappedStats s, int ink, float p, @Nullable Bitmap art) {
        tag(c, str(R.string.wrapped_tag_top_songs), ink, 300f);
        artwork(c, art, new RectF(PAD, 400f, PAD + 520f, 920f), p, ink);
        ranked(c, songTitles(s, s.topSongIds), ink, p, 1110f, 150f);
    }

    private void topArtists(Canvas c, WrappedStats s, int ink, float p) {
        tag(c, str(R.string.wrapped_tag_top_artists), ink, 300f);
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String n : s.topArtistNames) names.add(TitleCleaner.clean(n));
        ranked(c, names, ink, p, 560f, 190f);
    }

    private void topAlbum(Canvas c, WrappedStats s, int ink, float p, @Nullable Bitmap art) {
        tag(c, str(R.string.wrapped_tag_top_album), ink, 300f);
        artwork(c, art, new RectF(PAD, 420f, W - PAD, 420f + (W - 2 * PAD)), p, ink);
        SongInfo info = firstOfAlbum(s);
        String album = info != null ? TitleCleaner.clean(info.album) : "";
        String artist = info != null ? TitleCleaner.clean(info.artist) : "";
        float y = paragraph(c, album, ink, 84f, black, 1500f, ease(p));
        paragraph(c, artist, ink, 52f, medium, y + 10f, 0.85f * ease(p));
    }

    private void moodMix(Canvas c, WrappedStats s, int ink, float p) {
        tag(c, str(R.string.wrapped_tag_mood_mix), ink, 300f);
        MoodBucket[] order = {MoodBucket.ENERGETIC, MoodBucket.HAPPY, MoodBucket.CALM, MoodBucket.SAD};
        float top = 520f, barH = 150f, gap = 90f, maxW = W - 2 * PAD;
        for (int i = 0; i < order.length; i++) {
            long ms = s.moodMs.containsKey(order[i]) ? s.moodMs.get(order[i]) : 0L;
            float share = s.moodKnownMs > 0 ? ms / (float) s.moodKnownMs : 0f;
            float local = clamp01((p * 900f - i * 120f) / 500f);
            float y = top + i * (barH + gap + 60f);
            paragraph(c, moodName(order[i]) + "  " + Math.round(share * 100) + "%", ink, 54f, condensed, y, 1f);
            fill.setColor(withAlpha(ink, 0.18f));
            c.drawRoundRect(new RectF(PAD, y + 30f, PAD + maxW, y + 30f + barH), 75f, 75f, fill);
            fill.setColor(ink);
            float w = Math.max(barH, maxW * share * ease(local));
            if (share > 0) c.drawRoundRect(new RectF(PAD, y + 30f, PAD + w, y + 30f + barH), 75f, 75f, fill);
        }
    }

    private void moodOf(Canvas c, WrappedStats s, int ink, float p) {
        tag(c, str(R.string.wrapped_tag_mood_of, periodName(s.period)), ink, 300f);
        MoodBucket top = null;
        long best = -1;
        for (MoodBucket b : MoodBucket.values()) {
            long ms = s.moodMs.containsKey(b) ? s.moodMs.get(b) : 0L;
            if (ms > best) { best = ms; top = b; }
        }
        int pct = s.moodKnownMs > 0 ? Math.round(best * 100f / s.moodKnownMs) : 0;
        paragraph(c, str(R.string.wrapped_mood_of_sub, pct), ink, 58f, medium, 1300f, ease(p));
        giant(c, top != null ? moodName(top) : "", ink, p, 1150f);
    }

    private void vibe(Canvas c, WrappedStats s, int ink, float p) {
        tag(c, str(R.string.wrapped_tag_vibe), ink, 300f);
        paragraph(c, plural(R.plurals.wrapped_vibe_sessions, s.vibeStarts), ink, 72f, condensed, 1260f, 1f);
        String fav = songTitle(s, s.topVibeSongId);
        if (!fav.isEmpty()) paragraph(c, str(R.string.wrapped_vibe_fav, fav), ink, 50f, medium, 1350f, 0.85f * ease(p));
        giant(c, number(Math.round(s.vibeStarts * ease(p))), ink, p, 1150f);
    }

    private void habits(Canvas c, WrappedStats s, int ink, float p) {
        tag(c, str(R.string.wrapped_tag_habits), ink, 300f);
        String[][] rows = {
                {str(R.string.wrapped_habit_peak), s.peakHour >= 0 ? hourName(s.peakHour) : "–"},
                {str(R.string.wrapped_habit_day), s.busiestWeekday >= 0 ? weekdayName(s.busiestWeekday) : "–"},
                {str(R.string.wrapped_habit_streak), plural(R.plurals.wrapped_days_in_row, s.longestStreakDays, s.longestStreakDays)},
        };
        for (int i = 0; i < rows.length; i++) {
            float local = clamp01((p * 900f - i * 120f) / 420f);
            float y = 560f + i * 400f + (1f - ease(local)) * 50f;
            paragraph(c, rows[i][0], ink, 52f, medium, y, 0.85f * ease(local));
            text.setTypeface(black);
            text.setTextSize(170f);
            text.setColor(withAlpha(ink, ease(local)));
            String v = TextUtils.ellipsize(rows[i][1], text, W - 2 * PAD, TextUtils.TruncateAt.END).toString();
            c.drawText(v, PAD - 6f, y + 190f, text);
        }
    }

    private void repeatSkip(Canvas c, WrappedStats s, int ink, float p) {
        float y = 300f;
        if (s.mostRepeatedSongId != -1) {
            tag(c, str(R.string.wrapped_tag_repeat), ink, y);
            y = wrapped(c, songTitle(s, s.mostRepeatedSongId), ink, 130f, black, y + 70f, ease(p), 2);
            y = wrapped(c, str(R.string.wrapped_repeat_line, songArtist(s, s.mostRepeatedSongId),
                    s.mostRepeatedCount), ink, 50f, medium, y + 24f, 0.85f * ease(p), 2);
            y += 180f;
        }
        if (s.mostSkippedSongId != -1) {
            tag(c, str(R.string.wrapped_skip_title), ink, y);
            y = wrapped(c, songTitle(s, s.mostSkippedSongId), ink, 130f, black, y + 70f, ease(p), 2);
            paragraph(c, songArtist(s, s.mostSkippedSongId), ink, 50f, medium, y + 60f, 0.85f * ease(p));
        }
    }

    private void discovery(Canvas c, WrappedStats s, int ink, float p) {
        tag(c, str(R.string.wrapped_tag_discovery), ink, 300f);
        paragraph(c, plural(R.plurals.wrapped_new_songs, s.newSongs), ink, 72f, condensed, 1260f, 1f);
        if (s.rediscoveredSongId != -1) {
            paragraph(c, str(R.string.wrapped_rediscovered, songTitle(s, s.rediscoveredSongId)), ink, 50f, medium, 1350f, 0.85f * ease(p));
        }
        giant(c, number(Math.round(s.newSongs * ease(p))), ink, p, 1150f);
    }

    private void personality(Canvas c, WrappedStats s, int ink, float p) {
        tag(c, str(R.string.wrapped_tag_personality), ink, 300f);
        Personality type = Personality.of(s);
        String[] words = str(personalityName(type)).split(" ");
        float y = 820f;
        text.setTypeface(black);
        text.setLetterSpacing(-0.04f);
        for (int i = 0; i < words.length; i++) {
            float local = clamp01((p * 900f - i * 160f) / 450f);
            float size = 300f;
            text.setTextSize(size);
            while (text.measureText(words[i]) > W - 2 * PAD + 120f && size > 120f) {
                size -= 10f;
                text.setTextSize(size);
            }
            text.setColor(withAlpha(ink, ease(local)));
            c.drawText(words[i], PAD - 16f, y + (1f - ease(local)) * 80f, text);
            y += size * 0.95f;
        }
        text.setLetterSpacing(0f);
        paragraph(c, str(personalityReason(type)), ink, 52f, medium, y + 60f, 0.9f * ease(p));
    }

    private void summary(Canvas c, WrappedStats s, int ink, float p, @Nullable Bitmap art) {
        tag(c, str(R.string.wrapped_tag_summary, periodName(s.period)), ink, 300f);
        artwork(c, art, new RectF(PAD, 400f, PAD + 360f, 760f), p, ink);
        ranked(c, songTitles(s, s.topSongIds.subList(0, Math.min(3, s.topSongIds.size()))), ink, p, 900f, 120f);
        String artist = s.topArtistNames.isEmpty() ? "–" : TitleCleaner.clean(s.topArtistNames.get(0));
        MoodBucket mood = null;
        long best = -1;
        for (MoodBucket b : MoodBucket.values()) {
            long ms = s.moodMs.containsKey(b) ? s.moodMs.get(b) : 0L;
            if (ms > best) { best = ms; mood = b; }
        }
        boolean moodShown = s.moodKnownMs * 2 >= s.totalMs && s.moodKnownMs > 0;
        String[][] cells = {
                {str(R.string.wrapped_summary_minutes), number(s.totalMs / 60_000L)},
                {str(R.string.wrapped_summary_top_artist), artist},
                {str(R.string.wrapped_summary_mood), moodShown && mood != null ? moodName(mood) : "–"},
                {str(R.string.wrapped_summary_type), str(personalityName(Personality.of(s)))},
        };
        float colW = (W - 2 * PAD) / 2f;
        for (int i = 0; i < cells.length; i++) {
            float x = PAD + (i % 2) * colW;
            float y = 1340f + (i / 2) * 230f;
            text.setTypeface(medium);
            text.setTextSize(42f);
            text.setColor(withAlpha(ink, 0.8f));
            c.drawText(cells[i][0], x, y, text);
            text.setTypeface(black);
            text.setColor(ink);
            fitted(c, cells[i][1], 70f, 40f, x, y + 86f, colW - 24f);
        }
    }

    // ===== Helpers =====

    private List<String> songTitles(WrappedStats s, List<Long> ids) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Long id : ids) out.add(songTitle(s, id));
        return out;
    }

    private String songArtist(WrappedStats s, long id) {
        SongInfo info = s.songs.get(id);
        return info != null ? TitleCleaner.clean(info.artist) : "";
    }

    private String songTitle(WrappedStats s, long id) {
        SongInfo info = s.songs.get(id);
        return info != null ? TitleCleaner.clean(info.title) : "";
    }

    @Nullable
    private SongInfo firstOfAlbum(WrappedStats s) {
        for (SongInfo i : s.songs.values()) if (i.albumId == s.topAlbumId) return i;
        return null;
    }

    private String periodName(Period p) {
        switch (p.kind) {
            case MONTH: return new DateFormatSymbols(Locale.getDefault()).getMonths()[Day.month(p.start) - 1];
            case YEAR: return String.valueOf(Day.year(p.start));
            default: return str(R.string.wrapped_period_all);
        }
    }

    /** Short form for the giant intro word: "Oct", "2026", "All". */
    private String periodShort(Period p) {
        switch (p.kind) {
            case MONTH: return new DateFormatSymbols(Locale.getDefault()).getShortMonths()[Day.month(p.start) - 1];
            case YEAR: return String.valueOf(Day.year(p.start));
            default: return "∞";
        }
    }

    private String moodName(MoodBucket b) {
        switch (b) {
            case SAD: return str(R.string.wrapped_mood_sad);
            case CALM: return str(R.string.wrapped_mood_calm);
            case HAPPY: return str(R.string.wrapped_mood_happy);
            default: return str(R.string.wrapped_mood_energetic);
        }
    }

    private static int personalityName(Personality t) {
        switch (t) {
            case NIGHT_OWL: return R.string.wrapped_p_night_owl;
            case EARLY_RISER: return R.string.wrapped_p_early_riser;
            case MOOD_LIFTER: return R.string.wrapped_p_mood_lifter;
            case LOYALIST: return R.string.wrapped_p_loyalist;
            case EXPLORER: return R.string.wrapped_p_explorer;
            case ENERGY_SEEKER: return R.string.wrapped_p_energy_seeker;
            default: return R.string.wrapped_p_balanced;
        }
    }

    private static int personalityReason(Personality t) {
        switch (t) {
            case NIGHT_OWL: return R.string.wrapped_p_night_owl_why;
            case EARLY_RISER: return R.string.wrapped_p_early_riser_why;
            case MOOD_LIFTER: return R.string.wrapped_p_mood_lifter_why;
            case LOYALIST: return R.string.wrapped_p_loyalist_why;
            case EXPLORER: return R.string.wrapped_p_explorer_why;
            case ENERGY_SEEKER: return R.string.wrapped_p_energy_seeker_why;
            default: return R.string.wrapped_p_balanced_why;
        }
    }

    private String hourName(int hour) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, 0);
        return android.text.format.DateFormat.getTimeFormat(ctx).format(cal.getTime());
    }

    private String weekdayName(int mondayIndex) {
        String[] names = new DateFormatSymbols(Locale.getDefault()).getWeekdays(); // index 1 = Sunday
        return names[((mondayIndex + 1) % 7) + 1];
    }

    private static String number(long n) {
        return NumberFormat.getIntegerInstance(Locale.getDefault()).format(n);
    }

    private String str(@StringRes int id, Object... args) {
        return res.getString(id, args);
    }

    private String plural(@PluralsRes int id, int count, Object... args) {
        return res.getQuantityString(id, count, args);
    }

    private static int withAlpha(int color, float a) {
        int alpha = Math.round(clamp01(a) * ((color >>> 24) & 0xFF));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static float ease(float t) {
        float x = clamp01(t);
        return 1f - (1f - x) * (1f - x) * (1f - x);
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    /** The song whose artwork a slide shows, or -1 for slides without artwork. */
    public static long artSongFor(@NonNull SlideType t, @NonNull WrappedStats s) {
        switch (t) {
            case TOP_SONGS:
            case SUMMARY:
                return s.topSongIds.isEmpty() ? -1 : s.topSongIds.get(0);
            case TOP_ALBUM:
                for (SongInfo i : s.songs.values()) if (i.albumId == s.topAlbumId) return i.songId;
                return -1;
            default:
                return -1;
        }
    }

    /** True when the slide set for these stats would be drawable (no recap below 30 minutes). */
    public static boolean hasSlides(@NonNull WrappedStats s) {
        return !SlidePlan.plan(s).isEmpty();
    }
}
