package com.psthetech.swara.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.psthetech.swara.domain.model.Song;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Decides which artist credits are the same person ("Anirudh" vs "Anirudh Ravichander").
 * View-level only: nothing in the library is rewritten.
 *
 * Automatic rule: S merges into L only if S's tokens are a strict prefix of L's tokens
 * (initials-aware), L is the only (nearest) artist S prefixes, and they share an album.
 * Credits whose tokens are identical (e.g. "A.R. Rahman" / "AR Rahman") always form one group.
 * Overrides win: a null value means "never merge", a value is the key to merge into.
 */
public final class ArtistAliasResolver {

    private static final int MAX_HOPS = 8;
    private static final ArtistAliasResolver IDENTITY =
            new ArtistAliasResolver(Collections.emptyMap(), Collections.emptyMap());

    private final Map<String, String> merges;      // canonical key -> target key (single hop)
    private final Map<String, String> displayByRoot;
    private final Map<String, String> autoSnapshot;
    private final Map<String, String> creditDisplay;

    private ArtistAliasResolver(Map<String, String> merges, Map<String, String> displayByRoot) {
        this(merges, displayByRoot, Collections.emptyMap(), Collections.emptyMap());
    }

    private ArtistAliasResolver(Map<String, String> merges, Map<String, String> displayByRoot,
                                Map<String, String> autoSnapshot,
                                Map<String, String> creditDisplay) {
        this.autoSnapshot = autoSnapshot;
        this.creditDisplay = creditDisplay;
        this.merges = merges;
        this.displayByRoot = displayByRoot;
    }

    @NonNull
    public static ArtistAliasResolver identity() {
        return IDENTITY;
    }

    /** One identity group: all canonical keys sharing a tokensKey. */
    private static final class Group {
        final List<String> tokens;
        boolean initialsOnly;
        final Map<String, Integer> keyCounts = new LinkedHashMap<>();
        final Map<String, String> displayByKey = new HashMap<>();
        final Set<Long> albums = new HashSet<>();
        String rep;
        Group(List<String> tokens) {
            this.tokens = tokens;
        }
    }

    @NonNull
    public static ArtistAliasResolver build(@Nullable List<Song> songs,
                                            @Nullable Map<String, String> overrides) {
        Map<String, Group> groups = new LinkedHashMap<>();   // tokensKey -> group
        Map<String, String> displayByKey = new HashMap<>();
        Map<String, String> groupOfKey = new HashMap<>();

        if (songs != null) {
            for (Song s : songs) {
                if (s == null) continue;
                for (String name : ArtistIdentityHelper.extractArtists(s.getArtist())) {
                    String key = ArtistIdentityHelper.getCanonicalKey(name);
                    String tk = tokensKey(name);
                    if (tk.isEmpty()) tk = key;
                    Group g = groups.get(tk);
                    if (g == null) {
                        g = new Group(tokenList(name));
                        groups.put(tk, g);
                    }
                    // Order-independent: any initials-only credit ("A.R.", "AR") taints the group.
                    if (ArtistNameTokens.isInitialsOnly(name)) g.initialsOnly = true;
                    g.keyCounts.merge(key, 1, Integer::sum);
                    if (s.getAlbumId() > 0 && !MetadataNormalization.isMissingAlbum(s.getAlbum())) {
                        g.albums.add(s.getAlbumId());
                    }
                    groupOfKey.put(key, tk);
                    String cur = displayByKey.get(key);
                    if (cur == null || better(name, cur)) displayByKey.put(key, name);
                }
            }
        }

        // Representative key per group: most credited, then shortest, then alphabetical.
        for (Group g : groups.values()) {
            String best = null;
            for (Map.Entry<String, Integer> e : g.keyCounts.entrySet()) {
                if (best == null) { best = e.getKey(); continue; }
                int c = Integer.compare(e.getValue(), g.keyCounts.get(best));
                if (c > 0 || (c == 0 && e.getKey().compareTo(best) < 0)) best = e.getKey();
            }
            g.rep = best;
        }

        // Index groups by first token to avoid all-pairs comparison.
        Map<String, List<Group>> byFirst = new HashMap<>();
        for (Group g : groups.values()) {
            if (g.tokens.isEmpty()) continue;
            byFirst.computeIfAbsent(g.tokens.get(0), k -> new ArrayList<>()).add(g);
        }

        Map<String, String> merges = new HashMap<>();
        // Variants of one identity merge into the representative.
        for (Group g : groups.values()) {
            for (String k : g.keyCounts.keySet()) {
                if (!k.equals(g.rep)) merges.put(k, g.rep);
            }
        }
        // Strict-prefix merges between groups.
        for (Group s : groups.values()) {
            if (s.initialsOnly || s.tokens.isEmpty()) continue;
            List<Group> cands = new ArrayList<>();
            for (Group l : byFirst.getOrDefault(s.tokens.get(0), Collections.emptyList())) {
                if (l != s && l.tokens.size() > s.tokens.size() && isPrefix(s.tokens, l.tokens)) {
                    cands.add(l);
                }
            }
            if (cands.isEmpty()) continue;
            // Keep only the nearest links of a chain (drop candidates that extend another candidate).
            List<Group> nearest = new ArrayList<>();
            for (Group c : cands) {
                boolean extendsOther = false;
                for (Group o : cands) {
                    if (o != c && o.tokens.size() < c.tokens.size() && isPrefix(o.tokens, c.tokens)) {
                        extendsOther = true;
                        break;
                    }
                }
                if (!extendsOther) nearest.add(c);
            }
            if (nearest.size() != 1) continue;
            Group l = nearest.get(0);
            if (Collections.disjoint(s.albums, l.albums)) continue;
            for (String k : s.keyCounts.keySet()) merges.put(k, l.rep);
        }
        Map<String, String> auto = new HashMap<>(merges);

        // Overrides always win; null means never merge.
        if (overrides != null) {
            for (Map.Entry<String, String> e : overrides.entrySet()) {
                if (e.getValue() == null) merges.remove(e.getKey());
                else if (!e.getValue().equals(e.getKey())) merges.put(e.getKey(), e.getValue());
            }
        }

        ArtistAliasResolver tmp = new ArtistAliasResolver(merges, Collections.emptyMap());
        Map<String, String> display = new HashMap<>();
        for (Map.Entry<String, String> e : displayByKey.entrySet()) {
            String root = tmp.resolve(e.getKey());
            String cur = display.get(root);
            if (cur == null || better(e.getValue(), cur)) display.put(root, e.getValue());
        }
        return new ArtistAliasResolver(merges, display,
                Collections.unmodifiableMap(filterAuto(auto, overrides)),
                Collections.unmodifiableMap(new HashMap<>(displayByKey)));
    }

