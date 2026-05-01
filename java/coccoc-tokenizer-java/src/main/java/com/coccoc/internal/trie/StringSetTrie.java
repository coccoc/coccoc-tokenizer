package com.coccoc.internal.trie;

/**
 * DA-trie for a fixed set of strings (used for TLD whitelist in helper.hpp).
 * Port of StringSetTrie from string_set_trie.hpp.
 *
 * Only needs membership testing (contains), no weights or indices.
 */
public final class StringSetTrie extends DoubleArrayTrie {

    /** ending[u] != 0 if node u terminates a set member. */
    private final byte[] ending;

    public StringSetTrie(int[] charMap, int[] base, int[] parent, byte[] ending) {
        super(charMap, base, parent);
        this.ending = ending;
    }

    /**
     * Test whether cps[offset..offset+length) is a member of the set.
     * Mirrors C++ StringSetTrie::contains(const uint32_t* text, int length).
     */
    public boolean contains(int[] cps, int offset, int length) {
        int node = 0;
        for (int i = offset; i < offset + length; i++) {
            node = findChild(node, cps[i]);
            if (node == -1) return false;
        }
        return node >= 0 && node < ending.length && ending[node] != 0;
    }
}
