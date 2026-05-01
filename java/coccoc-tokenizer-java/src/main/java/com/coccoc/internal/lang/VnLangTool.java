package com.coccoc.internal.lang;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;

/**
 * Port of VnLangTool from tokenizer/auxiliary/vn_lang_tool.hpp.
 *
 * All table indices are BMP codepoints (0..65535). Astral codepoints (≥ 0x10000)
 * are handled by short-circuiting bounds checks — they pass through unchanged,
 * matching the C++ guard "return c < ALPHANUMERIC_SIZE ? table[c] : c".
 *
 * Thread safety: call initSimple() or init(dictPath) once before any tokenization.
 * Both are idempotent via the initialized flag.
 */
public final class VnLangTool {

    public static final int ALPHANUMERIC_SIZE = 1 << 16;

    // Sentinel: table entry not set (matches C++ memset -1 pattern).
    private static final int UNSET = -1;

    // -----------------------------------------------------------------------
    // Vietnamese charset constants — from vn_lang_tool.hpp:32-33
    // -----------------------------------------------------------------------

    private static final String VN_LOWER_CHARSET =
            "áàảãạâấầẩẫậăắằẳẵặéèẻẽẹêếềểễệíìỉĩịóòỏõọôốồổỗộơớờởỡợúùủũụưứừửữựýỳỷỹỵđđ";
    private static final String VN_UPPER_CHARSET =
            "ÁÀẢÃẠÂẤẦẨẪẬĂẮẰẲẴẶÉÈẺẼẸÊẾỀỂỄỆÍÌỈĨỊÓÒỎÕỌÔỐỒỔỖỘƠỚỜỞỠỢÚÙỦŨỤƯỨỪỬỮỰÝỲỶỸỴĐÐ";

    // root_forms[14] — vn_lang_tool.hpp:34-47
    private static final String[] ROOT_FORMS = {
        "aáàảãạâấầẩẫậăắằẳẵặ",
        "eéèẻẽẹêếềểễệ",
        "iíìỉĩị",
        "oóòỏõọôốồổỗộơớờởỡợ",
        "uúùủũụưứừửữự",
        "yýỳỷỹỵ",
        "dđđ",
        "AÁÀẢÃẠÂẤẦẨẪẬĂẮẰẲẴẶ",
        "EÉÈẺẼẸÊẾỀỂỄỆ",
        "IÍÌỈĨỊ",
        "OÓÒỎÕỌÔỐỒỔỖỘƠỚỜỞỠỢ",
        "UÚÙỦŨỤƯỨỪỬỮỰ",
        "YÝỲỶỸỴ",
        "DĐÐ"
    };

    // tone_forms[24] — vn_lang_tool.hpp:49-72
    // Each group: [no-tone, sắc, huyền, hỏi, ngã, nặng]
    private static final String[] TONE_FORMS = {
        "aáàảãạ", "âấầẩẫậ", "ăắằẳẵặ",
        "eéèẻẽẹ", "êếềểễệ",
        "iíìỉĩị",
        "oóòỏõọ", "ôốồổỗộ", "ơớờởỡợ",
        "uúùủũụ", "ưứừửữự",
        "yýỳỷỹỵ",
        "AÁÀẢÃẠ", "ÂẤẦẨẪẬ", "ĂẮẰẲẴẶ",
        "EÉÈẺẼẸ", "ÊẾỀỂỄỆ",
        "IÍÌỈĨỊ",
        "OÓÒỎÕỌ", "ÔỐỒỔỖỘ", "ƠỚỜỞỠỢ",
        "UÚÙỦŨỤ", "ƯỨỪỬỮỰ",
        "YÝỲỶỸỴ"
    };

    // hat_forms[24] — vn_lang_tool.hpp:75-100
    // Each group: [no-hat, circumflex ^, breve ˘, horn ̛]
    private static final String[] HAT_FORMS = {
        "aâăa", "áấắá", "àầằà", "ảẩẳả", "ãẫẵã", "ạậặạ",
        "eêee", "éếéé", "èềèè", "ẻểẻẻ", "ẽễẽẽ", "ẹệẹẹ",
        "oôoơ", "óốóớ", "òồòờ", "ỏổỏở", "õỗõỡ", "ọộọợ",
        "uuuư", "úúúứ", "ùùùừ", "ủủủử", "ũũũữ", "ụụụự",
    };

    // -----------------------------------------------------------------------
    // Tables (package-private so Segmenter can read them directly)
    // -----------------------------------------------------------------------

    static final int[] lowerOf      = new int[ALPHANUMERIC_SIZE];
    static final int[] upperOf      = new int[ALPHANUMERIC_SIZE];
    static final int[] rootOf       = new int[ALPHANUMERIC_SIZE];
    static final int[] lowerRootOf  = new int[ALPHANUMERIC_SIZE];

