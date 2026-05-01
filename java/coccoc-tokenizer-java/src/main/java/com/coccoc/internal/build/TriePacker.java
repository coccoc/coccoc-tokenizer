package com.coccoc.internal.build;

import com.coccoc.internal.trie.MultitermTrie;
import java.util.*;

/**
 * Packs a hash-based trie into a DA-trie (parallel arrays).
 * Port of da_trie.hpp build_trie() + construct() — the construction half only.
 * The runtime lookup half lives in DoubleArrayTrie.
 */
public final class TriePacker {

    // -----------------------------------------------------------------------
    // Intermediate hash-trie node (C++ HashTrieNode analog)
    // -----------------------------------------------------------------------

    public static final class HashNode {
        public int frequency = -1;
        public float weight = 0f;
        public boolean isSpecial = false;
        public int spaceCount = 0;
        /** children: codepoint → node index in the flat list */
        public final TreeMap<Integer, Integer> children = new TreeMap<>();

        public boolean isEnding() { return frequency >= 0; }
    }

    // -----------------------------------------------------------------------
    // Build hash trie from word list (for tests — uniform weight 1.0f)
    // -----------------------------------------------------------------------

    public static HashNode buildHashTrie(String[] words) {
        List<HashNode> pool = new ArrayList<>();
        pool.add(new HashNode()); // root

        for (String word : words) {
            int cur = 0;
            for (int cp : word.codePoints().toArray()) {
                HashNode node = pool.get(cur);
                if (!node.children.containsKey(cp)) {
                    node.children.put(cp, pool.size());
                    pool.add(new HashNode());
                }
                cur = node.children.get(cp);
            }
            pool.get(cur).frequency = 1;
            pool.get(cur).weight = 1.0f;
        }
        root_pool = pool;
        return pool.get(0);
    }

    // Package-private pool reference set by buildHashTrie (test helper).
    static List<HashNode> root_pool;

    // -----------------------------------------------------------------------
    // Pack: hash trie → MultitermTrie
    // -----------------------------------------------------------------------

    public static MultitermTrie pack(HashNode root) {
        if (root_pool == null)
            throw new IllegalStateException("call buildHashTrie() before pack()");
        return packFromPool(root_pool);
    }

    public static MultitermTrie packFromPool(List<HashNode> pool) {
        // Build alphabet
        TreeSet<Integer> alphabetSet = new TreeSet<>();
        for (HashNode node : pool) alphabetSet.addAll(node.children.keySet());

        int[] charMap;
        if (alphabetSet.isEmpty()) {
            charMap = new int[0];
        } else {
            int maxCp = alphabetSet.last();
            charMap = new int[maxCp + 1];
            Arrays.fill(charMap, -1);
            int idx = 0;
            for (int cp : alphabetSet) charMap[cp] = idx++;
        }
        int alphabetSize = alphabetSet.size();

        int[] positions = construct(pool, charMap, alphabetSize);

        // DA pool size: largest base + alphabet
        int lastPos = 0;
        for (int p : positions) if (p > lastPos) lastPos = p;
        int sz = lastPos + alphabetSize + 1;

        int[]   base   = new int[sz];
        int[]   parent = new int[sz];
        float[] weight = new float[sz];
        byte[]  flags  = new byte[sz];
        Arrays.fill(parent, -1);

        // mapping[hashNodeIdx] = DA pool slot index
        int[] mapping = new int[pool.size()];
        mapping[0] = 0;

        for (int i = 0; i < pool.size(); i++) {
            HashNode node = pool.get(i);
            int selfSlot = mapping[i];
            base[selfSlot] = positions[i];
            if (node.isEnding())  flags[selfSlot] |= 1;
            if (node.isSpecial)   flags[selfSlot] |= 2;
            weight[selfSlot] = node.weight;

            for (Map.Entry<Integer, Integer> entry : node.children.entrySet()) {
                int cp       = entry.getKey();
                int childIdx = entry.getValue();
                int ci       = charMap[cp];
                int slot     = base[selfSlot] + ci;
                mapping[childIdx] = slot;
                parent[slot]      = selfSlot;
            }
        }

        return new MultitermTrie(charMap, base, parent, weight, flags);
    }

