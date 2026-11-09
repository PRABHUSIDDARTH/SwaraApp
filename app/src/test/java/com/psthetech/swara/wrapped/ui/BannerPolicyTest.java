package com.psthetech.swara.wrapped.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.data.db.entity.Recap;

import org.junit.Test;

public class BannerPolicyTest {
    private static final long DAY = 86_400_000L;
    private static final long NOW = 1792231200000L;

    private static Recap recap(String key, long createdAt, boolean dismissed) {
        Recap r = new Recap();
        r.recapKey = key;
        r.createdAt = createdAt;
        r.bannerDismissed = dismissed;
        return r;
    }

    @Test
    public void shownForFourteenDays() {
        assertTrue(BannerPolicy.showBanner(recap("month-2026-09", NOW - 14 * DAY, false), NOW));
        assertFalse(BannerPolicy.showBanner(recap("month-2026-09", NOW - 14 * DAY - 1, false), NOW));
    }

    @Test
    public void hiddenOnceDismissed() {
        assertFalse(BannerPolicy.showBanner(recap("year-2026", NOW, true), NOW));
    }

    @Test
    public void onlyRealRecapKeys() {
        assertFalse(BannerPolicy.showBanner(recap("month-tmpcheck", NOW, false), NOW));
        assertTrue(BannerPolicy.showBanner(recap("year-2026", NOW, false), NOW));
        assertFalse(BannerPolicy.showBanner(null, NOW));
    }
}
