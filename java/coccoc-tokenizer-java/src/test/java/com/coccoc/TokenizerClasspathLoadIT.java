package com.coccoc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Tokenizer.getInstance() no-arg classpath loading.
 *
 * These tests require coccoc-tokenizer-java-dicts resources on the classpath
 * (com/coccoc/dicts/multiterm.bin and syllable.bin). If the dicts JAR is not
 * present, all tests are skipped automatically via Assumptions.
 *
 * To run: build the dicts module first, then run via maven-failsafe-plugin:
 *   mvn package -pl coccoc-tokenizer-java-dicts
 *   mvn verify  -pl coccoc-tokenizer-java
 */
class TokenizerClasspathLoadIT {

    private static final boolean DICTS_AVAILABLE =
        TokenizerClasspathLoadIT.class.getClassLoader()
            .getResource("com/coccoc/dicts/multiterm.bin") != null;

    /** If REQUIRE_DICTS=1 and dicts are absent, hard-fail instead of silently skipping. */
    private static void assumeDictsAvailable(String context) {
        if (!DICTS_AVAILABLE && "1".equals(System.getenv("REQUIRE_DICTS"))) {
            org.junit.jupiter.api.Assertions.fail(
                "REQUIRE_DICTS=1 is set but com/coccoc/dicts/multiterm.bin is not on classpath"
                + " (" + context + ")");
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(DICTS_AVAILABLE,
            "Skipping: com/coccoc/dicts/multiterm.bin not on classpath — " +
            "build the dicts module first: mvn package -pl coccoc-tokenizer-java-dicts");
    }

    @BeforeEach
    @AfterEach
    void resetSingleton() {
        TokenizerTestHelper.resetForTesting();
    }

    @Test
    void getInstance_loadsMultitermAndSyllableFromClasspath() throws IOException {
        Assumptions.assumeTrue(
            Tokenizer.class.getClassLoader()
                .getResource("com/coccoc/dicts/multiterm.bin") != null,
            "Skipping: com/coccoc/dicts/multiterm.bin not on classpath — " +
            "build the dicts module first: mvn package -pl coccoc-tokenizer-java-dicts");

        Tokenizer t = Tokenizer.getInstance();
        assertNotNull(t, "getInstance() should return non-null when dicts are on classpath");
    }

    @Test
    void getInstance_returnsSameInstanceOnRepeatCall() throws IOException {
        assumeDictsAvailable("this test");

        Tokenizer first  = Tokenizer.getInstance();
        Tokenizer second = Tokenizer.getInstance();
        assertSame(first, second, "repeated no-arg getInstance() should return the cached instance");
    }

    @Test
    void getInstance_thenGetInstanceWithPathThrowsIllegalState() throws IOException {
        assumeDictsAvailable("this test");

        Tokenizer.getInstance(); // prime with classpath sentinel
        assertThrows(IllegalStateException.class,
            () -> Tokenizer.getInstance("/some/other/path"),
            "getInstance(path) after no-arg init should throw IllegalStateException");
    }

    @Test
    void getInstance_segmentReturnsTokens() throws IOException {
        assumeDictsAvailable("this test");

        Tokenizer t = Tokenizer.getInstance();
        // M7b: segment() is now implemented — must return non-empty token list
        assertFalse(t.segment("hello").isEmpty(),
                "segment() should return tokens (M7b implemented)");
    }
}
