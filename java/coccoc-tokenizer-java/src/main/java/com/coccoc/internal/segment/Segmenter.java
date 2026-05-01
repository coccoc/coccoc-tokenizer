package com.coccoc.internal.segment;

import com.coccoc.Token;
import com.coccoc.TokenizeOption;
import com.coccoc.internal.lang.VnLangTool;
import com.coccoc.internal.bigram.BigramScores;
import com.coccoc.internal.trie.MultitermTrie;
import com.coccoc.internal.trie.SyllableTrie;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure-Java Viterbi segmenter.
 * Forward DP over MultitermTrie; single-char fallback for trie misses;
 * consecutive same-type spans merged during traceback.
 */
public final class Segmenter {

    private static final Set<String> ORDINAL_SUFFIXES = new HashSet<>(Arrays.asList("st", "nd", "rd", "th"));

    private final MultitermTrie multitermTrie;
    private final SyllableTrie  syllableTrie;   // null if sticky segmentation not available
    private final BigramScores  bigramScores;   // null if not loaded

    public Segmenter(MultitermTrie multitermTrie) {
        this(multitermTrie, null, null);
    }

    public Segmenter(MultitermTrie multitermTrie, SyllableTrie syllableTrie,
                     BigramScores bigramScores) {
        this.multitermTrie = multitermTrie;
        this.syllableTrie  = syllableTrie;
        this.bigramScores  = bigramScores;
    }

    public List<Token> segment(String text, TokenizeOption option, boolean keepPunct) {
        if (option == TokenizeOption.HOST) return segmentHost(text.codePoints().toArray());
        if (option == TokenizeOption.URL)  return segmentUrl(text);
        List<Token> raw = segment(text);
        if (keepPunct) {
            return raw.stream()
                    .filter(t -> t.getType() != Token.Type.SPACE)
                    .collect(java.util.stream.Collectors.toList());
        }
        return raw.stream()
                .filter(t -> t.getType() != Token.Type.SPACE && t.getType() != Token.Type.PUNCT)
                .collect(java.util.stream.Collectors.toList());
    }

