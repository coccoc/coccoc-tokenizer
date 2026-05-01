package com.coccoc.internal.trie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * M3 trie runtime lookup tests.
 *
 * Hand-crafted trie for words {"a", "ab"}:
 *   Alphabet: {a=0, b=1}
 *   Pool (indices 0-3):
 *     Node 0 root:  base=1,  parent=-1
 *     Node 1 'a':   base=2,  parent=0,  isEnding=true,  weight=0.5
 *     Node 2 vacant: base=0, parent=-1
 *     Node 3 'ab':  base=0,  parent=1,  isEnding=true,  weight=0.8
 *
 * findChild(0,'a')=1, findChild(0,'b')=-1, findChild(1,'b')=3, findChild(1,'a')=-1
 */
class DoubleArrayTrieTest {

    // charMap: 'a' (97)=0, 'b' (98)=1; all others -1
    private static final int[] CHAR_MAP;
    static {
        CHAR_MAP = new int['b' + 1];
        java.util.Arrays.fill(CHAR_MAP, -1);
        CHAR_MAP['a'] = 0;
        CHAR_MAP['b'] = 1;
    }

    private static final int[]   BASE   = {  1,  2,  0,  0 };
    private static final int[]   PARENT = { -1,  0, -1,  1 };
    private static final float[] WEIGHT = {  0f, 0.5f, 0f, 0.8f };
    // bit 0 = isEnding, bit 1 = isSpecial
    private static final byte[]  FLAGS  = {  0,  1,  0,  1 };
    private static final int[]   INDEX  = { -1, -1, -1, -1 };

    private MultitermTrie multiterm;
    private StringSetTrie tlds;

    @BeforeEach
    void setUp() {
        multiterm = new MultitermTrie(CHAR_MAP, BASE, PARENT, WEIGHT, FLAGS);
        // StringSetTrie for {"com", "net"}
        tlds = buildTldTrie();
    }

    // --- DoubleArrayTrie findChild ---

    @Test void findChildRootToA()      { assertEquals(1, multiterm.findChild(0, 'a')); }
    @Test void findChildRootToBMiss()  { assertEquals(-1, multiterm.findChild(0, 'b')); }
    @Test void findChildNodeAToB()     { assertEquals(3, multiterm.findChild(1, 'b')); }
    @Test void findChildNodeAToAMiss() { assertEquals(-1, multiterm.findChild(1, 'a')); }
    @Test void findChildOutOfAlphabet(){ assertEquals(-1, multiterm.findChild(0, 'z')); }
    @Test void findChildAstral()       { assertEquals(-1, multiterm.findChild(0, 0x1F600)); }

    // --- MultitermTrie ---

    @Test void rootNotEnding()       { assertFalse(multiterm.isEnding(0)); }
    @Test void nodeAIsEnding()       { assertTrue(multiterm.isEnding(1)); }
    @Test void nodeAbIsEnding()      { assertTrue(multiterm.isEnding(3)); }
    @Test void weightOfNodeA()       { assertEquals(0.5f, multiterm.getWeight(1), 1e-6f); }
    @Test void weightOfNodeAb()      { assertEquals(0.8f, multiterm.getWeight(3), 1e-6f); }

    // --- StringSetTrie contains ---

    @Test void tldComIsContained()  { assertTrue(tlds.contains(new int[]{'c','o','m'}, 0, 3)); }
    @Test void tldNetIsContained()  { assertTrue(tlds.contains(new int[]{'n','e','t'}, 0, 3)); }
    @Test void tldOrgNotContained() { assertFalse(tlds.contains(new int[]{'o','r','g'}, 0, 3)); }
    @Test void tldCoIsPrefix()      { assertFalse(tlds.contains(new int[]{'c','o'}, 0, 2)); }

    /** Walk "ab" through the MultitermTrie and assert we reach an ending node. */
    @Test void walkFullWord() {
        int node = 0;
        node = multiterm.findChild(node, 'a');
        assertNotEquals(-1, node, "should find 'a' from root");
        node = multiterm.findChild(node, 'b');
        assertNotEquals(-1, node, "should find 'b' from 'a'");
        assertTrue(multiterm.isEnding(node), "end of 'ab' should be an ending node");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Build a StringSetTrie containing {"com", "net"} by constructing arrays directly.
     *
     * Alphabet: c=0, e=1, m=2, n=3, o=4, t=5  (sorted alphabetically)
     * Pool layout (manually computed with base values that avoid collisions):
     *   Node 0 (root):    base=10
     *   Node 10+0=10 'c': base=20, parent=0
     *   Node 10+3=13 'n': base=30, parent=0
     *   Node 20+4=24 'co': base=40, parent=10
     *   Node 30+1=31 'ne': base=40, parent=13   <-- shares base40 (no child collision)
     *   Node 40+2=42 'com': parent=24, isEnding
     *   Node 40+5=45 'net': parent=31, isEnding
     *
     * Pool size = max(45)+1 = 46.
     */
    private static StringSetTrie buildTldTrie() {
        // charMap for lowercase letters c,e,m,n,o,t (only these 6 are needed)
        int[] cm = new int['t' + 1];
        java.util.Arrays.fill(cm, -1);
        cm['c'] = 0; cm['e'] = 1; cm['m'] = 2;
        cm['n'] = 3; cm['o'] = 4; cm['t'] = 5;

        int SZ = 46;
        int[]   base   = new int[SZ];
        int[]   parent = new int[SZ];
        byte[]  ending = new byte[SZ];
        java.util.Arrays.fill(parent, -1);

        base[0]  = 10;
        base[10] = 20;  parent[10] = 0;   // 'c'
        base[13] = 30;  parent[13] = 0;   // 'n'
        base[24] = 40;  parent[24] = 10;  // 'co'
        base[31] = 40;  parent[31] = 13;  // 'ne'
        parent[42] = 24; ending[42] = 1;  // 'com'
        parent[45] = 31; ending[45] = 1;  // 'net'

        return new StringSetTrie(cm, base, parent, ending);
    }
}