    // -----------------------------------------------------------------------
    // construct: find base positions for all hash-trie nodes.
    // Port of da_trie.hpp:78-189.
    //
    // Invariant: pos[k] = sorted set of base positions p (p >= 1) where
    //   state[p + k] == false (i.e. slot k at base p is currently free).
    //
    // Extension: when disclosing new base position curEnd, state[curEnd + k]
    //   is freshly false for all k → add curEnd to every pos[k].
    //
    // Removal: when occupying DA slot curPos = foundPos + offset, for every k
    //   remove (curPos - k) from pos[k] because state[curPos] is now true.
    // -----------------------------------------------------------------------

    static int[] construct(List<HashNode> pool, int[] charMap, int alphabetSize) {
        if (alphabetSize == 0) return new int[pool.size()];

        // pos[k] = sorted candidate base positions where slot k is free
        @SuppressWarnings("unchecked")
        TreeSet<Integer>[] pos = new TreeSet[alphabetSize];
        for (int k = 0; k < alphabetSize; k++) {
            pos[k] = new TreeSet<>();
            // Base position 1: state[1+k] is free for all k initially
            pos[k].add(1);
        }

        // state[p] = true → DA pool position p is occupied
        boolean[] state = new boolean[alphabetSize + 2];
        // curEnd: next base position not yet disclosed into pos sets.
        // We start at 2 because base=1 was seeded above.
        int curEnd = 2;

        int[] res = new int[pool.size()];

        for (int i = 0; i < pool.size(); i++) {
            HashNode node = pool.get(i);
            if (node.children.isEmpty()) {
                res[i] = 0;
                continue;
            }

            // Build mask: sorted alphabet indices of children (rarest pos-set first)
            Integer[] mask = new Integer[node.children.size()];
            int idx = 0;
            for (int cp : node.children.keySet()) mask[idx++] = charMap[cp];
            Arrays.sort(mask, Comparator.comparingInt(ci -> pos[ci].size()));

            // Find smallest base p in pos[mask[0]] also present in all pos[mask[j]]
            int foundPos = -1;
            outer:
            for (int p : pos[mask[0]]) {
                for (int j = 1; j < mask.length; j++) {
                    if (!pos[mask[j]].contains(p)) continue outer;
                }
                foundPos = p;
                break;
            }
            if (foundPos == -1) foundPos = curEnd;
            res[i] = foundPos;

            // Disclose new base positions up through foundPos + max(mask)
            int maxMask = 0;
            for (int m : mask) if (m > maxMask) maxMask = m;
            int affectedEnd = foundPos + maxMask;

            while (curEnd <= affectedEnd) {
                // Grow state array to cover curEnd + (alphabetSize-1)
                if (curEnd + alphabetSize >= state.length) {
                    state = Arrays.copyOf(state, curEnd + alphabetSize + 2);
                }
                // Disclose base=curEnd: state[curEnd+k] is false for all k → add curEnd to pos[k]
                for (int k = 0; k < alphabetSize; k++) {
                    pos[k].add(curEnd);
                }
                curEnd++;
            }

            // Mark child slots occupied and remove invalidated candidates from pos
            for (int offset : mask) {
                int curPos = foundPos + offset;
                // state[curPos] becoming true: any base p = curPos - k with slot k pointed here
                // is no longer valid → remove p from pos[k]
                for (int k = 0; k < alphabetSize; k++) {
                    int basePos = curPos - k;
                    if (basePos >= 1) pos[k].remove(basePos);
                }
                state[curPos] = true;
            }
        }

        return res;
    }
}
