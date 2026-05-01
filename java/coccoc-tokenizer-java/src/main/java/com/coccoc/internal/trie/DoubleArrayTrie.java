package com.coccoc.internal.trie;

/**
 * Read-only Double-Array Trie lookup engine.
 *
 * Port of the runtime portion of da_trie.hpp. Construction (the build_all /
 * construct algorithm) lives in DictCompile (M4). This class only provides
 * node traversal for use by the Segmenter (M6+).
 *
 * SoA layout: charMap, base[], parent[] kept as primitive int[] to avoid
 * per-element object-header overhead for million-node trie pools.
 */
public class DoubleArrayTrie {

    /** Codepoint → alphabet index (-1 if codepoint not in alphabet). */
    protected final int[] charMap;

    /** base[u]: offset from which children of node u are addressed. */
    protected final int[] base;

    /** parent[u]: parent node id (or -1 for root / unoccupied slots). */
    protected final int[] parent;

    protected DoubleArrayTrie(int[] charMap, int[] base, int[] parent) {
        this.charMap = charMap;
        this.base    = base;
        this.parent  = parent;
    }

    /**
     * Walk one edge of the trie.
     *
     * @param u   current node id
     * @param cp  codepoint of the character to follow
     * @return    child node id, or -1 if no such child exists
     */
    public int findChild(int u, int cp) {
        if (cp >= charMap.length) return -1;
        int ci = charMap[cp];
        if (ci == -1) return -1;
        int childIdx = base[u] + ci;
        if (childIdx >= 0 && childIdx < parent.length && parent[childIdx] == u) {
            return childIdx;
        }
        return -1;
    }

    /** @return true if node u is occupied (has a valid parent pointer). */
    public boolean isValidNode(int u) {
        return u >= 0 && u < parent.length && parent[u] >= 0;
    }

    // Accessors for DictWriter
    public int[] charMapArray() { return charMap; }
    public int[] baseArray()    { return base; }
    public int[] parentArray()  { return parent; }

}
