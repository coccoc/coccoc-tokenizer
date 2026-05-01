package com.coccoc.internal.trie;

/**
 * DA-trie for Vietnamese syllables, used by the sticky-tokenization path.
 * Port of SyllableDATrie from syllable_da_trie.hpp / syllable_da_trie_node.hpp.
 *
 * Each node carries a weight and an integer index that maps the syllable to
 * its row in the bigram frequency CSR (set during dict-compile, not at lookup).
 */
public final class SyllableTrie extends DoubleArrayTrie {

    private final int[]   index; // mutable post-pack (see setIndex)
    private final float[] weight;

    public SyllableTrie(int[] charMap, int[] base, int[] parent,
                        int[] index, float[] weight) {
        super(charMap, base, parent);
        this.index  = index;
        this.weight = weight;
    }

    /** Bigram row index for the syllable ending at node u (-1 = not a syllable). */
    public int getIndex(int u) { return index[u]; }

    /** Sets the bigram row index after the trie is packed (called during dict compile). */
    public void setIndex(int u, int idx) { index[u] = idx; }

    public float getWeight(int u) { return weight[u]; }

    // Accessors for DictWriter
    public int[]   indexArray()  { return index; }
    public float[] weightArray() { return weight; }

}
