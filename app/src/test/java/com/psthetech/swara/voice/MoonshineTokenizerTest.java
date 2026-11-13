package com.psthetech.swara.voice;

import static org.junit.Assert.assertEquals;

import com.psthetech.swara.voice.stt.MoonshineTokenizer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class MoonshineTokenizerTest {

    private static final int UNK = 0, BOS = 1, EOS = 2, BYTE_C3 = 3, BYTE_A9 = 4,
            PLAY = 5, KA = 6, VA = 7, LAA = 8, ST0 = 9;

    private MoonshineTokenizer tok;

    /** A tiny tokenizer.json in the Hugging Face layout: model.vocab plus added_tokens. */
    @Before public void setUp() throws Exception {
        JSONObject vocab = new JSONObject()
                .put("<unk>", UNK).put("<s>", BOS).put("</s>", EOS)
                .put("<0xC3>", BYTE_C3).put("<0xA9>", BYTE_A9)
                .put("▁play", PLAY).put("▁ka", KA).put("va", VA).put("laa", LAA);
        JSONArray added = new JSONArray()
                .put(added(UNK, "<unk>")).put(added(BOS, "<s>")).put(added(EOS, "</s>"))
                .put(added(ST0, "<<ST_0>>"));
        String json = new JSONObject()
                .put("added_tokens", added)
                .put("model", new JSONObject().put("type", "BPE").put("vocab", vocab))
                .toString();
        tok = MoonshineTokenizer.load(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static JSONObject added(int id, String content) throws Exception {
        return new JSONObject().put("id", id).put("content", content).put("special", true);
    }

    @Test public void joinsWordPiecesAndTrims() {
        assertEquals("play kavalaa", tok.decode(new int[]{PLAY, KA, VA, LAA}));
    }

    @Test public void skipsSpecialTokens() {
        assertEquals("play kavalaa", tok.decode(new int[]{BOS, PLAY, UNK, KA, VA, ST0, LAA, EOS}));
    }

    @Test public void decodesByteFallbackAsUtf8() {
        // <0xC3><0xA9> is the UTF-8 encoding of U+00E9 (e acute)
        assertEquals("kaé", tok.decode(new int[]{KA, BYTE_C3, BYTE_A9}));
        assertEquals("play kaéva", tok.decode(new int[]{PLAY, KA, BYTE_C3, BYTE_A9, VA}));
    }

    @Test public void ignoresUnknownIdsAndEmptyInput() {
        assertEquals("play", tok.decode(new int[]{PLAY, 999, -1}));
        assertEquals("", tok.decode(new int[0]));
    }
}
