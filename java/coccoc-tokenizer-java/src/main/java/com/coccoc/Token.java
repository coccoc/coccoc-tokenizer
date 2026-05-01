package com.coccoc;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable token produced by {@link Tokenizer#segment}.
 *
 * Type and SegType ordinals must match token.hpp lines 11-20 so that any
 * future JNI bridge or serialization layer sees the same integer values.
 */
public final class Token implements Cloneable {

    public static final Token FULL_STOP = new Token(".", Type.PUNCT, SegType.END_SEG_TYPE, -1, -1);
    public static final Token COMMA     = new Token(",", Type.PUNCT, SegType.END_SEG_TYPE, -1, -1);
    public static final Token SPACE     = new Token(" ", Type.SPACE, null, -1, -1);

    public enum Type {
        WORD,
        NUMBER,
        SPACE,
        PUNCT,
        WHOLE_URL,
        SITE_URL;

        private static final Type[] VALUES = values();

        public static Type fromInt(int i) {
            return VALUES[i];
        }
    }

    public enum SegType {
        OTHER_SEG_TYPE,
        SKIP_SEG_TYPE,
        URL_SEG_TYPE,
        END_URL_TYPE,
        END_SEG_TYPE;

        private static final SegType[] VALUES = values();

        public static SegType fromInt(int i) {
            return VALUES[i];
        }
    }

    private final String text;
    private final Type type;
    private SegType segType;
    private boolean splittedByDot;
    private final int startPos;
    private final int endPos;

    public Token(String text, int start, int end) {
        this(text, Type.WORD, null, start, end);
    }

    public Token(String text, Type type, int start, int end) {
        this(text, type, null, start, end);
    }

    public Token(String text, Type type, SegType segType, int start, int end) {
        this(text, type, segType, false, start, end);
    }

    public Token(String text, Type type, SegType segType, boolean splittedByDot, int start, int end) {
        this.text = text;
        this.type = type;
        this.segType = segType;
        this.splittedByDot = splittedByDot;
        this.startPos = start;
        this.endPos = end > 0 ? end : (start >= 0 ? start + text.length() : start);
    }

    public String getText() { return text; }
    public Type getType() { return type; }
    public int getPos() { return startPos; }
    public int getEndPos() { return endPos; }
    public SegType getSegType() { return segType; }
    public boolean isSplittedByDot() { return splittedByDot; }

    public boolean isWord()      { return type == Type.WORD; }
    public boolean isPunct()     { return type == Type.PUNCT; }
    public boolean isNumber()    { return type == Type.NUMBER; }
    public boolean isWholeUrl()  { return type == Type.WHOLE_URL; }
    public boolean isSiteUrl()   { return type == Type.SITE_URL; }
    public boolean isSpace()     { return type == Type.SPACE; }
    public boolean isWordOrNumber() { return isWord() || isNumber() || isSiteUrl(); }

    public boolean isEndSeg()    { return segType == SegType.END_SEG_TYPE; }
    public boolean isUrlSeg()    { return segType == SegType.URL_SEG_TYPE; }
    public boolean isEndUrlSeg() { return segType == SegType.END_URL_TYPE; }
    public boolean isSkipSeg()   { return segType == SegType.SKIP_SEG_TYPE; }
    public boolean isOtherSeg()  { return segType == SegType.OTHER_SEG_TYPE; }

    public void setEndSeg()    { segType = SegType.END_SEG_TYPE; }
    public void setOtherSeg()  { segType = SegType.OTHER_SEG_TYPE; }
    public void setEndUrlSeg() { segType = SegType.END_URL_TYPE; }
    public void setUrlSeg()    { segType = SegType.URL_SEG_TYPE; }
    public void setSkipSeg()   { segType = SegType.SKIP_SEG_TYPE; }

    public Token cloneWithNewText(String newText, int newEnd) {
        return new Token(newText, type, segType, splittedByDot, startPos, newEnd);
    }

    public static ArrayList<String> toStringList(List<Token> tokens) {
        ArrayList<String> out = new ArrayList<>(tokens.size());
        for (Token t : tokens) out.add(t.getText());
        return out;
    }

    @Override
    public Token clone() {
        return new Token(text, type, segType, splittedByDot, startPos, endPos);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Token that)) return false;
        return text.equals(that.text) && type == that.type;
    }

    @Override
    public int hashCode() {
        return text.hashCode() ^ type.hashCode();
    }

    @Override
    public String toString() {
        return type + " `" + text + "` " + startPos + '-' + endPos;
    }
}