    private static Map<String, String> filterAuto(Map<String, String> auto, Map<String, String> overrides) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : auto.entrySet()) {
            if (overrides != null && overrides.containsKey(e.getKey())) continue;
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    /** Follows merges to the longest identity; cycles or runaway chains keep the original key. */
    @NonNull
    public String resolve(@NonNull String canonicalKey) {
        String cur = canonicalKey;
        for (int i = 0; i < MAX_HOPS; i++) {
            String next = merges.get(cur);
            if (next == null) return cur;
            cur = next;
        }
        return canonicalKey;
    }

    /** Automatic (non-override) merges, source key to direct target key. */
    @NonNull
    public Map<String, String> autoMerges() {
        return autoSnapshot;
    }

    /** Credit keys (other than mergedKey) that currently resolve into mergedKey, auto or manual. */
    @NonNull
    public List<String> keysMergedInto(@NonNull String mergedKey) {
        List<String> out = new ArrayList<>();
        for (String k : merges.keySet()) {
            if (!k.equals(mergedKey) && resolve(k).equals(mergedKey)) out.add(k);
        }
        Collections.sort(out);
        return out;
    }

    /** Display spelling of one credit key (not its merged root); falls back to the key. */
    @NonNull
    public String creditDisplay(@NonNull String creditKey) {
        String d = creditDisplay.get(creditKey);
        return d != null ? d : creditKey;
    }

    /** Longest display name among the credits that resolve to this key. */
    @NonNull
    public String displayName(@NonNull String mergedKey) {
        String d = displayByRoot.get(mergedKey);
        return d != null ? d : mergedKey;
    }

    /**
     * Initials-aware tokens joined by a single space. Runs of initials collapse into one token
     * ("A.R." / "A. R." / "AR" -> "ar"), so "A.R. Rahman" and "AR Rahman" give "ar rahman".
     */
    @NonNull
    public static String tokensKey(@Nullable String name) {
        return String.join(" ", ArtistNameTokens.tokenList(name));
    }

    private static List<String> tokenList(@Nullable String name) {
        return ArtistNameTokens.tokenList(name);
    }

    private static boolean isPrefix(List<String> s, List<String> l) {
        for (int i = 0; i < s.size(); i++) {
            if (!s.get(i).equals(l.get(i))) return false;
        }
        return true;
    }

    private static boolean better(String cand, String cur) {
        if (cand.length() != cur.length()) return cand.length() > cur.length();
        return cand.compareTo(cur) < 0;
    }
}
