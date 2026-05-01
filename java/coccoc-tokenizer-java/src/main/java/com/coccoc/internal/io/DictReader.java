package com.coccoc.internal.io;

import com.coccoc.internal.bigram.BigramScores;
import com.coccoc.internal.trie.MultitermTrie;
import com.coccoc.internal.trie.SyllableTrie;

import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * Reads the three binary dict files written by DictCompile.
 * Format per file: magic(4) | version=1(4) | payload... | crc32(4) — all LE.
 * CRC covers every byte from position 4 (after magic) through end-of-payload;
 * the magic itself and the trailing CRC bytes are excluded from the CRC window.
 */
public final class DictReader {

    // Upper bounds to prevent heap exhaustion from a crafted-but-CRC-valid .bin.
    private static final int MAX_ALPHA_SIZE  = 0x11_0000; // full Unicode range
    private static final int MAX_NODE_COUNT  = 10_000_000;
    private static final int MAX_ROW_COUNT   = 1_000_000;
    private static final int MAX_NNZ         = 10_000_000;

    private DictReader() {}

    // =========================================================================
    // Public filesystem readers (Path-based)
    // =========================================================================

    public static MultitermTrie readMultiterm(Path file) throws IOException {
        return parseMultiterm(loadAndVerify(file, "CCMT"), file.getFileName().toString());
    }

    public static SyllableTrie readSyllable(Path file) throws IOException {
        return parseSyllable(loadAndVerify(file, "CCSY"), file.getFileName().toString());
    }

    public static BigramScores readBigram(Path file) throws IOException {
        return parseBigram(loadAndVerify(file, "CCBG"), file.getFileName().toString());
    }

    // =========================================================================
    // Public classpath readers (InputStream-based)
    // =========================================================================

    public static MultitermTrie readMultiterm(InputStream in, String name) throws IOException {
        return parseMultiterm(loadAndVerify(in, "CCMT", name), name);
    }

    public static SyllableTrie readSyllable(InputStream in, String name) throws IOException {
        return parseSyllable(loadAndVerify(in, "CCSY", name), name);
    }

    public static BigramScores readBigram(InputStream in, String name) throws IOException {
        return parseBigram(loadAndVerify(in, "CCBG", name), name);
    }

    // =========================================================================
    // Private parsers (operate on already-verified byte arrays)
    // =========================================================================

