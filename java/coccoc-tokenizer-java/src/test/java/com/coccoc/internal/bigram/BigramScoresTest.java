package com.coccoc.internal.bigram;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BigramScoresTest {

    // Fixture: 3-row sparse matrix
    //   row 0: col 1 -> 3.0f
    //   row 1: col 0 -> 1.5f, col 2 -> 2.5f
    //   row 2: (empty)
    private static final int[]   ROW_OFFSET = {0, 1, 3, 3};
    private static final int[]   COL_INDEX  = {1, 0, 2};
    private static final float[] VALUE      = {3.0f, 1.5f, 2.5f};

    private final BigramScores scores = new BigramScores(ROW_OFFSET, COL_INDEX, VALUE);

    @Test
    void getScore_existingPair_returnsValue() {
        assertEquals(3.0f, scores.getScore(0, 1), 1e-6f);
    }

    @Test
    void getScore_missingPair_returnsDefault() {
        assertEquals(BigramScores.DEFAULT_SCORE, scores.getScore(0, 0), 1e-6f);
    }

    @Test
    void getScore_secondRow_bothPairsFound() {
        assertEquals(1.5f, scores.getScore(1, 0), 1e-6f);
        assertEquals(2.5f, scores.getScore(1, 2), 1e-6f);
    }

    @Test
    void getScore_emptyRow_returnsDefault() {
        assertEquals(BigramScores.DEFAULT_SCORE, scores.getScore(2, 0), 1e-6f);
    }

    @Test
    void getScore_negativeRowIndex_throwsArrayIndexOutOfBounds() {
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> scores.getScore(-1, 0));
    }

    @Test
    void defaultScore_isZero() {
        // C++ contributes 0 for absent pairs (additive bonus model, not penalty)
        assertEquals(0.0f, BigramScores.DEFAULT_SCORE, 1e-6f);
    }
}