    // HOST mode: split on '.', return each non-empty label as WORD
    private List<Token> segmentHost(int[] cps) {
        List<Token> tokens = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= cps.length; i++) {
            if (i == cps.length || cps[i] == '.') {
                if (i > start) {
                    String part = new String(cps, start, i - start);
                    tokens.add(new Token(part, Token.Type.WORD, start, i));
                }
                start = i + 1;
            }
        }
        return tokens;
    }

    // URL mode: strip http(s):// scheme, then segment each alphanumeric run
    private List<Token> segmentUrl(String text) {
        // Strip well-known URL scheme prefixes
        String stripped = text;
        if (stripped.startsWith("https://")) stripped = stripped.substring(8);
        else if (stripped.startsWith("http://")) stripped = stripped.substring(7);

        int[] cps = stripped.codePoints().toArray();
        List<Token> tokens = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= cps.length; i++) {
            boolean isSep = (i == cps.length) || !VnLangTool.isAlphanumeric(cps[i]);
            if (isSep) {
                if (i > start) {
                    String part = new String(cps, start, i - start);
                    Token.Type type = classifySpan(cps, start, i);
                    tokens.add(new Token(part, type, start, i));
                }
                start = i + 1;
            }
        }
        return tokens;
    }

    public List<Token> segment(String text) {
        if (text.isEmpty()) return Collections.emptyList();

        int[] cps = VnLangTool.normalizeNfd(text.codePoints().toArray());
        int n = cps.length;

        float[] best = new float[n + 1];
        Arrays.fill(best, Float.NEGATIVE_INFINITY);
        best[0] = 0.0f;
        int[] trace = new int[n + 1];
        Arrays.fill(trace, -1);

        // shouldGo mirrors the C++ `should_go` flag: only scan from positions that are
        // explicit token-boundary anchors, not interior positions of an ongoing match.
        boolean[] shouldGo = new boolean[n + 1];
        shouldGo[0] = true;

        for (int i = 0; i < n; i++) {
            if (best[i] == Float.NEGATIVE_INFINITY) continue;

            if (shouldGo[i]) {
                // Trie scan: find all multi-char matches starting at i.
                // Track the furthest position that actually updated best[] so we
                // can set shouldGo only at that boundary, preventing interior
                // positions from being re-scanned (C++ should_go semantics).
                int node = 0;
                int furthestUpdated = -1;
                for (int j = i; j < n; j++) {
                    node = multitermTrie.findChild(node, VnLangTool.lower(cps[j]));
                    if (node == -1) break;
                    if (j > i && multitermTrie.isEnding(node)) {
                        float w = best[i] + multitermTrie.getWeight(node);
                        if (w > best[j + 1]) {
                            best[j + 1] = w;
                            trace[j + 1] = i;
                            furthestUpdated = j;
                        }
                    }
                }

                if (furthestUpdated >= 0) {
                    shouldGo[furthestUpdated + 1] = true;
                } else {
                    // No multi-char match: single-char step, scan may continue from i+1.
                    if (i + 1 <= n && best[i + 1] == Float.NEGATIVE_INFINITY) {
                        best[i + 1] = best[i];
                        trace[i + 1] = i;
                    }
                    shouldGo[i + 1] = true;
                }
            } else {
                // Interior position: carry score forward but do not scan trie.
                if (i + 1 <= n && best[i + 1] == Float.NEGATIVE_INFINITY) {
                    best[i + 1] = best[i];
                    trace[i + 1] = i;
                }
            }
        }

        // Traceback: collect (start, end) spans in reverse
        List<int[]> spans = new ArrayList<>();
        for (int pos = n; pos > 0; ) {
            int start = trace[pos];
            spans.add(new int[]{start, pos});
            pos = start;
        }
        Collections.reverse(spans);

        // Convert spans to tokens; merge consecutive same-type WORD/NUMBER spans
        List<Token> tokens = new ArrayList<>();
        for (int[] span : spans) {
            String spanText = new String(cps, span[0], span[1] - span[0]);
            Token.Type type = classifySpan(cps, span[0], span[1]);
            if (!tokens.isEmpty()) {
                Token last = tokens.get(tokens.size() - 1);
                if (canMerge(last.getType(), type)) {
                    tokens.set(tokens.size() - 1,
                            new Token(last.getText() + spanText, type, last.getPos(), span[1]));
                    continue;
                }
            }
            tokens.add(new Token(spanText, type, span[0], span[1]));
        }
        return applyPostHocRules(tokens);
    }

    /**
     * Splits a sticky (no-spaces) codepoint sequence into Vietnamese syllables.
     * Runs Viterbi DP on the SyllableTrie; single-char fallback for unknown chars.
     * Returns the list of syllable strings in order.
     */
    public List<String> splitSyllables(String text) {
        if (syllableTrie == null) return Collections.singletonList(text);
        int[] cps = VnLangTool.normalizeNfd(text.codePoints().toArray());
        int n = cps.length;
        if (n == 0) return Collections.emptyList();

        float[] best  = new float[n + 1];
        int[]   trace = new int[n + 1];
        int[]   sylAt = new int[n + 1]; // syllable index ending at each position (-1 if none)
        Arrays.fill(best, Float.NEGATIVE_INFINITY);
        Arrays.fill(trace, -1);
        Arrays.fill(sylAt, -1);
        best[0] = 0.0f;

        for (int i = 0; i < n; i++) {
            if (best[i] == Float.NEGATIVE_INFINITY) continue;

            int node = 0;
            for (int j = i; j < n; j++) {
                node = syllableTrie.findChild(node, cps[j]);
                if (node == -1) break;
                boolean isSyl = syllableTrie.getIndex(node) >= 0
                             || syllableTrie.getWeight(node) > 0.0f;
                if (isSyl) {
                    float w = best[i] + syllableTrie.getWeight(node);
                    int curIdx = syllableTrie.getIndex(node);
                    // Add bigram bonus when both syllable indices are known
                    if (bigramScores != null && sylAt[i] >= 0 && curIdx >= 0) {
                        w += bigramScores.getScore(sylAt[i], curIdx);
                    }
                    if (w > best[j + 1]) {
                        best[j + 1] = w;
                        trace[j + 1] = i;
                        sylAt[j + 1]  = curIdx;
                    }
                }
            }

            // Single-char fallback with heavy penalty
            if (i + 1 <= n && best[i + 1] == Float.NEGATIVE_INFINITY) {
                best[i + 1] = best[i] - 1_000.0f;
                trace[i + 1] = i;
                sylAt[i + 1]  = -1;
            }
        }

        // Traceback
        List<String> result = new ArrayList<>();
        for (int pos = n; pos > 0; ) {
            int start = trace[pos];
            result.add(new String(cps, start, pos - start));
            pos = start;
        }
        Collections.reverse(result);
        return result;
    }

    private Token.Type classifySpan(int[] cps, int from, int to) {
        if (to - from == 1 && cps[from] == ' ') return Token.Type.SPACE;
        boolean hasAlpha = false, hasNumeric = false;
        for (int i = from; i < to; i++) {
            if (VnLangTool.isAlphabetic(cps[i])) { hasAlpha = true; break; }
            if (VnLangTool.isNumeric(cps[i]))     hasNumeric = true;
        }
        if (hasAlpha)   return Token.Type.WORD;
        if (hasNumeric) return Token.Type.NUMBER;
        return Token.Type.PUNCT;
    }

    private boolean canMerge(Token.Type a, Token.Type b) {
        return a == b && (a == Token.Type.WORD || a == Token.Type.NUMBER);
    }

    // Post-hoc token merging rules applied after Viterbi traceback.
    private List<Token> applyPostHocRules(List<Token> tokens) {
        for (int i = tokens.size() - 2; i >= 0; i--) {
            Token cur  = tokens.get(i);
            Token next = tokens.get(i + 1);
            if (cur.getType() != Token.Type.NUMBER) continue;

            boolean mergePercent = next.getType() == Token.Type.PUNCT
                    && "%".equals(next.getText());
            boolean mergeOrdinal = next.getType() == Token.Type.WORD
                    && ORDINAL_SUFFIXES.contains(next.getText().toLowerCase());

            if (mergePercent || mergeOrdinal) {
                String merged = cur.getText() + next.getText();
                tokens.set(i, new Token(merged, Token.Type.WORD, cur.getPos(), next.getEndPos()));
                tokens.remove(i + 1);
            }
        }
        return tokens;
    }

}