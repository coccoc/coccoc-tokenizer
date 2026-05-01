package com.coccoc.internal.segment;

import com.coccoc.Token;
import com.coccoc.internal.build.TriePacker;
import com.coccoc.internal.lang.VnLangTool;
import com.coccoc.internal.trie.MultitermTrie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SegmenterTest {

    @BeforeAll
    static void init() {
        VnLangTool.initSimple();
    }

    private static MultitermTrie simpleTrie() {
        TriePacker.HashNode root = TriePacker.buildHashTrie(new String[]{"a"});
        return TriePacker.pack(root);
    }

    // Slice 1 — empty input edge case
    @Test
    void segment_emptyString_returnsEmpty() {
        Segmenter seg = new Segmenter(simpleTrie());
        assertTrue(seg.segment("").isEmpty());
    }

    // Slice 2 — ASCII word via single-char fallback merge
    @Test
    void segment_singleAsciiWord_returnsWordToken() {
        Segmenter seg = new Segmenter(simpleTrie());
        List<Token> tokens = seg.segment("hello");
        assertEquals(1, tokens.size());
        assertEquals("hello", tokens.get(0).getText());
        assertEquals(Token.Type.WORD, tokens.get(0).getType());
    }

    // Slice 3 — space as explicit SPACE token between two words
    @Test
    void segment_twoWordsWithSpace_returnsWordSpaceWord() {
        Segmenter seg = new Segmenter(simpleTrie());
        List<Token> tokens = seg.segment("a b");
        assertEquals(3, tokens.size());
        assertEquals("a",  tokens.get(0).getText());
        assertEquals(Token.Type.WORD,  tokens.get(0).getType());
        assertEquals(" ",  tokens.get(1).getText());
        assertEquals(Token.Type.SPACE, tokens.get(1).getType());
        assertEquals("b",  tokens.get(2).getText());
        assertEquals(Token.Type.WORD,  tokens.get(2).getType());
    }

    // Slice 4 — punctuation character after a word
    @Test
    void segment_punctuation_returnsPunctToken() {
        Segmenter seg = new Segmenter(simpleTrie());
        List<Token> tokens = seg.segment("hello!");
        assertEquals(2, tokens.size());
        assertEquals("hello", tokens.get(0).getText());
        assertEquals(Token.Type.WORD,  tokens.get(0).getType());
        assertEquals("!",     tokens.get(1).getText());
        assertEquals(Token.Type.PUNCT, tokens.get(1).getType());
    }

    // Slice 5 — digit sequence classified as NUMBER
    @Test
    void segment_number_returnsNumberToken() {
        Segmenter seg = new Segmenter(simpleTrie());
        List<Token> tokens = seg.segment("42");
        assertEquals(1, tokens.size());
        assertEquals("42",       tokens.get(0).getText());
        assertEquals(Token.Type.NUMBER, tokens.get(0).getType());
    }

    // Slice 6 — known trie entry returned as one token (exercises trie path, not fallback)
    @Test
    void segment_knownDictWord_returnsSingleToken() {
        TriePacker.HashNode root = TriePacker.buildHashTrie(new String[]{"hello"});
        MultitermTrie trie = TriePacker.pack(root);
        Segmenter seg = new Segmenter(trie);
        List<Token> tokens = seg.segment("hello world");
        assertEquals(3, tokens.size());
        assertEquals("hello", tokens.get(0).getText());
        assertEquals(Token.Type.WORD,  tokens.get(0).getType());
        assertEquals(Token.Type.SPACE, tokens.get(1).getType());
        assertEquals("world", tokens.get(2).getText());
        assertEquals(Token.Type.WORD,  tokens.get(2).getType());
    }

    // M7c — multi-syllable Vietnamese: space (0x20) is a valid trie edge
    @Test
    void segment_multiSyllableVietnamese_spaceEdge_returnsSingleToken() {
        // Build a trie with "xin chao" as one entry (ASCII approximation avoids NFD issues)
        TriePacker.HashNode root = TriePacker.buildHashTrie(new String[]{"xin chao"});
        MultitermTrie trie = TriePacker.pack(root);
        Segmenter seg = new Segmenter(trie);
        List<Token> tokens = seg.segment("xin chao");
        // space-edge trie match should return one WORD, not [WORD, SPACE, WORD]
        assertEquals(1, tokens.size());
        assertEquals("xin chao", tokens.get(0).getText());
        assertEquals(Token.Type.WORD, tokens.get(0).getType());
    }

    // M7d — sticky syllable segmentation via SyllableTrie Viterbi
    @Test
    void splitSyllables_knownStickyPhrase_returnsCorrectSyllables() {
        com.coccoc.internal.trie.SyllableTrie sylTrie =
            com.coccoc.tools.DictCompileTestSupport.buildSyllableTrie("xin", "chao");
        // Empty bigram scores (2 rows, no entries)
        com.coccoc.internal.bigram.BigramScores noScores =
            new com.coccoc.internal.bigram.BigramScores(
                new int[]{0, 0, 0}, new int[]{}, new float[]{});

        TriePacker.HashNode root = TriePacker.buildHashTrie(new String[0]);
        MultitermTrie emptyMt = TriePacker.pack(root);
        Segmenter seg = new Segmenter(emptyMt, sylTrie, noScores);

        java.util.List<String> syllables = seg.splitSyllables("xinchao");
        assertEquals(2, syllables.size());
        assertEquals("xin",  syllables.get(0));
        assertEquals("chao", syllables.get(1));
    }

    // M7e — HOST mode: split on dots, each label is a WORD token
    @Test
    void segment_hostMode_splitsOnDots() {
        Segmenter seg = new Segmenter(simpleTrie());
        List<Token> tokens = seg.segment("www.google.com",
                com.coccoc.TokenizeOption.HOST, false);
        assertEquals(3, tokens.size());
        assertTrue(tokens.stream().allMatch(t -> t.getType() == Token.Type.WORD));
        assertEquals("www",    tokens.get(0).getText());
        assertEquals("google", tokens.get(1).getText());
        assertEquals("com",    tokens.get(2).getText());
    }

    // M7e — URL mode: strip scheme, each alphanumeric segment is a WORD token
    @Test
    void segment_urlMode_stripsSchemeAndSegments() {
        Segmenter seg = new Segmenter(simpleTrie());
        List<Token> tokens = seg.segment("https://example.com/path",
                com.coccoc.TokenizeOption.URL, false);
        // expect word tokens: example, com, path (separators omitted or PUNCT)
        long wordCount = tokens.stream()
                .filter(t -> t.getType() == Token.Type.WORD).count();
        assertTrue(wordCount >= 3, "expected at least 3 WORD tokens, got " + wordCount);
        List<String> words = tokens.stream()
                .filter(t -> t.getType() == Token.Type.WORD)
                .map(Token::getText).collect(java.util.stream.Collectors.toList());
        assertTrue(words.contains("example"), "expected 'example' token");
        assertTrue(words.contains("com"),     "expected 'com' token");
        assertTrue(words.contains("path"),    "expected 'path' token");
    }

    // M7f — post-hoc: NUMBER + "%" PUNCT → WORD
    @Test
    void segment_numberPercent_returnsWordToken() {
        Segmenter seg = new Segmenter(simpleTrie());
        List<Token> tokens = seg.segment("100%");
        assertEquals(1, tokens.size(), "100% should be one token, got: " + tokens);
        assertEquals(Token.Type.WORD, tokens.get(0).getType());
        assertEquals("100%", tokens.get(0).getText());
    }

    // M7f — post-hoc: NUMBER + ordinal suffix → WORD
    @Test
    void segment_ordinalSuffix_returnsWordToken() {
        Segmenter seg = new Segmenter(simpleTrie());
        for (String ord : new String[]{"1st", "2nd", "3rd", "4th"}) {
            List<Token> tokens = seg.segment(ord);
            assertEquals(1, tokens.size(), ord + " should be one token, got: " + tokens);
            assertEquals(Token.Type.WORD, tokens.get(0).getType(), ord + " should be WORD");
            assertEquals(ord, tokens.get(0).getText());
        }
    }
    // M8a — keepPunct=false removes SPACE and PUNCT from NORMAL mode result
    @Test
    void segment_normalMode_keepPunctFalse_removesSpaceAndPunct() {
        Segmenter seg = new Segmenter(simpleTrie());
        List<Token> tokens = seg.segment("hello!", com.coccoc.TokenizeOption.NORMAL, false);
        assertTrue(tokens.stream().noneMatch(t ->
                t.getType() == Token.Type.SPACE || t.getType() == Token.Type.PUNCT),
            "keepPunct=false must remove SPACE and PUNCT; got: " + tokens);
        assertEquals(1, tokens.size());
        assertEquals("hello", tokens.get(0).getText());
    }

    // M8b — keepPunct=false removes SPACE between words
    @Test
    void segment_normalMode_keepPunctFalse_removesSpaceBetweenWords() {
        Segmenter seg = new Segmenter(simpleTrie());
        List<Token> tokens = seg.segment("a b", com.coccoc.TokenizeOption.NORMAL, false);
        assertEquals(2, tokens.size(), "keepPunct=false should leave only WORD tokens; got: " + tokens);
        assertEquals("a", tokens.get(0).getText());
        assertEquals("b", tokens.get(1).getText());
    }

    // M8c — keepPunct=true keeps PUNCT but removes SPACE
    @Test
    void segment_normalMode_keepPunctTrue_keepsPunctRemovesSpace() {
        Segmenter seg = new Segmenter(simpleTrie());
        List<Token> tokens = seg.segment("a b!", com.coccoc.TokenizeOption.NORMAL, true);
        assertTrue(tokens.stream().noneMatch(t -> t.getType() == Token.Type.SPACE),
            "keepPunct=true must still remove SPACE; got: " + tokens);
        long punctCount = tokens.stream().filter(t -> t.getType() == Token.Type.PUNCT).count();
        assertEquals(1, punctCount, "keepPunct=true must preserve PUNCT tokens; got: " + tokens);
        assertEquals(3, tokens.size(), "expected WORD('a'), WORD('b'), PUNCT('!'); got: " + tokens);
    }
    // M9 shouldGo regression — compound "viet" must beat sub-words "vi"+"et"
    // Without the shouldGo gate, position 2 is re-scanned: "et" (score=1.0) stacks on
    // "vi" (score=1.0) giving total 2.0 > 1.0 for "viet", producing 2 tokens.
    // With the gate, position 2 is interior and not re-scanned, so "viet" wins.
    @Test
    void segment_shouldGoGate_prefersCompoundOverSubwords() {
        TriePacker.HashNode root = TriePacker.buildHashTrie(new String[]{"vi", "et", "viet"});
        MultitermTrie trie = TriePacker.pack(root);
        Segmenter seg = new Segmenter(trie);
        List<Token> tokens = seg.segment("viet");
        assertEquals(1, tokens.size(),
            "shouldGo gate must prevent re-scan at pos 2; expected single token 'viet', got: " + tokens);
        assertEquals("viet", tokens.get(0).getText());
        assertEquals(Token.Type.WORD, tokens.get(0).getType());
    }

}
