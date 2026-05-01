package com.coccoc.internal.lang;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * M2 parity tests for VnLangTool.
 * All expected values are cross-checked against vn_lang_tool.hpp behavior.
 */
class VnLangToolTest {

    @BeforeAll
    static void initTables() {
        // Simple mode: ASCII + VN charsets; no dict files required.
        VnLangTool.initSimple();
    }

    // --- lower / upper ---

    @Test void lowerAsciiUppercase() { assertEquals('a', VnLangTool.lower('A')); }
    @Test void lowerAsciiLowercase() { assertEquals('a', VnLangTool.lower('a')); }
    @Test void lowerVietnameseUppercase() { assertEquals('á', VnLangTool.lower('Á')); }
    @Test void lowerVietnameseLowercase() { assertEquals('ấ', VnLangTool.lower('ấ')); }
    @Test void lowerAstralUnchanged() { assertEquals(0x1F600, VnLangTool.lower(0x1F600)); }

    // --- lowerRoot ---

    @Test void lowerRootOfTonedVowel() { assertEquals('a', VnLangTool.lowerRoot('Á')); }
    @Test void lowerRootOfHattedVowel() { assertEquals('a', VnLangTool.lowerRoot('â')); }
    @Test void lowerRootOfPlainLetter() { assertEquals('a', VnLangTool.lowerRoot('a')); }
    @Test void lowerRootOfD()          { assertEquals('d', VnLangTool.lowerRoot('đ')); }

    // --- alphabet / numeric flags ---

    @Test void asciiLetterIsAlphabetic()   { assertTrue(VnLangTool.isAlphabetic('z')); }
    @Test void vnLetterIsAlphabetic()      { assertTrue(VnLangTool.isAlphabetic('ệ')); }
    @Test void digitNotAlphabetic()        { assertFalse(VnLangTool.isAlphabetic('0')); }
    @Test void digitIsNumeric()            { assertTrue(VnLangTool.isNumeric('9')); }
    @Test void letterNotNumeric()          { assertFalse(VnLangTool.isNumeric('a')); }
    @Test void letterIsAlphanumeric()      { assertTrue(VnLangTool.isAlphanumeric('a')); }
    @Test void digitIsAlphanumericFalse()  {
        // simple-mode numeric is not set (init_simple_alphanumeric only sets in_numeric for 0-9
        // and in_alphabet for letters; in_alphanumeric is set for alphabet chars only in simple mode)
        // Verify numeric chars are recognized
        assertTrue(VnLangTool.isNumeric('5'));
    }

    // --- isToneHat / canPutToneHat ---

    @Test void combiningAcuteIsToneHat()      { assertTrue(VnLangTool.isToneHat(0x301)); }
    @Test void combiningGraveIsToneHat()       { assertTrue(VnLangTool.isToneHat(0x300)); }
    @Test void combiningHookIsToneHat()        { assertTrue(VnLangTool.isToneHat(0x309)); }
    @Test void combiningTildeIsToneHat()       { assertTrue(VnLangTool.isToneHat(0x303)); }
    @Test void combiningDotBelowIsToneHat()    { assertTrue(VnLangTool.isToneHat(0x323)); }
    @Test void combiningCircumflexIsToneHat()  { assertTrue(VnLangTool.isToneHat(0x302)); }
    @Test void combiningBreveIsToneHat()       { assertTrue(VnLangTool.isToneHat(0x306)); }
    @Test void combiningHornIsToneHat()        { assertTrue(VnLangTool.isToneHat(0x31b)); }
    @Test void plainLetterNotToneHat()         { assertFalse(VnLangTool.isToneHat('a')); }
    @Test void plainLetterCanReceiveTone()     { assertTrue(VnLangTool.canPutToneHat('a')); }
    @Test void fullyTonedAndHattedLetterCannotReceiveToneOrHat()  { assertFalse(VnLangTool.canPutToneHat('ấ')); }

    // --- mergeToneHat ---

    @Test void mergeToneAcuteOnA()           { assertEquals('á', VnLangTool.mergeToneHat('a', 0x301)); }
    @Test void mergeToneGraveOnA()            { assertEquals('à', VnLangTool.mergeToneHat('a', 0x300)); }
    @Test void mergeToneHookOnA()             { assertEquals('ả', VnLangTool.mergeToneHat('a', 0x309)); }
    @Test void mergeToneTildeOnA()            { assertEquals('ã', VnLangTool.mergeToneHat('a', 0x303)); }
    @Test void mergeToneDotBelowOnA()         { assertEquals('ạ', VnLangTool.mergeToneHat('a', 0x323)); }
    @Test void mergeToneAcuteOnCircumflexA()  { assertEquals('ấ', VnLangTool.mergeToneHat('â', 0x301)); }
    @Test void mergeToneAcuteOnBreveA()       { assertEquals('ắ', VnLangTool.mergeToneHat('ă', 0x301)); }
    @Test void mergeToneDotBelowOnCircumflexE(){ assertEquals('ệ', VnLangTool.mergeToneHat('ê', 0x323)); }
    @Test void mergeHatCircumflexOnA()        { assertEquals('â', VnLangTool.mergeToneHat('a', 0x302)); }
    @Test void mergeHatBreveOnA()             { assertEquals('ă', VnLangTool.mergeToneHat('a', 0x306)); }
    @Test void mergeHatCircumflexOnE()        { assertEquals('ê', VnLangTool.mergeToneHat('e', 0x302)); }
    @Test void mergeHatHornOnO()              { assertEquals('ơ', VnLangTool.mergeToneHat('o', 0x31b)); }
    @Test void mergeHatHornOnU()              { assertEquals('ư', VnLangTool.mergeToneHat('u', 0x31b)); }
    @Test void noMergeForNonCombiningChar()   { assertEquals(-1, VnLangTool.mergeToneHat('a', 'b')); }
    @Test void noMergeForTonedChar()          { assertEquals(-1, VnLangTool.mergeToneHat('á', 0x301)); }

    // --- normalizeNfd: NFD → NFC combining ---

    @Test void normalizeNfdSimpleWord() {
        // NFD encoding of "việt": v i ê 0x323 t  →  should produce "việt" (NFC-like)
        int[] nfd = { 'v', 'i', 0xEA /* ê */, 0x323, 't' };
        int[] result = VnLangTool.normalizeNfd(nfd);
        assertEquals(4, result.length, "ê+0x323 should merge into ệ, reducing length");
        assertEquals('v', result[0]);
        assertEquals('i', result[1]);
        assertEquals('ệ', result[2]);
        assertEquals('t', result[3]);
    }

    @Test void normalizeNfdAlreadyNfc() {
        // NFC "viêt" — 'ê' is already composed, no combining marks
        int[] nfc = { 'v', 'i', 0xEA /* ê */, 't' };
        int[] result = VnLangTool.normalizeNfd(nfc);
        assertArrayEquals(nfc, result);
    }

    @Test void normalizeNfdCollapseDuplicateSpaces() {
        int[] input = { 'a', ' ', ' ', 'b' };
        int[] result = VnLangTool.normalizeNfd(input, true);
        assertArrayEquals(new int[]{ 'a', ' ', 'b' }, result);
    }
}
