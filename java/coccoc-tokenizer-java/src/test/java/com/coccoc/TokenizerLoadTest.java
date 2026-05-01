package com.coccoc;

import com.coccoc.internal.build.TriePacker;
import com.coccoc.internal.trie.MultitermTrie;
import com.coccoc.tools.DictCompileTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TokenizerLoadTest {

    @TempDir Path tempDir;

    @BeforeEach
    void resetSingleton() {
        TokenizerTestHelper.resetForTesting();
    }

    /** Writes three minimal synthetic .bin files into dir. */
    private void stageBins(Path dir) throws IOException {
        TriePacker.HashNode root = TriePacker.buildHashTrie(new String[]{"a"});
        MultitermTrie mt = TriePacker.pack(root);
        DictCompileTestSupport.writeMultitermBin(dir.resolve("multiterm.bin"), mt);

        DictCompileTestSupport.writeSyllableBin(
            dir.resolve("syllable.bin"),
            DictCompileTestSupport.buildSyllableTrie("a"),
            /*syllableCount=*/1);

        DictCompileTestSupport.writeBigramBin(
            dir.resolve("bigram.bin"),
            /*n=*/1,
            new int[]{0, 0},
            new int[]{},
            new float[]{});
    }

    @Test
    void getInstance_loadsAllThreeBinsFromTempDir() throws IOException {
        stageBins(tempDir);

        Tokenizer tok = Tokenizer.getInstance(tempDir.toString());

        assertNotNull(tok, "getInstance should return a non-null Tokenizer");
        // segment() is implemented (M7b) — verify it returns a non-empty result
        assertFalse(tok.segment("xin chao").isEmpty(),
                "segment() should return tokens for non-empty input");
    }
    @Test
    void getInstance_samePathReturnsSameInstance_differentPathThrows() throws IOException {
        stageBins(tempDir);

        Tokenizer first  = Tokenizer.getInstance(tempDir.toString());
        Tokenizer second = Tokenizer.getInstance(tempDir.toString());
        assertSame(first, second, "same path should return the cached instance");

        assertThrows(IllegalStateException.class,
                () -> Tokenizer.getInstance("/different/path"),
                "different path should throw IllegalStateException");
    }
}
