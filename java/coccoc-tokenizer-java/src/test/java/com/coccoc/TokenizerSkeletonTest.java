package com.coccoc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * M1 skeleton verification: public API classes exist and signal "not implemented".
 * Tests turn green as real implementation replaces each stub body.
 */
class TokenizerSkeletonTest {

    @BeforeEach
    void resetSingleton() {
        TokenizerTestHelper.resetForTesting();
    }

    @Test
    void tokenizeOptionHasThreeValues() {
        assertEquals(3, TokenizeOption.values().length);
        assertEquals(TokenizeOption.NORMAL, TokenizeOption.values()[0]);
        assertEquals(TokenizeOption.HOST, TokenizeOption.values()[1]);
        assertEquals(TokenizeOption.URL, TokenizeOption.values()[2]);
    }

    @Test
    void tokenTypeHasSixValues() {
        assertEquals(6, Token.Type.values().length);
    }

    @Test
    void tokenSegTypeHasFiveValues() {
        assertEquals(5, Token.SegType.values().length);
    }

    @Test
    void getInstance_throwsWhenDictDirectoryMissing() {
        assertThrows(java.io.IOException.class,
                () -> Tokenizer.getInstance("/no/such/path/to/dicts"));
    }
}
