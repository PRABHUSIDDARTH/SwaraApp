package com.psthetech.swara.wrapped.ui;

import androidx.annotation.Nullable;

import com.psthetech.swara.data.db.entity.Recap;

import java.util.regex.Pattern;

/** When Home shows the "Your Wrapped is ready" banner. Pure. */
public final class BannerPolicy {

    static final long SHOW_FOR_MS = 14L * 86_400_000L;
    private static final Pattern KEY = Pattern.compile("^(month-\\d{4}-\\d{2}|year-\\d{4})$");

    private BannerPolicy() {}

    public static boolean showBanner(@Nullable Recap r, long nowMs) {
        if (r == null || r.bannerDismissed || !KEY.matcher(r.recapKey).matches()) return false;
        return nowMs - r.createdAt <= SHOW_FOR_MS;
    }
}
