package com.coccoc.internal.build;

import com.coccoc.internal.trie.SyllableTrie;
import java.util.*;

/**
 * Packs a hash-based syllable trie into a SyllableTrie (DA-trie with index[] array).
 * The index[] array starts all-zero; DictCompile sets it after reading Freq2NontoneUniFile.
 */
public final class SyllablePacker {

    private SyllablePacker() {}

    public static SyllableTrie packFromPool(List<TriePacker.HashNode> pool) {
        // Build alphabet (same as TriePacker)
        TreeSet<Integer> alphabetSet = new TreeSet<>();
        for (TriePacker.HashNode node : pool) alphabetSet.addAll(node.children.keySet());

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

        int[] positions = TriePacker.construct(pool, charMap, alphabetSize);

        int lastPos = 0;
        for (int p : positions) if (p > lastPos) lastPos = p;
        int sz = lastPos + alphabetSize + 1;

        int[]   base   = new int[sz];
        int[]   parent = new int[sz];
        float[] weight = new float[sz];
        int[]   index  = new int[sz];  // bigram row indices, -1 = unassigned
        Arrays.fill(parent, -1);
        Arrays.fill(index, -1);

        int[] mapping = new int[pool.size()];
        mapping[0] = 0;

        for (int i = 0; i < pool.size(); i++) {
            TriePacker.HashNode node = pool.get(i);
            int selfSlot = mapping[i];
            base[selfSlot] = positions[i];
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

        return new SyllableTrie(charMap, base, parent, index, weight);
    }
}
