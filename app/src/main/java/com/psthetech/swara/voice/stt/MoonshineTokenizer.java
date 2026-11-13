package com.psthetech.swara.voice.stt;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.PriorityQueue;

/**
 * Tokenizer for Moonshine's SentencePiece-style BPE vocabulary, read from a Hugging
 * Face {@code tokenizer.json}: {@code model.vocab} (token -> id) plus {@code added_tokens}.
 * Decoding skips special tokens, turns {@code <0xNN>} byte-fallback tokens into raw bytes
 * (interpreted as UTF-8), maps {@code ▁} to a space and trims the result.
 * Encoding mirrors Hugging Face {@code tokenizers}: prepend {@code ▁}, spaces to {@code ▁}, then
 * BPE over the whole string by merge rank, with {@code <0xNN>} byte fallback and fused unks.
 */
public final class MoonshineTokenizer {

    private static final char WORD_START = '▁';

    private static final int MAX_CACHE = 20000;

    private final String[] tokens;
    private final boolean[] special;
    private final HashMap<String, Integer> vocab;
    /** (leftId << 32 | rightId) -> merge rank. */
    private final HashMap<Long, Integer> mergeRank;
    /** merge rank -> id of the merged piece. */
    private final int[] mergeResult;
    private final int unkId;
    private final int[] byteIds = new int[256];
    private final HashMap<String, int[]> cache = new HashMap<>();

    private MoonshineTokenizer(String[] tokens, boolean[] special, HashMap<String, Integer> vocab,
                               HashMap<Long, Integer> mergeRank, int[] mergeResult, int unkId) {
        this.tokens = tokens;
        this.special = special;
        this.vocab = vocab;
        this.mergeRank = mergeRank;
        this.mergeResult = mergeResult;
        this.unkId = unkId;
        for (int b = 0; b < 256; b++) {
            Integer id = vocab.get(String.format("<0x%02X>", b));
            byteIds[b] = id == null ? -1 : id;
        }
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

            HashMap<String, Integer> vocabMap = new HashMap<>(vocab.length() * 2);
            for (Iterator<String> it = vocab.keys(); it.hasNext(); ) {
                String tok = it.next();
                vocabMap.put(tok, vocab.getInt(tok));
            }
            JSONObject model = root.getJSONObject("model");
            JSONArray merges = model.optJSONArray("merges");
            int mc = merges == null ? 0 : merges.length();
            HashMap<Long, Integer> rank = new HashMap<>(mc * 2);
            int[] result = new int[mc];
            int rk = 0;
            for (int i = 0; i < mc; i++) {
                Object m = merges.get(i);
                String a, b;
                if (m instanceof JSONArray) {
                    a = ((JSONArray) m).getString(0);
                    b = ((JSONArray) m).getString(1);
                } else {
                    String ms = m.toString();
                    int sp = ms.indexOf(' ');
                    if (sp < 0) continue;
                    a = ms.substring(0, sp);
                    b = ms.substring(sp + 1);
                }
                Integer ia = vocabMap.get(a), ib = vocabMap.get(b), ir = vocabMap.get(a + b);
                if (ia == null || ib == null || ir == null) continue;
                Long key = ((long) ia << 32) | ib;
                if (rank.containsKey(key)) continue;
                rank.put(key, rk);
                result[rk++] = ir;
            }
            String unk = model.optString("unk_token", "<unk>");
            Integer unkId = vocabMap.get(unk);
            return new MoonshineTokenizer(tokens, special, vocabMap, rank,
                    java.util.Arrays.copyOf(result, rk), unkId == null ? -1 : unkId);
        } catch (JSONException e) {
            throw new IOException("bad tokenizer.json", e);
        }
    }

    /** True when the token's piece starts with the word-start marker {@code ▁}. */
    public boolean isWordStart(int tokenId) {
        return tokenId >= 0 && tokenId < tokens.length && tokens[tokenId] != null
                && !tokens[tokenId].isEmpty() && tokens[tokenId].charAt(0) == WORD_START;
    }

    /** Text to token ids like Hugging Face {@code encode(text, add_special_tokens=False)}. */
    public int[] encode(String text) {
        if (text == null || text.isEmpty()) return new int[0];
        synchronized (cache) {
            int[] hit = cache.get(text);
            if (hit != null) return hit.clone();
        }
        int[] out = bpe(WORD_START + text.replace(' ', WORD_START));
        synchronized (cache) {
            if (cache.size() >= MAX_CACHE) cache.clear();
            cache.put(text, out);
        }
        return out.clone();
    }

    private int[] bpe(String word) {
        int n = word.length();
        int[] ids = new int[n * 4];
        int len = 0;
        for (int i = 0; i < n; ) {
            int cp = word.codePointAt(i);
            int w = Character.charCount(cp);
            Integer id = vocab.get(word.substring(i, i + w));
            if (id != null) {
                ids[len++] = id;
            } else {
                byte[] bytes = word.substring(i, i + w).getBytes(StandardCharsets.UTF_8);
                boolean ok = true;
                for (byte b : bytes) if (byteIds[b & 0xFF] < 0) ok = false;
                if (ok) {
                    for (byte b : bytes) ids[len++] = byteIds[b & 0xFF];
                } else {
                    ids[len++] = unkId;
                }
            }
            i += w;
        }
        int[] next = new int[len];
        int[] prev = new int[len];
        for (int i = 0; i < len; i++) { next[i] = i + 1 < len ? i + 1 : -1; prev[i] = i - 1; }
        PriorityQueue<Long> pq = new PriorityQueue<>();
        for (int i = 0; i + 1 < len; i++) push(pq, ids, i, i + 1);
        while (!pq.isEmpty()) {
            long e = pq.poll();
            int r = (int) (e >>> 32), pos = (int) e;
            int nx = next[pos];
            if (ids[pos] < 0 || nx < 0 || ids[nx] < 0) continue;
            Integer cur = mergeRank.get(((long) ids[pos] << 32) | ids[nx]);
            if (cur == null || cur != r) continue;
            ids[pos] = mergeResult[r];
            ids[nx] = -1;
            int nn = next[nx];
            next[pos] = nn;
            if (nn >= 0) prev[nn] = pos;
            if (nn >= 0) push(pq, ids, pos, nn);
            int pv = prev[pos];
            if (pv >= 0) push(pq, ids, pv, pos);
        }
        int[] res = new int[len];
        int c = 0;
        for (int i = 0; i < len; i++) {
            if (ids[i] < 0) continue;
            if (ids[i] == unkId && c > 0 && res[c - 1] == unkId) continue; // fuse_unk
            res[c++] = ids[i];
        }
        return java.util.Arrays.copyOf(res, c);
    }

    private void push(PriorityQueue<Long> pq, int[] ids, int a, int b) {
        Integer r = mergeRank.get(((long) ids[a] << 32) | ids[b]);
        if (r != null) pq.add(((long) r << 32) | a);
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