    private static MultitermTrie parseMultiterm(byte[] bytes, String name) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(bytes, 8, bytes.length - 8).order(ByteOrder.LITTLE_ENDIAN);
        try {
            int alphaSize = buf.getInt();
            checkSize(alphaSize, MAX_ALPHA_SIZE, "alphaSize");
            int[] codepoints = new int[alphaSize];
            for (int i = 0; i < alphaSize; i++) codepoints[i] = buf.getInt();

            int sz = buf.getInt();
            checkSize(sz, MAX_NODE_COUNT, "node count");
            int[]   base   = new int[sz];
            int[]   parent = new int[sz];
            float[] weight = new float[sz];
            byte[]  flags  = new byte[sz];
            for (int i = 0; i < sz; i++) base[i]   = buf.getInt();
            for (int i = 0; i < sz; i++) parent[i] = buf.getInt();
            for (int i = 0; i < sz; i++) weight[i] = buf.getFloat();
            buf.get(flags);

            // P0#3: replace NaN weights (e.g. from bundled dict entries) with NEGATIVE_INFINITY
            // so they never silently poison the Viterbi DP comparison chain
            for (int i = 0; i < sz; i++) {
                if (Float.isNaN(weight[i])) weight[i] = Float.NEGATIVE_INFINITY;
            }
            return new MultitermTrie(buildCharMap(codepoints), base, parent, weight, flags);
        } catch (BufferUnderflowException e) {
            throw new IOException("truncated payload in " + name, e);
        }
    }

    private static SyllableTrie parseSyllable(byte[] bytes, String name) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(bytes, 8, bytes.length - 8).order(ByteOrder.LITTLE_ENDIAN);
        try {
            int alphaSize = buf.getInt();
            checkSize(alphaSize, MAX_ALPHA_SIZE, "alphaSize");
            int[] codepoints = new int[alphaSize];
            for (int i = 0; i < alphaSize; i++) codepoints[i] = buf.getInt();

            int sz = buf.getInt();
            checkSize(sz, MAX_NODE_COUNT, "node count");
            int[]   base   = new int[sz];
            int[]   parent = new int[sz];
            float[] weight = new float[sz];
            int[]   index  = new int[sz];
            for (int i = 0; i < sz; i++) base[i]   = buf.getInt();
            for (int i = 0; i < sz; i++) parent[i] = buf.getInt();
            for (int i = 0; i < sz; i++) weight[i] = buf.getFloat();
            for (int i = 0; i < sz; i++) index[i]  = buf.getInt();

            return new SyllableTrie(buildCharMap(codepoints), base, parent, index, weight);
        } catch (BufferUnderflowException e) {
            throw new IOException("truncated payload in " + name, e);
        }
    }

    private static BigramScores parseBigram(byte[] bytes, String name) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(bytes, 8, bytes.length - 8).order(ByteOrder.LITTLE_ENDIAN);
        try {
            int n = buf.getInt();
            checkSize(n, MAX_ROW_COUNT, "rowCount");
            int[] rowOffset = new int[n + 1];
            for (int i = 0; i <= n; i++) rowOffset[i] = buf.getInt();

            // P0#2: validate monotone invariant before allocating colIndex/value arrays
            for (int i = 0; i < n; i++) {
                if (rowOffset[i] > rowOffset[i + 1])
                    throw new IOException("rowOffset invariant violated at row " + i
                            + ": offset[" + i + "]=" + rowOffset[i]
                            + " > offset[" + (i + 1) + "]=" + rowOffset[i + 1]);
            }
            int totalNnz = rowOffset[n];
            checkSize(totalNnz, MAX_NNZ, "nnz count");
            int[]   colIndex = new int[totalNnz];
            float[] value    = new float[totalNnz];
            for (int i = 0; i < totalNnz; i++) colIndex[i] = buf.getInt();
            for (int i = 0; i < totalNnz; i++) value[i]    = buf.getFloat();

            return new BigramScores(rowOffset, colIndex, value);
        } catch (BufferUnderflowException e) {
            throw new IOException("truncated payload in " + name, e);
        }
    }

    // =========================================================================
    // Shared low-level helpers (package-private for tests)
    // =========================================================================

    /** Load file bytes and verify magic, version, and CRC before parsing. */
    static byte[] loadAndVerify(Path file, String expectedMagic) throws IOException {
        return verifyBytes(Files.readAllBytes(file), expectedMagic, file.getFileName().toString());
    }

    /** Load stream bytes and verify magic, version, and CRC before parsing. */
    static byte[] loadAndVerify(InputStream in, String expectedMagic, String name) throws IOException {
        return verifyBytes(in.readAllBytes(), expectedMagic, name);
    }

    private static byte[] verifyBytes(byte[] bytes, String expectedMagic, String name) throws IOException {
        if (bytes.length < 12)
            throw new IOException("truncated: " + name + " too short");

        if (bytes[0] != expectedMagic.charAt(0) || bytes[1] != expectedMagic.charAt(1)
                || bytes[2] != expectedMagic.charAt(2) || bytes[3] != expectedMagic.charAt(3))
            throw new IOException("bad magic: expected " + expectedMagic
                    + ", got " + new String(bytes, 0, 4));

        int version = leInt(bytes, 4);
        if (version != 1)
            throw new IOException("version mismatch: expected 1, got " + version);

        CRC32 crc = new CRC32();
        crc.update(bytes, 4, bytes.length - 8);
        int stored = leInt(bytes, bytes.length - 4);
        if ((int) crc.getValue() != stored)
            throw new IOException("crc mismatch: payload corrupted");

        return bytes;
    }

    /** Reconstruct charMap (codepoint→alphabet-index) from on-disk codepoints array. */
    static int[] buildCharMap(int[] codepoints) throws IOException {
        if (codepoints.length == 0) return new int[0];
        int maxCp = 0;
        for (int cp : codepoints) {
            if (cp < 0 || cp >= MAX_ALPHA_SIZE)
                throw new IOException("invalid codepoint in dict: " + cp);
            if (cp > maxCp) maxCp = cp;
        }
        int[] charMap = new int[maxCp + 1];
        Arrays.fill(charMap, -1);
        for (int i = 0; i < codepoints.length; i++) charMap[codepoints[i]] = i;
        return charMap;
    }

    private static void checkSize(int n, int max, String what) throws IOException {
        if (n < 0 || n > max)
            throw new IOException("truncated: implausible " + what + ": " + n);
    }

    private static int leInt(byte[] b, int off) {
        return (b[off] & 0xFF)
             | ((b[off + 1] & 0xFF) << 8)
             | ((b[off + 2] & 0xFF) << 16)
             | ((b[off + 3] & 0xFF) << 24);
    }
}
