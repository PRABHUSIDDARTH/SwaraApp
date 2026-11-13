package com.psthetech.swara.voice.stt;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/**
 * Decode-only tokenizer for Moonshine's SentencePiece-style BPE vocabulary, read from a Hugging
 * Face {@code tokenizer.json}: {@code model.vocab} (token -> id) plus {@code added_tokens}.
 * Decoding skips special tokens, turns {@code <0xNN>} byte-fallback tokens into raw bytes
 * (interpreted as UTF-8), maps {@code ▁} to a space and trims the result.
 */
public final class MoonshineTokenizer {

    private static final char WORD_START = '▁';

    private final String[] tokens;
    private final boolean[] special;

    private MoonshineTokenizer(String[] tokens, boolean[] special) {
        this.tokens = tokens;
        this.special = special;
    }

    /** Parses {@code tokenizer.json}; does not close {@code tokenizerJson}. */
    public static MoonshineTokenizer load(InputStream tokenizerJson) throws IOException {
        try {
            JSONObject root = new JSONObject(readUtf8(tokenizerJson));
            JSONObject vocab = root.getJSONObject("model").getJSONObject("vocab");
            JSONArray added = root.optJSONArray("added_tokens");

            int maxId = -1;
            for (Iterator<String> it = vocab.keys(); it.hasNext(); ) {
                maxId = Math.max(maxId, vocab.getInt(it.next()));
            }
            int addedCount = added == null ? 0 : added.length();
            for (int i = 0; i < addedCount; i++) {
                maxId = Math.max(maxId, added.getJSONObject(i).getInt("id"));
            }

            String[] tokens = new String[maxId + 1];
            boolean[] special = new boolean[maxId + 1];
            for (Iterator<String> it = vocab.keys(); it.hasNext(); ) {
                String tok = it.next();
                tokens[vocab.getInt(tok)] = tok;
            }
            for (int i = 0; i < addedCount; i++) {
                JSONObject a = added.getJSONObject(i);
                int id = a.getInt("id");
                tokens[id] = a.getString("content");
                special[id] = a.optBoolean("special", false);
            }
            return new MoonshineTokenizer(tokens, special);
        } catch (JSONException e) {
            throw new IOException("bad tokenizer.json", e);
        }
    }

    /** Turns token ids into text; unknown ids and special tokens are skipped. */
    public String decode(int[] ids) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int id : ids) {
            if (id < 0 || id >= tokens.length || special[id] || tokens[id] == null) continue;
            String tok = tokens[id];
            int b = byteFallback(tok);
            if (b >= 0) {
                bytes.write(b);
            } else {
                byte[] utf8 = tok.replace(WORD_START, ' ').getBytes(StandardCharsets.UTF_8);
                bytes.write(utf8, 0, utf8.length);
            }
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8).trim();
    }

    /** The byte value of a {@code <0xNN>} token, or -1. */
    private static int byteFallback(String tok) {
        if (tok.length() != 6 || !tok.startsWith("<0x") || tok.charAt(5) != '>') return -1;
        try {
            return Integer.parseInt(tok.substring(3, 5), 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String readUtf8(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[64 * 1024];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
