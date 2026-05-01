package com.coccoc.tools;

import com.coccoc.internal.build.SyllablePacker;
import com.coccoc.internal.trie.MultitermTrie;
import com.coccoc.internal.trie.SyllableTrie;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

/**
 * Test helper: exposes DictCompile's package-private binary writers so that
 * tests outside com.coccoc.tools can write synthetic .bin files for round-trip
 * tests without widening DictCompile's own visibility.
 */
public final class DictCompileTestSupport {

    private DictCompileTestSupport() {}

    public static void writeMultitermBin(Path out, MultitermTrie trie) throws IOException {
        DictCompile.writeMultitermBin(out, trie);
    }

    public static void writeSyllableBin(Path out, SyllableTrie trie, int syllableCount)
            throws IOException {
        DictCompile.writeSyllableBin(out, trie, syllableCount);
    }

    /**
     * Writes a bigram.bin directly from pre-built CSR arrays for round-trip tests.
     * Mirrors DictCompile.writeBigramBin's binary layout without requiring dict sources.
     */
    public static void writeBigramBin(Path out, int n,
                                      int[] rowOffset, int[] colIndex, float[] value)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CRC32 crc = new CRC32();
        baos.write("CCBG".getBytes());
        writeLE32(baos, crc, 1);          // version
        writeLE32(baos, crc, n);          // rowCount
        for (int v : rowOffset) writeLE32(baos, crc, v);
        for (int v : colIndex)  writeLE32(baos, crc, v);
        for (float v : value)   writeLEFloat(baos, crc, v);
        writeLE32NoUpdate(baos, (int) crc.getValue());
        Files.write(out, baos.toByteArray());
    }


    /**
     * Builds a SyllableTrie from a list of words.
     * Uses package-private HashTrieBuilder (com.coccoc.tools) + SyllablePacker.
     */
    public static SyllableTrie buildSyllableTrie(String... words) {
        DictCompile.HashTrieBuilder builder = new DictCompile.HashTrieBuilder();
        for (String w : words) builder.add(w, 1, false);
        return SyllablePacker.packFromPool(builder.pool);
    }

    // ── LE helpers (mirrors DictCompile package-private helpers) ─────────────

    private static void writeLE32(OutputStream out, CRC32 crc, int v) throws IOException {
        byte[] b = {(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)};
        crc.update(b);
        out.write(b);
    }

    private static void writeLEFloat(OutputStream out, CRC32 crc, float v) throws IOException {
        writeLE32(out, crc, Float.floatToRawIntBits(v));
    }

    private static void writeLE32NoUpdate(OutputStream out, int v) throws IOException {
        out.write((byte) v);
        out.write((byte) (v >> 8));
        out.write((byte) (v >> 16));
        out.write((byte) (v >> 24));
    }
}
