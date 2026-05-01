package com.coccoc.tools;

import com.coccoc.internal.build.SyllablePacker;
import com.coccoc.internal.build.TriePacker;
import com.coccoc.internal.io.VarintReader;
import com.coccoc.internal.lang.VnLangTool;
import com.coccoc.internal.trie.MultitermTrie;
import com.coccoc.internal.trie.SyllableTrie;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;

/**
 * CLI tool: compiles raw text dictionary sources into binary .bin files.
 * Port of utils/dict_compiler.cpp loading + our own Java-native binary format.
 *
 * Usage: java -cp ... com.coccoc.tools.DictCompile <dicts-dir> <out-dir>
 *
 * Reads from <dicts-dir>:
 *   tokenizer/vndic_multiterm, tokenizer/acronyms, tokenizer/chemical_comp,
 *   tokenizer/special_token.strong, tokenizer/Freq2NontoneUniFile, tokenizer/nontone_pair_freq
 *
 * Writes to <out-dir>:
 *   multiterm.bin, syllable.bin, bigram.bin
 */
public final class DictCompile {

    // ------------ Weight formula (multiterm_hash_trie_node.hpp:19-25) --------

    private static final double[] WEIGHT_PARAM = {
        0.38, 1.0,   // spaceCount=0
        0.14, 2.59,  // spaceCount=1
        1.42, 4.42,  // spaceCount=2
        1.45, 0.23,  // spaceCount=3
        0.10, 1.0,   // spaceCount=4+
    };

    public static double multitermWeight(int freq, int spaceCount) {
        int sc = Math.min(spaceCount, 4);
        double log2freq = Math.log(freq + 3) / Math.log(2);
        return Math.pow(log2freq, WEIGHT_PARAM[sc * 2])
             * Math.pow(sc + 1,  WEIGHT_PARAM[sc * 2 + 1]);
    }

    // Bigram pair-score params (dict_compiler.cpp inline vector)
    private static final double PAIR_COEFF     = 0.1;
    private static final double PAIR_LEN_POWER = 0.994141;
    private static final double PAIR_POWER     = 0.19;

    private static float pairScore(int pairLen, int pairFreq) {
        return (float)(PAIR_COEFF
            * Math.pow(pairLen,  PAIR_LEN_POWER)
            * Math.pow(pairFreq, PAIR_POWER));
    }

    // =================== Hash-trie builder ==================================

    static final class HashTrieBuilder {
        final List<TriePacker.HashNode> pool = new ArrayList<>();

        HashTrieBuilder() { pool.add(new TriePacker.HashNode()); }

        /** Add a word to this hash trie. Freq is accumulated (max kept). */
        void add(String word, int freq, boolean isSpecial) {
            if (word.isEmpty()) return;
            int sc = countSpaces(word);
            double w = multitermWeight(freq, sc);
            int[] cps = word.codePoints().toArray();
            int cur = 0;
            for (int cp : cps) {
                TriePacker.HashNode node = pool.get(cur);
                if (!node.children.containsKey(cp)) {
                    node.children.put(cp, pool.size());
                    pool.add(new TriePacker.HashNode());
                }
                cur = node.children.get(cp);
            }
            TriePacker.HashNode terminal = pool.get(cur);
            if (terminal.frequency < freq) {
                terminal.frequency = freq;
                terminal.weight = (float) w;
                terminal.spaceCount = sc;
                terminal.isSpecial = isSpecial || terminal.isSpecial;
            }
        }

        /** Add without marking as isEnding (root-form variant). */
        void addNonEnding(String word, int freq) {
            if (word.isEmpty()) return;
            int[] cps = word.codePoints().toArray();
            int cur = 0;
            for (int cp : cps) {
                TriePacker.HashNode node = pool.get(cur);
                if (!node.children.containsKey(cp)) {
                    node.children.put(cp, pool.size());
                    pool.add(new TriePacker.HashNode());
                }
                cur = node.children.get(cp);
            }
            // Only create path; don't mark as ending (frequency stays -1)
        }
    }

    // =================== Dict loading =======================================