    // tone_id[c] = group index (0-23) if c is the toneless base of a group; else UNSET
    static final int[] toneId       = new int[ALPHANUMERIC_SIZE];
    // hat_id[c] = group index (0-23) if c is the hatless base of a group; else UNSET
    static final int[] hatId        = new int[ALPHANUMERIC_SIZE];
    // toneFormsId[c] = position index (1-5) if c is a combining tone mark; else UNSET
    static final int[] toneFormsId  = new int[ALPHANUMERIC_SIZE];
    // hatFormsId[c] = position index (1-3) if c is a combining hat mark; else UNSET
    static final int[] hatFormsId   = new int[ALPHANUMERIC_SIZE];

    // toneFormsUtf[group][pos] = resulting codepoint
    static final int[][] toneFormsUtf = new int[24][];
    static final int[][] hatFormsUtf  = new int[24][];

    static final boolean[] inAlphabet     = new boolean[ALPHANUMERIC_SIZE];
    static final boolean[] inNumeric      = new boolean[ALPHANUMERIC_SIZE];
    static final boolean[] inAlphanumeric = new boolean[ALPHANUMERIC_SIZE];

    private static volatile boolean initialized = false;

    private VnLangTool() {}

    // -----------------------------------------------------------------------
    // Public init entry points
    // -----------------------------------------------------------------------

    /**
     * Simple init: uses hard-coded VN charsets and ASCII. No dict files required.
     * Equivalent to C++ init(path, simple_mode=true).
     */
    public static synchronized void initSimple() {
        if (initialized) return;
        initSimpleAlphanumeric();
        initLowerUpper();
        initRootForms();
        initToneForms();
        initHatForms();
        initialized = true;
    }

    /**
     * Full init: reads alphabetic/numeric/d_and_gi/i_and_y dict files from dictPath,
     * then builds tone/hat/root tables.
     * Equivalent to C++ init(path, simple_mode=false).
     */
    public static synchronized void init(String dictPath) throws IOException {
        if (initialized) return;
        initAlphanumericFromFiles(dictPath);
        initLowerUpper();
        initRootForms();
        initToneForms();
        initHatForms();
        initialized = true;
    }

    // -----------------------------------------------------------------------
    // Public utility methods
    // -----------------------------------------------------------------------

    public static int lower(int c) {
        return c < ALPHANUMERIC_SIZE ? lowerOf[c] : c;
    }

    public static int lowerRoot(int c) {
        return c < ALPHANUMERIC_SIZE ? lowerRootOf[c] : c;
    }

    public static boolean isAlphabetic(int c) {
        return c < ALPHANUMERIC_SIZE && inAlphabet[c];
    }

    public static boolean isNumeric(int c) {
        return c < ALPHANUMERIC_SIZE && inNumeric[c];
    }

    public static boolean isAlphanumeric(int c) {
        return c < ALPHANUMERIC_SIZE && inAlphanumeric[c];
    }

    public static boolean isToneHat(int c) {
        return c < ALPHANUMERIC_SIZE && (toneFormsId[c] != UNSET || hatFormsId[c] != UNSET);
    }

    public static boolean canPutToneHat(int c) {
        return c < ALPHANUMERIC_SIZE && (toneId[c] != UNSET || hatId[c] != UNSET);
    }

    /**
     * Attempt to merge a Vietnamese combining mark into the preceding codepoint.
     *
     * @return the merged codepoint, or -1 if no merge is possible.
     *
     * Mirrors C++ merge_tone_hat(uint32_t &prev_char, uint32_t cur_char) which
     * modifies prev_char in place. Java callers use the return value instead:
     *   int merged = VnLangTool.mergeToneHat(prev, cur);
     *   if (merged != -1) prev = merged;
     */
    public static int mergeToneHat(int prevChar, int curChar) {
        if (prevChar >= ALPHANUMERIC_SIZE || curChar >= ALPHANUMERIC_SIZE) return UNSET;
        if (toneId[prevChar] != UNSET && toneFormsId[curChar] != UNSET) {
            return toneFormsUtf[toneId[prevChar]][toneFormsId[curChar]];
        }
        if (hatId[prevChar] != UNSET && hatFormsId[curChar] != UNSET) {
            return hatFormsUtf[hatId[prevChar]][hatFormsId[curChar]];
        }
        return UNSET;
    }

    /**
     * Normalize an NFD codepoint sequence — merge combining tone/hat marks into
     * the preceding vowel, producing a NFC-like result.
     * Mirrors C++ normalize_NFD_UTF(text, remove_duplicate_spaces).
     */
    public static int[] normalizeNfd(int[] cps) {
        return normalizeNfd(cps, false);
    }

    public static int[] normalizeNfd(int[] cps, boolean removeDuplicateSpaces) {
        if (cps.length == 0) return new int[0];
        int[] out = new int[cps.length];
        int len = 0;
        out[len++] = cps[0];
        for (int i = 1; i < cps.length; i++) {
            int prevChar = out[len - 1];
            int curChar  = cps[i];
            int merged   = mergeToneHat(prevChar, curChar);
            if (merged != UNSET) {
                out[len - 1] = merged;
            } else {
                if (removeDuplicateSpaces && curChar == ' ' && out[len - 1] == ' ') continue;
                out[len++] = curChar;
            }
        }
        return len == out.length ? out : Arrays.copyOf(out, len);
    }

