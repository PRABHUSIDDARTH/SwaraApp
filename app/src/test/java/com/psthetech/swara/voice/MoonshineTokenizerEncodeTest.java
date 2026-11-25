package com.psthetech.swara.voice;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.psthetech.swara.voice.stt.MoonshineTokenizer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Checks the Java BPE encoder against ids produced by Hugging Face {@code tokenizers}. */
public class MoonshineTokenizerEncodeTest {

    private static MoonshineTokenizer tok;

    @BeforeClass public static void load() throws Exception {
        File f = new File("src/main/assets/voice/moonshine/tokenizer.json");
        if (!f.exists()) f = new File("app/src/main/assets/voice/moonshine/tokenizer.json");
        try (InputStream in = new FileInputStream(f)) {
            tok = MoonshineTokenizer.load(in);
        }
    }

    private static JSONArray fixture() throws Exception {
        try (InputStream in = MoonshineTokenizerEncodeTest.class.getClassLoader()
                .getResourceAsStream("voice/encode_fixture.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new JSONArray(new String(out.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    @Test public void encodeMatchesPythonTokenizers() throws Exception {
        JSONArray cases = fixture();
        assertTrue(cases.length() >= 200);
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            JSONArray ids = c.getJSONArray("ids");
            int[] want = new int[ids.length()];
            for (int j = 0; j < want.length; j++) want[j] = ids.getInt(j);
            assertArrayEquals("text=[" + c.getString("text") + "]", want, tok.encode(c.getString("text")));
        }
    }

    @Test public void decodeOfEncodeRoundTrips() {
        String[] ascii = {"play kaavaalaa", "Anirudh Ravichander", "A.R. Rahman", "Track 12",
                "What's Up?", "Rowdy Baby", "Rock & Roll"};
        for (String s : ascii) assertEquals(s, tok.decode(tok.encode(s)));
    }

    @Test public void isWordStartDetectsLeadingMarker() {
        int[] ids = tok.encode("play kaavaalaa");
        assertTrue(tok.isWordStart(ids[0]));
        boolean sawContinuation = false;
        for (int id : ids) if (!tok.isWordStart(id)) sawContinuation = true;
        assertTrue(sawContinuation);
        assertFalse(tok.isWordStart(-1));
        assertFalse(tok.isWordStart(99999999));
    }
}
