package com.coccoc;

import java.lang.reflect.Field;

/**
 * Test-only utility to reset the Tokenizer singleton between test cases.
 * Uses reflection to avoid putting test infrastructure in production code.
 */
public final class TokenizerTestHelper {
    private TokenizerTestHelper() {}

    public static synchronized void resetForTesting() {
        try {
            Field inst = Tokenizer.class.getDeclaredField("instance");
            Field path = Tokenizer.class.getDeclaredField("initializedDictPath");
            inst.setAccessible(true);
            path.setAccessible(true);
            inst.set(null, null);
            path.set(null, null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("resetForTesting failed", e);
        }
    }
}
