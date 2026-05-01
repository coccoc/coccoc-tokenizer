package com.coccoc.internal.io;

import com.coccoc.internal.build.TriePacker;
import com.coccoc.internal.trie.MultitermTrie;
import com.coccoc.internal.bigram.BigramScores;
import com.coccoc.internal.trie.SyllableTrie;
import com.coccoc.tools.DictCompileTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DictReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void readMultiterm_roundTripsTinyTrie() throws IOException {
        TriePacker.HashNode root = TriePacker.buildHashTrie(new String[]{"ab"});
        MultitermTrie written = TriePacker.pack(root);

        Path binFile = tempDir.resolve("multiterm.bin");
        DictCompileTestSupport.writeMultitermBin(binFile, written);

        MultitermTrie read = DictReader.readMultiterm(binFile);

        int nodeA = read.findChild(0, 'a');
        assertNotEquals(-1, nodeA, "should find child 'a'");
        int nodeB = read.findChild(nodeA, 'b');
        assertNotEquals(-1, nodeB, "should find child 'b' after 'a'");
        assertTrue(read.isEnding(nodeB), "node at 'ab' should be an ending");
    }
    @Test
    void readMultiterm_rejectsBadMagic() throws IOException {
        TriePacker.HashNode root = TriePacker.buildHashTrie(new String[]{"a"});
        Path binFile = tempDir.resolve("multiterm_badmagic.bin");
        DictCompileTestSupport.writeMultitermBin(binFile, TriePacker.pack(root));

        byte[] bytes = java.nio.file.Files.readAllBytes(binFile);
        bytes[0] = 'X'; // corrupt magic
        java.nio.file.Files.write(binFile, bytes);

        IOException ex = assertThrows(IOException.class,
                () -> DictReader.readMultiterm(binFile));
        assertTrue(ex.getMessage().contains("bad magic"), "message: " + ex.getMessage());
    }
    @Test
    void readMultiterm_rejectsVersionMismatch() throws IOException {
        TriePacker.HashNode root = TriePacker.buildHashTrie(new String[]{"a"});
        Path binFile = tempDir.resolve("multiterm_badver.bin");
        DictCompileTestSupport.writeMultitermBin(binFile, TriePacker.pack(root));

        byte[] bytes = java.nio.file.Files.readAllBytes(binFile);
        bytes[4] = 2; bytes[5] = 0; bytes[6] = 0; bytes[7] = 0; // version = 2 LE
        // Recompute CRC to avoid crc-check shadowing the version error
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(bytes, 4, bytes.length - 8);
        int cv = (int) crc.getValue();
        int pe = bytes.length - 4;
        bytes[pe]   = (byte) cv;
        bytes[pe+1] = (byte)(cv >> 8);
        bytes[pe+2] = (byte)(cv >> 16);
        bytes[pe+3] = (byte)(cv >> 24);
        java.nio.file.Files.write(binFile, bytes);

        IOException ex = assertThrows(IOException.class,
                () -> DictReader.readMultiterm(binFile));
        assertTrue(ex.getMessage().contains("version mismatch"), "message: " + ex.getMessage());
    }
    @Test
    void readMultiterm_rejectsCrcMismatch() throws IOException {
        TriePacker.HashNode root = TriePacker.buildHashTrie(new String[]{"a"});
        Path binFile = tempDir.resolve("multiterm_badcrc.bin");
        DictCompileTestSupport.writeMultitermBin(binFile, TriePacker.pack(root));

        byte[] bytes = java.nio.file.Files.readAllBytes(binFile);
        // Flip a payload byte (byte 8 = first byte after version) without updating CRC
        bytes[8] ^= 0xFF;
        java.nio.file.Files.write(binFile, bytes);

        IOException ex = assertThrows(IOException.class,
                () -> DictReader.readMultiterm(binFile));
        assertTrue(ex.getMessage().contains("crc mismatch"), "message: " + ex.getMessage());
    }
    @Test
    void readSyllable_roundTripsIndexAndCount() throws IOException {
        SyllableTrie written = DictCompileTestSupport.buildSyllableTrie("xin");

        // Locate terminal node for "xin" and assign bigram row index 42
        int node = 0;
        for (int cp : "xin".codePoints().toArray()) node = written.findChild(node, cp);
        written.setIndex(node, 42);

        Path binFile = tempDir.resolve("syllable.bin");
        DictCompileTestSupport.writeSyllableBin(binFile, written, /*syllableCount=*/7);

        SyllableTrie read = DictReader.readSyllable(binFile);

        int rNode = 0;
        for (int cp : "xin".codePoints().toArray()) rNode = read.findChild(rNode, cp);
        assertNotEquals(-1, rNode, "should find 'xin' in round-tripped trie");
        assertEquals(42, read.getIndex(rNode), "bigram row index should survive round-trip");
    }
    @Test
    void readBigram_roundTripsCsr() throws IOException {
        // 2-row CSR: row 0 has {col=1, val=0.5f}, row 1 is empty
        int n = 2;
        int[] rowOffset = {0, 1, 1};
        int[] colIndex  = {1};
        float[] value   = {0.5f};

        Path binFile = tempDir.resolve("bigram.bin");
        DictCompileTestSupport.writeBigramBin(binFile, n, rowOffset, colIndex, value);

        BigramScores scores = DictReader.readBigram(binFile);

        assertEquals(n, scores.rowCount(), "rowCount");
        assertArrayEquals(rowOffset, scores.rowOffsets(), "rowOffset");
        assertArrayEquals(colIndex,  scores.colIndex(),   "colIndex");
        assertArrayEquals(value,     scores.values(),     0.0f, "value");
    }
    // P0#2 — non-monotonic rowOffset must be rejected at load time
    @Test
    void readBigram_rejectsNonMonotonicRowOffset() throws IOException {
        // rowOffset[1]=5 but totalNnz (=rowOffset[2])=3: 5 > 3 violates monotone invariant.
        // Without the validation, BigramScores.getScore(0, j) would do binarySearch on [0..5)
        // when colIndex.length==3, causing AIOOBE at runtime.
        int n = 2;
        int[] rowOffset = {0, 5, 3};  // rowOffset[1]=5 > rowOffset[2]=3
        int[] colIndex  = {0, 1, 2};  // 3 entries (matches totalNnz=rowOffset[2]=3)
        float[] value   = {0.1f, 0.2f, 0.3f};

        Path binFile = tempDir.resolve("bigram_bad_rowoffset.bin");
        DictCompileTestSupport.writeBigramBin(binFile, n, rowOffset, colIndex, value);

        IOException ex = assertThrows(IOException.class,
                () -> DictReader.readBigram(binFile));
        assertTrue(ex.getMessage().contains("rowOffset invariant"),
                "expected 'rowOffset invariant' in message, got: " + ex.getMessage());
    }

    // P0#3 — NaN weights in multiterm.bin must be sanitized to NEGATIVE_INFINITY at load time
    @Test
    void readMultiterm_sanitizesNanWeightToNegativeInfinity() throws IOException {
        // Build a valid trie, corrupt one weight to NaN via the live array reference,
        // write it to disk, then verify DictReader replaces NaN with NEGATIVE_INFINITY.
        TriePacker.HashNode root = TriePacker.buildHashTrie(new String[]{"ab"});
        MultitermTrie trie = TriePacker.pack(root);

        // The live weightArray() allows in-place NaN injection before serialization.
        float[] weights = trie.weightArray();
        boolean injected = false;
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] > 0f) {          // ending-node weight is 1.0f from buildHashTrie
                weights[i] = Float.NaN;
                injected = true;
                break;
            }
        }
        assertTrue(injected, "test setup: failed to find a positive-weight node to corrupt");

        Path binFile = tempDir.resolve("multiterm_nan.bin");
        DictCompileTestSupport.writeMultitermBin(binFile, trie);

        MultitermTrie loaded = DictReader.readMultiterm(binFile);

        for (float w : loaded.weightArray()) {
            assertFalse(Float.isNaN(w),
                    "after loading, no weight should be NaN; expected NEGATIVE_INFINITY in place of NaN");
        }
    }
    // P1#21a — readSyllable must reject a corrupted magic header
    @Test
    void readSyllable_rejectsBadMagic() throws IOException {
        SyllableTrie written = DictCompileTestSupport.buildSyllableTrie("xin");
        Path binFile = tempDir.resolve("syllable_badmagic.bin");
        DictCompileTestSupport.writeSyllableBin(binFile, written, 1);

        byte[] bytes = java.nio.file.Files.readAllBytes(binFile);
        bytes[0] = 'X'; // corrupt first byte of "CCSY"
        java.nio.file.Files.write(binFile, bytes);

        IOException ex = assertThrows(IOException.class,
                () -> DictReader.readSyllable(binFile));
        assertTrue(ex.getMessage().contains("bad magic"), "message: " + ex.getMessage());
    }

    // P1#21b — readBigram must reject a corrupted magic header
    @Test
    void readBigram_rejectsBadMagic() throws IOException {
        int[] rowOffset = {0, 0}; int[] colIndex = {}; float[] value = {};
        Path binFile = tempDir.resolve("bigram_badmagic.bin");
        DictCompileTestSupport.writeBigramBin(binFile, 1, rowOffset, colIndex, value);

        byte[] bytes = java.nio.file.Files.readAllBytes(binFile);
        bytes[0] = 'X'; // corrupt first byte of "CCBG"
        java.nio.file.Files.write(binFile, bytes);

        IOException ex = assertThrows(IOException.class,
                () -> DictReader.readBigram(binFile));
        assertTrue(ex.getMessage().contains("bad magic"), "message: " + ex.getMessage());
    }

    // P1#21c — truncated file (fewer than 12 bytes) must be rejected immediately
    @Test
    void readMultiterm_rejectsTooShortFile() throws IOException {
        Path binFile = tempDir.resolve("multiterm_tooshort.bin");
        java.nio.file.Files.write(binFile, new byte[]{0x43, 0x43, 0x4D}); // 3 bytes: "CCM" (incomplete magic)

        IOException ex = assertThrows(IOException.class,
                () -> DictReader.readMultiterm(binFile));
        assertTrue(ex.getMessage().contains("truncated"), "message: " + ex.getMessage());
    }

}