    // -----------------------------------------------------------------------
    // Init helpers
    // -----------------------------------------------------------------------

    private static void initSimpleAlphanumeric() {
        for (int i = 0; i <= 9; i++) {
            inNumeric['0' + i]      = true;
            inAlphanumeric['0' + i] = true;
        }
        for (int i = 0; i < 26; i++) {
            inAlphabet['A' + i]     = true;
            inAlphabet['a' + i]     = true;
            inAlphanumeric['A' + i] = true;
            inAlphanumeric['a' + i] = true;
        }
        int[] lowers = VN_LOWER_CHARSET.codePoints().toArray();
        int[] uppers = VN_UPPER_CHARSET.codePoints().toArray();
        for (int i = 0; i < lowers.length; i++) {
            inAlphabet[lowers[i]]     = true;
            inAlphabet[uppers[i]]     = true;
            inAlphanumeric[lowers[i]] = true;
            inAlphanumeric[uppers[i]] = true;
        }
    }

    private static void initAlphanumericFromFiles(String dictPath) throws IOException {
        readLetterFile(dictPath + "/alphabetic", true);
        readLetterFile(dictPath + "/numeric",    false);
    }

    private static void readLetterFile(String path, boolean isAlpha) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            int n = Integer.parseInt(br.readLine().trim());
            for (int i = 0; i < n; i++) {
                String line = br.readLine();
                if (line == null) break;
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 4) continue;
                int upperCp = Integer.parseInt(parts[1]);
                int lowerCp = Integer.parseInt(parts[3]);
                if (Math.max(upperCp, lowerCp) >= ALPHANUMERIC_SIZE) continue;
                if (isAlpha) {
                    inAlphabet[upperCp] = true;
                    inAlphabet[lowerCp] = true;
                } else {
                    inNumeric[upperCp] = true;
                    inNumeric[lowerCp] = true;
                }
                inAlphanumeric[upperCp] = true;
                inAlphanumeric[lowerCp] = true;
                if (upperCp != lowerCp) {
                    upperOf[lowerCp] = upperCp;
                    lowerOf[upperCp] = lowerCp;
                }
            }
        }
    }

    private static void initLowerUpper() {
        // Identity by default
        for (int i = 0; i < ALPHANUMERIC_SIZE; i++) {
            lowerOf[i] = i;
            upperOf[i] = i;
        }
        // ASCII A-Z / a-z
        for (int i = 0; i < 26; i++) {
            lowerOf['A' + i] = 'a' + i;
            upperOf['a' + i] = 'A' + i;
        }
        // VN uppercase ↔ lowercase pairs
        int[] lowers = VN_LOWER_CHARSET.codePoints().toArray();
        int[] uppers = VN_UPPER_CHARSET.codePoints().toArray();
        for (int i = 0; i < lowers.length; i++) {
            lowerOf[uppers[i]] = lowers[i];
            upperOf[lowers[i]] = uppers[i];
        }
    }

    private static void initRootForms() {
        for (int i = 0; i < ALPHANUMERIC_SIZE; i++) {
            rootOf[i]      = i;
            lowerRootOf[i] = lowerOf[i];
        }
        for (String group : ROOT_FORMS) {
            int[] cps = group.codePoints().toArray();
            int root = cps[0];
            for (int cp : cps) {
                rootOf[cp]      = root;
                lowerRootOf[cp] = lowerOf[root];
            }
        }
    }

    private static void initToneForms() {
        Arrays.fill(toneFormsId, UNSET);
        Arrays.fill(toneId, UNSET);
        for (int i = 0; i < TONE_FORMS.length; i++) {
            int[] cps = TONE_FORMS[i].codePoints().toArray();
            toneId[cps[0]] = i;           // Only the toneless base gets a group id
            toneFormsUtf[i] = cps;
        }
        // Combining tone marks → position indices (1-5)
        toneFormsId[0x301] = 1; // U+0301 COMBINING ACUTE ACCENT  (sắc)
        toneFormsId[0x300] = 2; // U+0300 COMBINING GRAVE ACCENT  (huyền)
        toneFormsId[0x309] = 3; // U+0309 COMBINING HOOK ABOVE    (hỏi)
        toneFormsId[0x303] = 4; // U+0303 COMBINING TILDE         (ngã)
        toneFormsId[0x323] = 5; // U+0323 COMBINING DOT BELOW     (nặng)
    }

    private static void initHatForms() {
        Arrays.fill(hatFormsId, UNSET);
        Arrays.fill(hatId, UNSET);
        for (int i = 0; i < HAT_FORMS.length; i++) {
            int[] cps = HAT_FORMS[i].codePoints().toArray();
            hatId[cps[0]] = i;            // Only the hat-less base gets a group id
            hatFormsUtf[i] = cps;
        }
        // Combining hat/modifier marks → position indices (1-3)
        hatFormsId[0x302] = 1; // U+0302 COMBINING CIRCUMFLEX ACCENT (^)
        hatFormsId[0x306] = 2; // U+0306 COMBINING BREVE             (ă group)
        hatFormsId[0x31b] = 3; // U+031B COMBINING HORN              (ơ/ư group)
    }
}
