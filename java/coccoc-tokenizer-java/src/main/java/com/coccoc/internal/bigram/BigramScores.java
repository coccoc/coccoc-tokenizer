package com.coccoc.internal.bigram;

import java.util.Arrays;

/**
 * CSR-format bigram frequency scores loaded from bigram.bin.
 * Populated by DictReader.
 */
public final class BigramScores {
    public static final float DEFAULT_SCORE = 0.0f;

    private final int[]   rowOffset;
    private final int[]   colIndex;
    private final float[] value;

    public BigramScores(int[] rowOffset, int[] colIndex, float[] value) {
        this.rowOffset = rowOffset;
        this.colIndex  = colIndex;
        this.value     = value;
    }

    public float getScore(int i, int j) {
        int start = rowOffset[i];
        int end   = rowOffset[i + 1];
        int pos   = Arrays.binarySearch(colIndex, start, end, j);
        return pos >= 0 ? value[pos] : DEFAULT_SCORE;
    }

    public int[]   rowOffsets() { return Arrays.copyOf(rowOffset, rowOffset.length); }
    public int[]   colIndex()   { return Arrays.copyOf(colIndex,  colIndex.length);  }
    public float[] values()     { return Arrays.copyOf(value,     value.length);     }
    public int     rowCount()   { return rowOffset.length - 1; }
}
