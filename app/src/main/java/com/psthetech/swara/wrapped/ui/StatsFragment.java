package com.psthetech.swara.wrapped.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.psthetech.swara.R;
import com.psthetech.swara.SwaraApplication;
import com.psthetech.swara.ui.theme.DesignTokens;
import com.psthetech.swara.ui.theme.MorphismThemeManager;
import com.psthetech.swara.wrapped.MoodBucket;
import com.psthetech.swara.wrapped.Period;
import com.psthetech.swara.wrapped.Personality;
import com.psthetech.swara.wrapped.RecapRepository;
import com.psthetech.swara.wrapped.SlidePlan;
import com.psthetech.swara.wrapped.SongInfo;
import com.psthetech.swara.wrapped.TitleCleaner;
import com.psthetech.swara.wrapped.WrappedStats;

import java.text.DateFormatSymbols;
import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Locale;

/** Always-available listening stats for this month, this year or all time. */
public class StatsFragment extends Fragment {

    private LinearLayout content;
    private ProgressBar loading;
    @Nullable private Period current;
    private int request;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_wrapped_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        content = view.findViewById(R.id.statsContent);
        loading = view.findViewById(R.id.statsLoading);
        view.findViewById(R.id.btnStatsBack).setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        MaterialButtonToggleGroup group = view.findViewById(R.id.statsPeriod);
        group.addOnButtonCheckedListener((g, id, checked) -> {
            if (checked) load(id);
        });
        DesignTokens tokens = MorphismThemeManager.getInstance().getCurrentTokens();
        if (tokens != null) {
            view.setBackgroundColor(tokens.getBackgroundColor());
            ((TextView) view.findViewById(R.id.tvStatsTitle)).setTextColor(tokens.getTextPrimaryColor());
        }
        load(group.getCheckedButtonId());
    }

    private void load(int buttonId) {
        final int req = ++request;
        loading.setVisibility(View.VISIBLE);
        content.removeAllViews();
        Context app = requireContext().getApplicationContext();
        SwaraApplication.getInstance().getIoExecutor().execute(() -> {
            RecapRepository repo = new RecapRepository(app);
            Period p = buttonId == R.id.periodYear ? RecapRepository.thisYear()
                    : buttonId == R.id.periodAll ? repo.allTime() : RecapRepository.thisMonth();
            WrappedStats s = repo.statsFor(p);
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded() || req != request) return;
                current = p;
                loading.setVisibility(View.GONE);
                render(s);
            });
        });
    }

    private void render(WrappedStats s) {
        DesignTokens t = MorphismThemeManager.getInstance().getCurrentTokens();
        int primary = t != null ? t.getTextPrimaryColor() : 0xFFFFFFFF;
        int secondary = t != null ? t.getTextSecondaryColor() : 0xB3FFFFFF;
        int accent = t != null ? t.getAccentColor() : 0xFFFFC300;
        Context c = requireContext();

        if (s.totalMs < SlidePlan.MIN_TOTAL_MS) {
            content.addView(text(c, getString(R.string.wrapped_not_enough), 18, secondary, false, 48));
            return;
        }
        long minutes = s.totalMs / 60_000L;
        content.addView(text(c, NumberFormat.getIntegerInstance().format(minutes), 72, primary, true, 16));
        content.addView(text(c, getString(R.string.wrapped_minutes_unit), 18, secondary, false, 0));

        content.addView(header(c, R.string.wrapped_tag_top_songs, accent));
        int n = 1;
        for (Long id : s.topSongIds) content.addView(row(c, n++, title(s, id), primary));
        if (!s.topArtistNames.isEmpty()) {
            content.addView(header(c, R.string.wrapped_tag_top_artists, accent));
            n = 1;
            for (String a : s.topArtistNames) content.addView(row(c, n++, TitleCleaner.clean(a), primary));
        }
        if (s.moodKnownMs > 0) {
            content.addView(header(c, R.string.wrapped_tag_mood_mix, accent));
            MoodBucket[] order = {MoodBucket.ENERGETIC, MoodBucket.HAPPY, MoodBucket.CALM, MoodBucket.SAD};
            for (MoodBucket b : order) {
                long ms = s.moodMs.containsKey(b) ? s.moodMs.get(b) : 0L;
                content.addView(bar(c, moodName(b), ms / (float) s.moodKnownMs, primary, accent));
            }
        }
        content.addView(header(c, R.string.wrapped_tag_habits, accent));
        if (s.peakHour >= 0) content.addView(pair(c, getString(R.string.wrapped_habit_peak), hourName(s.peakHour), primary, secondary));
        if (s.busiestWeekday >= 0) content.addView(pair(c, getString(R.string.wrapped_habit_day), weekday(s.busiestWeekday), primary, secondary));
        content.addView(pair(c, getString(R.string.wrapped_habit_streak),
                getResources().getQuantityString(R.plurals.wrapped_days_in_row, s.longestStreakDays, s.longestStreakDays),
                primary, secondary));
        content.addView(header(c, R.string.wrapped_tag_personality, accent));
        content.addView(text(c, getString(personality(Personality.of(s))), 30, primary, true, 4));

        MaterialButton play = new MaterialButton(c);
        play.setText(R.string.wrapped_play);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        lp.topMargin = dp(28);
        play.setLayoutParams(lp);
        play.setOnClickListener(v -> {
            if (current != null) startActivity(WrappedStoryActivity.forPeriod(requireContext(), current));
        });
        content.addView(play);
    }

    private String title(WrappedStats s, long id) {
        SongInfo i = s.songs.get(id);
        return i != null ? TitleCleaner.clean(i.title) : "";
    }

    private TextView text(Context c, String s, int sp, int color, boolean bold, int topDp) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setTypeface(Typeface.create(bold ? "sans-serif-black" : "sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView header(Context c, int res, int accent) {
        TextView v = text(c, getString(res), 15, accent, true, 28);
        v.setPadding(0, 0, 0, dp(6));
        return v;
    }

    private View row(Context c, int rank, String title, int color) {
        TextView v = text(c, rank + "   " + title, 17, color, false, 6);
        v.setMaxLines(1);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return v;
    }

    private View pair(Context c, String label, String value, int primary, int secondary) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setPadding(0, dp(8), 0, 0);
        TextView a = text(c, label, 16, secondary, false, 0);
        a.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView b = text(c, value, 16, primary, true, 0);
        b.setGravity(Gravity.END);
        l.addView(a);
        l.addView(b);
        return l;
    }

    private View bar(Context c, String label, float share, int primary, int accent) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(0, dp(8), 0, 0);
        l.addView(text(c, label + "  " + Math.round(share * 100) + "%", 15, primary, false, 0));
        View track = new View(c);
        GradientDrawable g = new GradientDrawable();
        g.setColor(accent);
        g.setCornerRadius(dp(6));
        track.setBackground(g);
        int width = Math.max(dp(10), Math.round((getResources().getDisplayMetrics().widthPixels - dp(48)) * share));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, dp(12));
        lp.topMargin = dp(4);
        track.setLayoutParams(lp);
        l.addView(track);
        return l;
    }

    private String moodName(MoodBucket b) {
        switch (b) {
            case SAD: return getString(R.string.wrapped_mood_sad);
            case CALM: return getString(R.string.wrapped_mood_calm);
            case HAPPY: return getString(R.string.wrapped_mood_happy);
            default: return getString(R.string.wrapped_mood_energetic);
        }
    }

    private static int personality(Personality p) {
        switch (p) {
            case NIGHT_OWL: return R.string.wrapped_p_night_owl;
            case EARLY_RISER: return R.string.wrapped_p_early_riser;
            case MOOD_LIFTER: return R.string.wrapped_p_mood_lifter;
            case LOYALIST: return R.string.wrapped_p_loyalist;
            case EXPLORER: return R.string.wrapped_p_explorer;
            case ENERGY_SEEKER: return R.string.wrapped_p_energy_seeker;
            default: return R.string.wrapped_p_balanced;
        }
    }

    private String hourName(int hour) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, 0);
        return android.text.format.DateFormat.getTimeFormat(requireContext()).format(cal.getTime());
    }

    private static String weekday(int mondayIndex) {
        return new DateFormatSymbols(Locale.getDefault()).getWeekdays()[((mondayIndex + 1) % 7) + 1];
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
