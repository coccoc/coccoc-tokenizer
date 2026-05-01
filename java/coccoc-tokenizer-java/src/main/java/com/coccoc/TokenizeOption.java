package com.coccoc;

/**
 * Tokenization mode — ordinals must match tokenizer/tokenizer.hpp lines 20-22.
 *
 * NORMAL(0): standard word segmentation
 * HOST(1):   dot-split hostname tokenization
 * URL(2):    full URL tokenization (sticky syllable + host)
 */
public enum TokenizeOption {
    NORMAL(0),
    HOST(1),
    URL(2);

    private final int value;

    TokenizeOption(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
