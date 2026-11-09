package com.psthetech.swara.wrapped;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.test.platform.app.InstrumentationRegistry;

import com.psthetech.swara.wrapped.render.SlideRenderer;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SlideRendererDeviceTest {

    static WrappedStats sample() {
        Map<Long, SongInfo> songs = new HashMap<>();
        String[][] t = {
                {"1", "Ooroda Oththa Don - MassTamilan", "Sai Abhyankkar, Vangal Pulla Vijay"},
                {"2", "Arabic Kuthu - Halamithi Habibo - MassTamilan.so", "Anirudh Ravichander"},
                {"3", "Thaarame Thaarame - MassTamilan.io", "Sid Sriram"},
                {"4", "Kannukulla - MassTamilan.dev", "Sai Abhyankkar, Jonita Gandhi"},
                {"5", "Chilla Chilla - MassTamilan.dev", "Anirudh Ravichander"},
                {"6", "Pookal Pookum - MassTamilan", "G. V. Prakash"},
        };
        List<DailyRow> rows = new ArrayList<>();
        Map<Long, double[]> moods = new HashMap<>();
        int i = 0;
        for (String[] s : t) {
            long id = Long.parseLong(s[0]);
            songs.put(id, new SongInfo(id, s[1], s[2], s[2].toLowerCase(), "Album " + id, id, 20261001 + i));
            rows.add(new DailyRow(20261001 + i, id, (7 - i) * 3_600_000L, 4, 1, i == 5 ? 4 : 0, i == 0 ? 6 : 0, i == 1 ? 6 : 0));
            rows.add(new DailyRow(20261010 + i, id, 1_200_000L, 2, 1, 0, 0, 0));
            moods.put(id, new double[]{0.2 + 0.12 * i, 0.3 + 0.1 * i});
            i++;
        }
        List<HourlyRow> hourly = Arrays.asList(new HourlyRow(20261001, 23, 9_000_000), new HourlyRow(20261002, 22, 6_000_000),
                new HourlyRow(20261003, 9, 2_000_000));
        return WrappedStatsEngine.compute(Period.month(2026, 10), rows, new ArrayList<>(), hourly, songs, moods);
    }

    @Test
    public void rendersEverySlideAtEveryPhase() throws Exception {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        WrappedStats s = sample();
        File dir = new File(ctx.getCacheDir(), "slide_test");
        dir.mkdirs();
        for (SlideType type : SlideType.values()) {
            for (float p : new float[]{0f, 0.5f, 1f}) {
                Bitmap b = Bitmap.createBitmap(540, 960, Bitmap.Config.ARGB_8888);
                new SlideRenderer(ctx).draw(new android.graphics.Canvas(b), 540, 960, type, s, p, null);
                assertTrue(type + " is blank", notUniform(b));
                if (p == 1f) {
                    try (FileOutputStream out = new FileOutputStream(new File(dir, type.name() + ".png"))) {
                        b.compress(Bitmap.CompressFormat.PNG, 100, out);
                    }
                }
                b.recycle();
            }
        }
        Bitmap fin = SlideRenderer.renderFinalFrame(ctx, SlideType.SUMMARY, s, null);
        assertNotNull(fin);
        assertTrue(fin.getWidth() == 1080 && fin.getHeight() == 1920);
    }

    private static boolean notUniform(Bitmap b) {
        int first = b.getPixel(0, 0);
        for (int y = 0; y < b.getHeight(); y += 17) {
            for (int x = 0; x < b.getWidth(); x += 13) {
                if (b.getPixel(x, y) != first) return true;
            }
        }
        return false;
    }
}
