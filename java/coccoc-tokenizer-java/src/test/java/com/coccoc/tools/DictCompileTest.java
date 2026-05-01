package com.coccoc.tools;

import com.coccoc.internal.io.VarintReader;
import com.coccoc.internal.build.TriePacker;
import com.coccoc.internal.trie.MultitermTrie;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;

/**
 * M4 unit tests for DictCompile subsystems.
 * Tests the three sub-components independently before the full CLI integration.
 */
class DictCompileTest {

    // -----------------------------------------------------------------------
    // 1. VarintReader — 7-bit little-endian varint (buffered_reader.hpp:44-67)
    // -----------------------------------------------------------------------

    @Test void varintSingleByteSeries() throws Exception {
        // Three 1-byte values: 0, 5, 127
        // Encoding: first byte has high bit 0; a byte with high bit 0 after power>0
        // terminates the previous number.
        // Stream: 0x00 0x05 0x7F  — each is a single-byte number.
        // But each subsequent byte terminates the previous number when high bit=0.
        byte[] raw = { 0x00, 0x05, 0x7F };
        VarintReader vr = new VarintReader(new ByteArrayInputStream(raw));
        assertEquals(0,   vr.nextInt());
        assertEquals(5,   vr.nextInt());
        assertEquals(127, vr.nextInt());
    }

    @Test void varintTwoBytesValue200() throws Exception {
        // 200 = 72 + 128 = 0b11001000
        // First byte (bits 0-6): 72 = 0x48 (high bit 0)
        // Continuation byte (bit 7): 1 → 0x80 | 1 = 0x81 (high bit 1)
        // Next number start byte (to terminate): 0x00
        byte[] raw = { 0x48, (byte)0x81, 0x00 };
        VarintReader vr = new VarintReader(new ByteArrayInputStream(raw));
        assertEquals(200, vr.nextInt());
        assertEquals(0,   vr.nextInt());
    }

    @Test void varintSequenceOfDeltaInts() throws Exception {
        // Simulate a typical bigram row: n_pairs=2, delta1=3, val1=50, delta2=5, val2=20
        // Encode 2,3,50,5,20 as varints
        byte[] raw = buildVarintStream(2, 3, 50, 5, 20);
        VarintReader vr = new VarintReader(new ByteArrayInputStream(raw));
        assertEquals(2,  vr.nextInt());
        assertEquals(3,  vr.nextInt());
        assertEquals(50, vr.nextInt());
        assertEquals(5,  vr.nextInt());
        assertEquals(20, vr.nextInt());
    }

    // -----------------------------------------------------------------------
    // 2. TriePacker — HashTrie → DATrie arrays
    // -----------------------------------------------------------------------

    @Test void packTrieAndLookupWords() {
        // Words: "a" (weight=0.5, isEnding), "ab" (weight=0.8, isEnding), "b" (weight=0.3, isEnding)
        TriePacker.HashNode root = TriePacker.buildHashTrie(new String[]{"a","ab","b"});
        MultitermTrie trie = TriePacker.pack(root);

        // Walk 'a'
        int nodeA = trie.findChild(0, 'a');
        assertTrue(nodeA > 0, "root should have child 'a'");
        assertTrue(trie.isEnding(nodeA), "'a' should be an ending node");

        // Walk 'ab'
        int nodeAb = trie.findChild(nodeA, 'b');
        assertTrue(nodeAb > 0, "node 'a' should have child 'b'");
        assertTrue(trie.isEnding(nodeAb), "'ab' should be an ending node");

        // Walk 'b'
        int nodeB = trie.findChild(0, 'b');
        assertTrue(nodeB > 0, "root should have child 'b'");
        assertTrue(trie.isEnding(nodeB), "'b' should be an ending node");

        // 'ba' doesn't exist
        assertEquals(-1, trie.findChild(nodeB, 'a'), "'ba' should not exist");

        // 'c' doesn't exist from root
        assertEquals(-1, trie.findChild(0, 'c'), "'c' should not exist from root");
    }

    // -----------------------------------------------------------------------
    // 3. Weight formula — MultitermHashTrieNode::finalize()
    // -----------------------------------------------------------------------

    @Test void weightFormulaOneWordTerm() {
        // space_count=0 → param[0]=0.38, param[1]=1
        // weight = pow(log2(freq + 3), 0.38) * pow(0+1, 1)
        // For freq=10: weight = pow(log2(13), 0.38) * 1
        double expected = Math.pow(Math.log(13) / Math.log(2), 0.38) * Math.pow(1, 1.0);
        assertEquals(expected, DictCompile.multitermWeight(10, 0), 1e-5);
    }

    @Test void weightFormulaTwoWordTerm() {
        // space_count=1 → param[2]=0.14, param[3]=2.59
        // weight = pow(log2(freq + 3), 0.14) * pow(1+1, 2.59)
        double expected = Math.pow(Math.log(7) / Math.log(2), 0.14) * Math.pow(2, 2.59);
        assertEquals(expected, DictCompile.multitermWeight(4, 1), 1e-5);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private byte[] buildVarintStream(int... values) {
        byte[] buf = new byte[values.length * 5]; // worst case 5 bytes per value
        int pos = 0;
        for (int i = 0; i < values.length; i++) {
            int v = values[i];
            // First byte: low 7 bits, high bit 0
            buf[pos++] = (byte)(v & 0x7F);
            v >>>= 7;
            // Continuation bytes: low 7 bits, high bit 1
            while (v > 0) {
                buf[pos++] = (byte)(0x80 | (v & 0x7F));
                v >>>= 7;
            }
        }
        return java.util.Arrays.copyOf(buf, pos);
    }
}
