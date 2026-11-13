package com.psthetech.swara.voice.match;

import androidx.annotation.NonNull;

import com.psthetech.swara.voice.intent.VoiceIntent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Finds the best song / artist / album for a play intent and grades its confidence. */
public final class LibraryVoiceMatcher {

    static final double HIGH_MIN = 0.85;
    static final double LEAD_MIN = 0.10;
    static final double RANK_MIN = 0.60;
    static final double CATEGORY_BONUS = 0.10;
    static final int MAX_RANKED = 4;
    static final double EXACT_TITLE_BONUS = 0.01;
    static final int JOINED_MIN_LEN = 5;
    private static final double EPS = 1e-9;

    private LibraryVoiceMatcher() {}

    @NonNull
    public static MatchResult match(VoiceIntent intent, LibraryIndex index) {
        if (intent == null || index == null) return MatchResult.none();
        VoiceIntent.Type type = intent.getType();
        if (type != VoiceIntent.Type.PLAY_ANY && type != VoiceIntent.Type.PLAY_ARTIST
                && type != VoiceIntent.Type.PLAY_ALBUM) {
            return MatchResult.none();
        }
        String qKey = PhoneticKey.of(intent.getQuery());
        if (qKey.isEmpty()) return MatchResult.none();
        String[] qTokens = qKey.split(" ");
        String rawNorm = normalizeRaw(intent.getQuery());

        List<Candidate> scored;
        if (type == VoiceIntent.Type.PLAY_ARTIST) {
            // Artist first; only fall back to albums when no artist is a plausible match.
            scored = rank(index, qKey, qTokens, rawNorm, MatchKind.ARTIST, true);
            if (intent.isArtistThenAlbum() && (scored.isEmpty() || scored.get(0).score < RANK_MIN)) {
                scored = rank(index, qKey, qTokens, rawNorm, MatchKind.ALBUM, true);
            }
        } else if (type == VoiceIntent.Type.PLAY_ALBUM) {
            scored = rank(index, qKey, qTokens, rawNorm, MatchKind.ALBUM, true);
        } else {
            scored = rank(index, qKey, qTokens, rawNorm, null, false);
        }
        if (scored.isEmpty() || scored.get(0).score < RANK_MIN) return MatchResult.none();

        double top = scored.get(0).score;
        double second = scored.size() > 1 ? scored.get(1).score : 0.0;
        Confidence c;
        if (top >= HIGH_MIN) {
            // Exact-title bonus may break a near-tie only when no other candidate is also an exact match.
            boolean exactWin = top > 1.0 + EPS && second <= 1.0 + EPS && top - second >= EXACT_TITLE_BONUS - EPS;
            c = (exactWin || top - second >= LEAD_MIN - EPS) ? Confidence.HIGH : Confidence.AMBIGUOUS;
        } else {
            c = Confidence.MEDIUM;
        }
        List<Candidate> ranked = new ArrayList<>();
        for (Candidate cand : scored) {
            if (cand.score < RANK_MIN || ranked.size() >= MAX_RANKED) break;
            ranked.add(cand);
        }
        return new MatchResult(c, ranked);
    }

    private static String normalizeRaw(String q) {
        return q == null ? "" : q.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static boolean firstLetterMatches(String qKey, LibraryIndex.Entry e) {
        String qj = PhoneticKey.joined(qKey);
        if (qj.isEmpty()) return false;
        if (startsSame(qj, String.join(" ", e.tokens))) return true;
        for (List<String> vt : e.variantTokens) {
            if (startsSame(qj, String.join(" ", vt))) return true;
        }
        return false;
    }

    private static boolean startsSame(String qj, String key) {
        String cj = PhoneticKey.joined(key);
        return !cj.isEmpty() && cj.charAt(0) == qj.charAt(0);
    }

    private static List<Candidate> rank(LibraryIndex index, String qKey, String[] qTokens, String rawNorm,
                                         MatchKind only, boolean bonus) {
        List<Candidate> scored = new ArrayList<>();
        for (LibraryIndex.Entry e : index.entries()) {
            if (only != null && e.kind != only) continue;
            double score = score(qKey, qTokens, e);
            if (score <= 0) continue;
            if (bonus) score = Math.min(1.0, score + CATEGORY_BONUS);
            if (score < HIGH_MIN && !firstLetterMatches(qKey, e)) score = Math.min(score, RANK_MIN - 0.01);
            if (!rawNorm.isEmpty() && rawNorm.equals(normalizeRaw(e.compareName))) score += EXACT_TITLE_BONUS;
            scored.add(new Candidate(e.kind, e.key, e.display, e.detail, score, e.songs));
        }
        Collections.sort(scored, (a, b) -> {
            if (Math.abs(a.score - b.score) > EPS) return Double.compare(b.score, a.score);
            return Integer.compare(tieOrder(a.kind), tieOrder(b.kind));
        });
        return scored;
    }

    /** SONG > ALBUM > ARTIST, used only on equal scores (artist first for explicit artist intents is moot: one kind). */
    private static int tieOrder(MatchKind k) {
        switch (k) {
            case SONG: return 0;
            case ALBUM: return 1;
            default: return 2;
        }
    }

    private static double score(String qKey, String[] qTokens, LibraryIndex.Entry e) {
        double best = scoreTokens(qKey, qTokens, e, e.tokens);
        for (List<String> vt : e.variantTokens) {
            best = Math.max(best, scoreTokens(qKey, qTokens, e, vt));
        }
        return best;
    }

    private static double scoreTokens(String qKey, String[] qTokens, LibraryIndex.Entry e,
                                      List<String> tokens) {
        String fullKey = String.join(" ", tokens);
        if (fullKey.isEmpty()) return 0;
        if (e.kind == MatchKind.ARTIST) {
            // Query-side initials: drop single letters ("a r") and the entry's own initials.
            List<String> kept = new ArrayList<>();
            for (String q : qTokens) {
                if (q.length() == 1) continue;
                if (!e.initials.contains(q)) kept.add(q);
            }
            if (kept.isEmpty()) return 0;
            qTokens = kept.toArray(new String[0]);
            qKey = String.join(" ", kept);
        }
        double sim = similarity(qKey, fullKey);
        double best = Math.max(sim, coverage(qTokens, tokens));
        String qj = PhoneticKey.joined(qKey), cj = PhoneticKey.joined(fullKey);
        if (qTokens.length != tokens.size() && qj.length() >= JOINED_MIN_LEN && cj.length() >= JOINED_MIN_LEN) {
            best = Math.max(best, similarity(qj, cj));
        }
        return best;
    }

    private static double coverage(String[] qTokens, List<String> cTokens) {
        if (cTokens.isEmpty()) return 0;
        for (String q : qTokens) {
            boolean used = false;
            for (String c : cTokens) {
                if (lev(q, c) <= 1) { used = true; break; }
            }
            if (!used) return 0;
        }
        int covered = 0;
        for (String c : cTokens) {
            for (String q : qTokens) {
                if (lev(q, c) <= 1) { covered++; break; }
            }
        }
        return (double) covered / cTokens.size();
    }

    private static double similarity(String a, String b) {
        int max = Math.max(a.length(), b.length());
        if (max == 0) return 0;
        return 1.0 - (double) lev(a, b) / max;
    }

    public static int levenshtein(String a, String b) { return lev(a, b); }

    static int lev(String a, String b) {
        int n = a.length(), m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] prev = new int[m + 1], cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[m];
    }
}
