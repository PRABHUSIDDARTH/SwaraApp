package com.psthetech.swara.voice;

import static org.junit.Assert.assertEquals;

import com.psthetech.swara.util.TagNoise;

import org.junit.Test;

public class TagNoiseTest {
    @Test public void stripsNoise() {
        assertEquals("roja", TagNoise.stripAlbumNoise("Roja (Original Motion Picture Soundtrack)"));
        assertEquals("roja", TagNoise.stripAlbumNoise("Roja - MassTamilan.com"));
        assertEquals("roja", TagNoise.stripAlbumNoise("Roja OST"));
        assertEquals("roja", TagNoise.stripAlbumNoise("Roja (From \"Roja\")"));
        assertEquals("scene", TagNoise.stripAlbumNoise("Scene"));
    }
}