    static void loadVndicMultiterm(Path dir,
                                   HashTrieBuilder mt, HashTrieBuilder syl) throws IOException {
        Path p = dir.resolve("vndic_multiterm");
        try (BufferedReader br = Files.newBufferedReader(p)) {
            String line;
            while ((line = br.readLine()) != null) {
                int cutPos = findCutPos(line);
                if (cutPos < 0) continue;
                int freq = parseNumber(line, cutPos + 1);
                String word = line.substring(0, cutPos).strip();
                if (word.isEmpty()) continue;

                mt.add(word, freq, false);
                String root = lowerRoot(word);
                if (!root.equals(word)) mt.addNonEnding(root, freq);

                // Add individual syllables to syllable trie
                for (String syllable : word.split(" ")) {
                    if (syllable.isEmpty()) continue;
                    syl.add(syllable, freq, false);
                    String syllRoot = lowerRoot(syllable);
                    if (!syllRoot.equals(syllable)) syl.addNonEnding(syllRoot, freq);
                }
            }
        }
    }

    static void loadCommonTerms(HashTrieBuilder mt) {
        int maxFreq = Integer.MAX_VALUE;
        mt.add("m2", maxFreq, false);
        mt.add("m3", maxFreq, false);
        mt.add("km2", maxFreq, false);
    }

    static void loadAcronyms(Path dir,
                              HashTrieBuilder mt, HashTrieBuilder syl) throws IOException {
        Path p = dir.resolve("acronyms");
        try (BufferedReader br = Files.newBufferedReader(p)) {
            String line;
            while ((line = br.readLine()) != null) {
                // Format: "word freq|..." — first two space-separated tokens
                int spaceIdx = line.indexOf(' ');
                if (spaceIdx < 0) continue;
                String word = line.substring(0, spaceIdx);
                int freq = parseNumber(line, spaceIdx + 1);
                if (word.isEmpty() || freq <= 0) continue;

                mt.add(word, freq, false);
                syl.add(word, freq, false);
            }
        }
    }

    static void loadChemical(Path dir, HashTrieBuilder mt) throws IOException {
        Path p = dir.resolve("chemical_comp");
        try (BufferedReader br = Files.newBufferedReader(p)) {
            String line;
            while ((line = br.readLine()) != null) {
                String word = line.strip();
                if (!word.isEmpty()) mt.add(word, Integer.MAX_VALUE, true);
            }
        }
    }

    static void loadSpecial(Path dir, HashTrieBuilder mt) throws IOException {
        // Hardcoded special terms
        String[] hardcoded = {
            "vietnam+", "google+", "notepad++", "c#", "c++", "g++",
            "xbase++", "vc++", "k+", "g+", "16+", "18+"
        };
        for (String t : hardcoded) mt.add(t, Integer.MAX_VALUE, true);

        Path p = dir.resolve("special_token.strong");
        try (BufferedReader br = Files.newBufferedReader(p)) {
            String line;
            while ((line = br.readLine()) != null) {
                String word = line.strip();
                if (!word.isEmpty()) mt.add(word, Integer.MAX_VALUE, true);
            }
        }
    }

    // =================== Syllable index assignment ==========================

    /**
     * Reads Freq2NontoneUniFile, assigns bigram row indices to the syllable trie,
     * and returns syllable codepoint lengths for the pair-score formula.
     */
    static int[] assignSyllableIndices(Path dir, SyllableTrie trie) throws IOException {
        Path p = dir.resolve("Freq2NontoneUniFile");
        List<Integer> lengths = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(p)) {
            String line;
            while ((line = br.readLine()) != null) {
                String syllable = line.strip();
                if (syllable.isEmpty()) continue;
                int bigramIdx = lengths.size();
                int[] cps = syllable.codePoints().toArray();
                lengths.add(cps.length);
                // Walk trie and assign index to the terminal node
                int node = 0;
                for (int cp : cps) {
                    node = trie.findChild(node, cp);
                    if (node == -1) break;
                }
                if (node >= 0) trie.setIndex(node, bigramIdx);
            }
        }
        int[] arr = new int[lengths.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = lengths.get(i);
        return arr;
    }

    // =================== Bigram build & write ================================

