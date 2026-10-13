package com.psthetech.swara.widget;

import static org.junit.Assert.assertEquals;

import com.psthetech.swara.R;

import org.junit.Test;

public class WidgetSeekTest {

    @Test
    public void seekZonesMapToTheirCentres() {
        assertEquals(0.05f, SwaraWidgetUpdater.zoneCenterFraction(0, 10), 1e-6f);
        assertEquals(0.55f, SwaraWidgetUpdater.zoneCenterFraction(5, 10), 1e-6f);
        assertEquals(0.95f, SwaraWidgetUpdater.zoneCenterFraction(9, 10), 1e-6f);
        // Out-of-range indices clamp instead of seeking past the track
        assertEquals(0.95f, SwaraWidgetUpdater.zoneCenterFraction(42, 10), 1e-6f);
        assertEquals(0.05f, SwaraWidgetUpdater.zoneCenterFraction(-3, 10), 1e-6f);
        assertEquals(0f, SwaraWidgetUpdater.zoneCenterFraction(3, 0), 0f);
    }

    @Test
    public void seekTargetIsClampedInsideTheTrack() {
        assertEquals(90_000L, SwaraWidgetReceiver.seekTargetMs(0.5f, 180_000L));
        assertEquals(0L, SwaraWidgetReceiver.seekTargetMs(-1f, 180_000L));
        assertEquals(179_999L, SwaraWidgetReceiver.seekTargetMs(2f, 180_000L));
        assertEquals(0L, SwaraWidgetReceiver.seekTargetMs(Float.NaN, 180_000L));
        assertEquals(0L, SwaraWidgetReceiver.seekTargetMs(0.5f, 0L));
    }

    @Test
    public void seekFillTracksFraction() {
        assertEquals(0f, WidgetArt.seekFillWidth(-0.2f, 600), 0f);
        assertEquals(300f, WidgetArt.seekFillWidth(0.5f, 600), 1e-3f);
        assertEquals(600f, WidgetArt.seekFillWidth(1.4f, 600), 1e-3f);
    }

    @Test
    public void stateFractionHandlesUnknownDuration() {
        assertEquals(0f, new SwaraWidgetUpdater.State(true, 5_000, 0, false, false).fraction(), 0f);
        assertEquals(0.25f, new SwaraWidgetUpdater.State(true, 15_000, 60_000, false, false).fraction(), 1e-6f);
        assertEquals(1f, new SwaraWidgetUpdater.State(true, 90_000, 60_000, false, false).fraction(), 1e-6f);
    }

    @Test
    public void legacyLayoutPickedFromSize() {
        assertEquals(R.layout.widget_swara_player_strip, SwaraWidgetUpdater.layoutForSize(300, 70));
        assertEquals(R.layout.widget_swara_player_vertical, SwaraWidgetUpdater.layoutForSize(150, 170));
        assertEquals(R.layout.widget_swara_player, SwaraWidgetUpdater.layoutForSize(320, 180));
        assertEquals(R.layout.widget_swara_player, SwaraWidgetUpdater.layoutForSize(0, 0));
    }
}
