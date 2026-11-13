package com.psthetech.swara.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.voice.match.PhoneticKey;

import org.junit.Test;

public class PhoneticKeyTest {
    @Test public void soundAlikes() {
        assertEquals(PhoneticKey.of("Kaavaalaa"), PhoneticKey.of("kavalaa"));
        assertEquals("kavala", PhoneticKey.of("Kaavaalaa"));
        assertEquals(PhoneticKey.of("Rahman"), PhoneticKey.of("Rehman"));
        assertEquals(PhoneticKey.of("Thalapathy"), PhoneticKey.of("Talapati"));
        assertEquals(PhoneticKey.of("Azhagu"), PhoneticKey.of("Alagu"));
        assertEquals(PhoneticKey.of("Phir"), PhoneticKey.of("fir"));
        assertEquals(PhoneticKey.of("Wada"), PhoneticKey.of("vada"));
    }
    @Test public void indianFoldingEquatesAnirudhVariants() {
        assertEquals(PhoneticKey.of("Anirudh"), PhoneticKey.of("anirud"));
        int d = lev(
                PhoneticKey.joined(PhoneticKey.of("an erode")), PhoneticKey.joined(PhoneticKey.of("Anirudh")));
        assertTrue(d <= 2);
    }
    @Test public void emptyInputs() {
        assertEquals("", PhoneticKey.of(null));
        assertEquals("", PhoneticKey.of("  !! "));
    }
    private static int lev(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++)
            for (int j = 1; j <= b.length(); j++)
                d[i][j] = Math.min(Math.min(d[i-1][j] + 1, d[i][j-1] + 1),
                        d[i-1][j-1] + (a.charAt(i-1) == b.charAt(j-1) ? 0 : 1));
        return d[a.length()][b.length()];
    }
}