    static void writeBigramBin(Path srcDir, Path out, int[] syllableLengths) throws IOException {
        Path bigramSrc = srcDir.resolve("nontone_pair_freq");
        int n = syllableLengths.length;

        // --- Pass 1: count nnz per row ---
        int[] rowNnz = new int[n];
        try (VarintReader vr = new VarintReader(
                new BufferedInputStream(Files.newInputStream(bigramSrc)))) {
            int fileN = vr.nextInt();
            if (fileN != n) throw new IOException(
                "Bigram row count mismatch: file=" + fileN + " syllables=" + n);
            for (int i = 0; i < n; i++) {
                int nPairs = vr.nextInt();
                rowNnz[i] = nPairs;
                for (int k = 0; k < nPairs; k++) {
                    vr.nextInt(); // delta
                    vr.nextInt(); // freq
                }
            }
        }

        // Build CSR row offsets
        int[] rowOffset = new int[n + 1];
        for (int i = 0; i < n; i++) rowOffset[i + 1] = rowOffset[i] + rowNnz[i];
        int totalNnz = rowOffset[n];

        int[] colIndex = new int[totalNnz];
        float[] value  = new float[totalNnz];

        // --- Pass 2: collect data ---
        try (VarintReader vr = new VarintReader(
                new BufferedInputStream(Files.newInputStream(bigramSrc)))) {
            vr.nextInt(); // skip n
            int[] writePos = rowOffset.clone();
            for (int i = 0; i < n; i++) {
                int nPairs = vr.nextInt();
                int secondIdx = 0;
                for (int k = 0; k < nPairs; k++) {
                    secondIdx += vr.nextInt();
                    int pairFreq = vr.nextInt();
                    if (secondIdx < n) {
                        int pairLen = syllableLengths[i] + syllableLengths[secondIdx];
                        int pos = writePos[i]++;
                        colIndex[pos] = secondIdx;
                        value[pos]    = pairScore(pairLen, pairFreq);
                    }
                }
            }
        }

        // --- Write bigram.bin ---
        ByteArrayOutputStream baos = new ByteArrayOutputStream(totalNnz * 8 + (n + 2) * 4 + 12);
        CRC32 crc = new CRC32();
        baos.write("CCBG".getBytes());
        writeLE32(baos, crc, 1); // version
        writeLE32(baos, crc, n); // rowCount
        for (int v : rowOffset) writeLE32(baos, crc, v);
        for (int v : colIndex)  writeLE32(baos, crc, v);
        for (float v : value)   writeLEFloat(baos, crc, v);
        writeLE32NoUpdate(baos, (int) crc.getValue());
        Files.write(out, baos.toByteArray());
        System.out.printf("  bigram.bin  %d rows, %d nnz, %.1f MB%n",
            n, totalNnz, baos.size() / 1_048_576.0);
    }

    // =================== Binary format writers ==============================

    static void writeMultitermBin(Path out, MultitermTrie trie) throws IOException {
        int[] codepoints = invertCharMap(trie.charMapArray());
        int sz = trie.baseArray().length;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CRC32 crc = new CRC32();
        baos.write("CCMT".getBytes());
        writeLE32(baos, crc, 1);              // version
        writeLE32(baos, crc, codepoints.length);
        for (int cp : codepoints)             writeLE32(baos, crc, cp);
        writeLE32(baos, crc, sz);
        for (int v : trie.baseArray())        writeLE32(baos, crc, v);
        for (int v : trie.parentArray())      writeLE32(baos, crc, v);
        for (float v : trie.weightArray())    writeLEFloat(baos, crc, v);
        byte[] flags = trie.flagsArray();
        crc.update(flags);
        baos.write(flags);
        writeLE32NoUpdate(baos, (int) crc.getValue());
        Files.write(out, baos.toByteArray());
        System.out.printf("  multiterm.bin  pool=%d, alpha=%d, %.1f MB%n",
            sz, codepoints.length, baos.size() / 1_048_576.0);
    }

    static void writeSyllableBin(Path out, SyllableTrie trie, int syllableCount) throws IOException {
        int[] codepoints = invertCharMap(trie.charMapArray());
        int sz = trie.baseArray().length;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CRC32 crc = new CRC32();
        baos.write("CCSY".getBytes());
        writeLE32(baos, crc, 1);              // version
        writeLE32(baos, crc, codepoints.length);
        for (int cp : codepoints)             writeLE32(baos, crc, cp);
        writeLE32(baos, crc, sz);
        for (int v : trie.baseArray())        writeLE32(baos, crc, v);
        for (int v : trie.parentArray())      writeLE32(baos, crc, v);
        for (float v : trie.weightArray())    writeLEFloat(baos, crc, v);
        for (int v : trie.indexArray())       writeLE32(baos, crc, v);
        writeLE32(baos, crc, syllableCount);
        writeLE32NoUpdate(baos, (int) crc.getValue());
        Files.write(out, baos.toByteArray());
        System.out.printf("  syllable.bin  pool=%d, syllables=%d, %.1f MB%n",
            sz, syllableCount, baos.size() / 1_048_576.0);
    }

