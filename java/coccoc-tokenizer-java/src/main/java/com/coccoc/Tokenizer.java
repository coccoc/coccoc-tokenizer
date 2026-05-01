package com.coccoc;

import com.coccoc.internal.bigram.BigramScores;
import com.coccoc.internal.io.DictReader;
import com.coccoc.internal.trie.MultitermTrie;
import com.coccoc.internal.segment.Segmenter;
import com.coccoc.internal.lang.VnLangTool;
import com.coccoc.internal.trie.SyllableTrie;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Public facade for the CocCoc Vietnamese tokenizer.
 *
 * API is binary-compatible with the vendored Tokenizer in
 * elasticsearch-analysis-vietnamese so the plugin can swap to this Maven
 * artifact without changing any call sites.
 *
 * Lifecycle: singleton per dict-path, lazily initialized via getInstance().
 */
public class Tokenizer {

    // Integer constants kept for source-level back-compat with old callers.
    public static final int TOKENIZE_NORMAL = 0;
    public static final int TOKENIZE_HOST   = 1;
    public static final int TOKENIZE_URL    = 2;

    private static final String CLASSPATH_DICTS     = "com/coccoc/dicts";
    // Sentinel used as initializedDictPath when loaded from classpath.
    private static final String CLASSPATH_DICT_PATH = "classpath:" + CLASSPATH_DICTS;

    // Both fields are only ever read/written inside synchronized getInstance() methods.
    private static Tokenizer instance;
    private static String initializedDictPath;

    private final MultitermTrie multitermTrie;
    private final SyllableTrie  syllableTrie;
    // Nullable: bigram.bin may not be bundled in the classpath dicts JAR.
    private final BigramScores  bigramScores;
    private final String        dictPath;
    private final Segmenter     segmenter;

    // -----------------------------------------------------------------------
    // Singleton factories
    // -----------------------------------------------------------------------

    /**
     * Load dict files from the bundled classpath resources under
     * {@code com/coccoc/dicts/} (packaged in {@code coccoc-tokenizer-java-dicts}).
     * bigram.bin is optional — absent if the dicts JAR omits it.
     */
    public static synchronized Tokenizer getInstance() throws IOException {
        if (instance == null) {
            instance = new Tokenizer();
            initializedDictPath = CLASSPATH_DICT_PATH;
        } else if (!CLASSPATH_DICT_PATH.equals(initializedDictPath)) {
            throw new IllegalStateException(
                    "Tokenizer already initialized with dictPath=" + initializedDictPath);
        }
        return instance;
    }

    /**
     * Load dict files from the given filesystem directory.
     * bigram.bin is optional — loaded only if the file exists in the directory.
     */
    public static synchronized Tokenizer getInstance(String dictPath) throws IOException {
        if (instance == null) {
            instance = new Tokenizer(dictPath);
            initializedDictPath = dictPath;
        } else if (!initializedDictPath.equals(dictPath)) {
            throw new IllegalStateException(
                    "Tokenizer already initialized with dictPath=" + initializedDictPath);
        }
        return instance;
    }

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /** Load from classpath resources (called by no-arg getInstance()). */
    private Tokenizer() throws IOException {
        ClassLoader cl = Tokenizer.class.getClassLoader();
        try (InputStream mt = requireResource(cl, "multiterm.bin");
             InputStream sy = requireResource(cl, "syllable.bin")) {
            this.multitermTrie = DictReader.readMultiterm(mt, "multiterm.bin");
            this.syllableTrie  = DictReader.readSyllable(sy,  "syllable.bin");
        }
        try (InputStream bigramIn = cl.getResourceAsStream(CLASSPATH_DICTS + "/bigram.bin")) {
            this.bigramScores = bigramIn != null ? DictReader.readBigram(bigramIn, "bigram.bin") : null;
        }
        VnLangTool.initSimple();
        this.segmenter    = new Segmenter(this.multitermTrie);
        this.dictPath     = CLASSPATH_DICT_PATH;
    }

    /** Load from a filesystem directory (called by getInstance(String)). */
    Tokenizer(String dictPath) throws IOException {
        Path dir = Path.of(dictPath);
        this.multitermTrie = DictReader.readMultiterm(dir.resolve("multiterm.bin"));
        this.syllableTrie  = DictReader.readSyllable(dir.resolve("syllable.bin"));
        Path bigramPath = dir.resolve("bigram.bin");
        this.bigramScores  = Files.exists(bigramPath) ? DictReader.readBigram(bigramPath) : null;
        this.dictPath      = dictPath;
        VnLangTool.initSimple();
        this.segmenter     = new Segmenter(this.multitermTrie);
    }

    private static InputStream requireResource(ClassLoader cl, String name) throws IOException {
        InputStream in = cl.getResourceAsStream(CLASSPATH_DICTS + "/" + name);
        if (in == null)
            throw new IOException("missing classpath resource: " + CLASSPATH_DICTS + "/" + name);
        return in;
    }

    // -----------------------------------------------------------------------
    // Primary segment API (enum-based — used by the ES plugin)
    // -----------------------------------------------------------------------

    public List<Token> segment(String text, TokenizeOption option, boolean keepPunctuation) {
        return segmenter.segment(text, option, keepPunctuation);
    }

    // -----------------------------------------------------------------------
    // Convenience overloads (int-based — back-compat with original module)
    // -----------------------------------------------------------------------

    public ArrayList<Token> segment(String text, boolean forTransforming, int tokenizeOption, boolean keepPuncts) {
        return new ArrayList<>(segmenter.segment(text, TokenizeOption.values()[tokenizeOption], keepPuncts));
    }

    public ArrayList<Token> segment(String text, boolean forTransforming, int tokenizeOption) {
        return segment(text, forTransforming, tokenizeOption, forTransforming);
    }

    public ArrayList<Token> segment(String text, int tokenizeOption) {
        return segment(text, false, tokenizeOption);
    }

    public ArrayList<Token> segment(String text, boolean forTransforming) {
        return segment(text, forTransforming, TOKENIZE_NORMAL);
    }

    public ArrayList<Token> segment(String text) {
        return segment(text, false);
    }

    public ArrayList<String> segmentToStringList(String text) {
        return Token.toStringList(segment(text, false));
    }

    public ArrayList<Token> segmentKeepPuncts(String text) {
        return segment(text, false, TOKENIZE_NORMAL, true);
    }

    public ArrayList<String> segmentKeepPunctsToStringList(String text) {
        return Token.toStringList(segmentKeepPuncts(text));
    }

    public ArrayList<Token> segmentUrl(String text) {
        return segment(text, false, TOKENIZE_URL);
    }

    public ArrayList<String> segmentUrlToStringList(String text) {
        return Token.toStringList(segmentUrl(text));
    }

    public ArrayList<Token> segment4Transforming(String text) {
        return segment(text, true, TOKENIZE_NORMAL);
    }

    public ArrayList<Token> segment4Transforming(String text, int tokenizeOption) {
        return segment(text, true, tokenizeOption);
    }
}
