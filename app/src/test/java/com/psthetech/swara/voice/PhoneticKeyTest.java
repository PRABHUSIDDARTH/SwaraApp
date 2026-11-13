package com.psthetech.swara.voice;

import static org.junit.Assert.assertEquals;

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
    @Test public void emptyInputs() {
        assertEquals("", PhoneticKey.of(null));
        assertEquals("", PhoneticKey.of("  !! "));
    }
}
