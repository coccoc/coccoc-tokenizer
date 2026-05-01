package com.coccoc.internal.trie;

/**
 * DA-trie for the main Vietnamese dictionary (multiterm + acronyms + chemicals).
 * Port of MultitermDATrie from multiterm_da_trie.hpp / multiterm_da_trie_node.hpp.
 *
 * Each node carries a weight (log-probability) plus two boolean flags:
 *   bit 0 = is_ending  (this node terminates a valid dictionary entry)
 *   bit 1 = is_special (special-token entry, e.g. chemical compound)
 */
public final class MultitermTrie extends DoubleArrayTrie {

    private final float[] weight;
    /** flags[u] bit-packed: bit 0 = isEnding, bit 1 = isSpecial. */
    private final byte[] flags;

    public MultitermTrie(int[] charMap, int[] base, int[] parent,
                         float[] weight, byte[] flags) {
        super(charMap, base, parent);
        this.weight = weight;
        this.flags  = flags;
    }

    public float getWeight(int u) { return weight[u]; }
    public boolean isEnding(int u) { return (flags[u] & 1) != 0; }
    public boolean isSpecial(int u) { return (flags[u] & 2) != 0; }

    // Accessors for DictWriter
    public float[] weightArray() { return weight; }
    public byte[]  flagsArray()  { return flags; }

}