    // =================== Compile entrypoint =================================

    public static void compile(Path dictsDir, Path outDir) throws IOException {
        VnLangTool.initSimple();
        Files.createDirectories(outDir);
        Path tokenDir = dictsDir.resolve("tokenizer");

        System.out.println("Loading dict sources...");
        HashTrieBuilder mt  = new HashTrieBuilder();
        HashTrieBuilder syl = new HashTrieBuilder();

        loadVndicMultiterm(tokenDir, mt, syl);
        loadCommonTerms(mt);
        loadAcronyms(tokenDir, mt, syl);
        loadChemical(tokenDir, mt);
        loadSpecial(tokenDir, mt);
        System.out.printf("  multiterm nodes: %d  syllable nodes: %d%n",
            mt.pool.size(), syl.pool.size());

        System.out.println("Packing multiterm trie...");
        MultitermTrie multitermTrie = TriePacker.packFromPool(mt.pool);

        System.out.println("Packing syllable trie...");
        SyllableTrie syllableTrie = SyllablePacker.packFromPool(syl.pool);

        System.out.println("Writing multiterm.bin...");
        writeMultitermBin(outDir.resolve("multiterm.bin"), multitermTrie);

        System.out.println("Assigning syllable indices...");
        int[] syllableLengths = assignSyllableIndices(tokenDir, syllableTrie);
        System.out.printf("  syllableCount: %d%n", syllableLengths.length);

        System.out.println("Writing syllable.bin...");
        writeSyllableBin(outDir.resolve("syllable.bin"), syllableTrie, syllableLengths.length);

        System.out.println("Building + writing bigram.bin...");
        writeBigramBin(tokenDir, outDir.resolve("bigram.bin"), syllableLengths);

        System.out.println("Done.");
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: DictCompile <dicts-dir> <out-dir>");
            System.exit(1);
        }
        compile(Path.of(args[0]), Path.of(args[1]));
    }

    // =================== Helpers ============================================

    /** Mirror of dict_compiler.cpp find_cut_pos: finds position before the trailing number. */
    static int findCutPos(String line) {
        int i = line.length() - 1;
        while (i >= 0 && !Character.isDigit(line.charAt(i))) i--;
        if (i < 0) return -1;
        while (i >= 0 && Character.isDigit(line.charAt(i))) i--;
        return i;
    }

    /** Parse decimal integer starting at position from. Stops at first non-digit. */
    static int parseNumber(String line, int from) {
        long num = 0;
        while (from < line.length() && Character.isDigit(line.charAt(from))) {
            num = num * 10 + (line.charAt(from) - '0');
            from++;
        }
        return (int) Math.min(num, Integer.MAX_VALUE);
    }

    static int countSpaces(String word) {
        int count = 0;
        for (int i = 0; i < word.length(); i++) if (word.charAt(i) == ' ') count++;
        return count;
    }

    static String lowerRoot(String word) {
        int[] cps = word.codePoints().toArray();
        StringBuilder sb = new StringBuilder(word.length());
        for (int cp : cps) sb.appendCodePoint(VnLangTool.lowerRoot(cp));
        return sb.toString();
    }

    /** Invert charMap: returns array where result[i] = codepoint with charMap index i. */
    static int[] invertCharMap(int[] charMap) {
        int size = 0;
        for (int v : charMap) if (v >= size) size = v + 1;
        int[] result = new int[size];
        for (int cp = 0; cp < charMap.length; cp++) {
            int idx = charMap[cp];
            if (idx >= 0) result[idx] = cp;
        }
        return result;
    }

    // =================== Little-endian I/O helpers ==========================

    static void writeLE32(OutputStream out, CRC32 crc, int v) throws IOException {
        byte[] b = {(byte)v, (byte)(v>>8), (byte)(v>>16), (byte)(v>>24)};
        crc.update(b);
        out.write(b);
    }

    static void writeLEFloat(OutputStream out, CRC32 crc, float v) throws IOException {
        writeLE32(out, crc, Float.floatToRawIntBits(v));
    }

    /** Write CRC value itself without updating the running CRC. */
    static void writeLE32NoUpdate(OutputStream out, int v) throws IOException {
        out.write((byte)v);
        out.write((byte)(v>>8));
        out.write((byte)(v>>16));
        out.write((byte)(v>>24));
    }
}
