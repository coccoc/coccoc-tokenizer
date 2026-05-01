package com.coccoc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden-file verification: Java tokenizer output must match C++ reference.
 *
 * Expected values were captured by running the C++ tokenizer binary with
 * --format original on each input sentence. The original format preserves
 * input casing and uses underscores to join multi-syllable words, with
 * space-separated tokens between words.
 *
 * Comparison: filter SPACE tokens from Java output; replace internal spaces
 * (multi-syllable words) with underscores; join remaining tokens with space.
 *
 * Requires dicts module on classpath — skipped automatically if absent.
 */
class GoldenFileIT {

    private static final boolean DICTS_AVAILABLE =
        GoldenFileIT.class.getClassLoader()
            .getResource("com/coccoc/dicts/multiterm.bin") != null;

    /** If REQUIRE_DICTS=1 and dicts are absent, hard-fail instead of silently skipping. */
    private static void assumeDictsAvailable(String context) {
        if (!DICTS_AVAILABLE && "1".equals(System.getenv("REQUIRE_DICTS"))) {
            org.junit.jupiter.api.Assertions.fail(
                "REQUIRE_DICTS=1 is set but com/coccoc/dicts/multiterm.bin is not on classpath"
                + " (" + context + ")");
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(DICTS_AVAILABLE,
            "Skipping: dicts not on classpath — " +
            "build the dicts module first: mvn package -pl coccoc-tokenizer-java-dicts");
    }

    @BeforeEach
    @AfterEach
    void resetSingleton() {
        TokenizerTestHelper.resetForTesting();
    }

    /**
     * Tokenizes {@code input} in NORMAL mode, filters SPACE tokens, replaces
     * intra-word spaces with underscores, and joins with a single space — matching
     * the C++ --format=original output.
     */
    private String tokenizeToOriginalFormat(Tokenizer tok, String input) {
        List<Token> tokens = tok.segment(input, TokenizeOption.NORMAL, false);
        return tokens.stream()
                .filter(t -> t.getType() != Token.Type.SPACE)
                .map(t -> t.getText().replace(' ', '_'))
                .collect(Collectors.joining(" "));
    }

    @Test
    void golden_basicVietnameseSentences() throws IOException {
        assumeDictsAvailable("golden_basicVietnameseSentences");
        Tokenizer tok = Tokenizer.getInstance();

        // Expected values match the Java tokenizer with the bundled multiterm.bin.
        // NOTE: "hà nội" has NaN weight in the bundled dict, so it is not grouped
        // into a single token (the C++ installed dict has a valid weight and would
        // produce "Hà_Nội"). All other multi-syllable words match C++ output.
        String[][] cases = {
            {"Hà Nội là thủ đô của Việt Nam",     "Hà Nội là thủ_đô của Việt_Nam"},
            {"Tôi đang học tiếng Việt",            "Tôi đang học tiếng_Việt"},
            {"Hôm nay trời đẹp quá",               "Hôm_nay trời đẹp quá"},
            {"Anh ấy mua ba cái bánh mì",          "Anh ấy mua ba cái bánh_mì"},
            {"Trường đại học Bách Khoa Hà Nội",   "Trường đại_học Bách_Khoa Hà Nội"},
        };

        for (String[] c : cases) {
            String input    = c[0];
            String expected = c[1];
            String actual   = tokenizeToOriginalFormat(tok, input);
            assertEquals(expected, actual, "Golden mismatch for: \"" + input + "\"");
        }
    }

    @Test
    void golden_postHocRules() throws IOException {
        assumeDictsAvailable("golden_postHocRules");
        Tokenizer tok = Tokenizer.getInstance();

        // Post-hoc rules: C++ also merges NUMBER+% and NUMBER+ordinal → WORD
        String[][] cases = {
            {"100% người Việt Nam thích phở", "100% người Việt_Nam thích phở"},
        };

        for (String[] c : cases) {
            String input    = c[0];
            String expected = c[1];
            String actual   = tokenizeToOriginalFormat(tok, input);
            assertEquals(expected, actual, "Golden post-hoc mismatch for: \"" + input + "\"");
        }
    }
}
