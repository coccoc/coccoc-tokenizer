package com.coccoc;

import java.util.*;
import java.io.*;

public class Tokenizer {
	public static final int TOKENIZE_NORMAL = 0;
	public static final int TOKENIZE_HOST = 1;
	public static final int TOKENIZE_URL = 2;
	public static final String dictPath = "/usr/share/tokenizer/dicts"; // TODO: don't hardcode this value

	public native long segmentPointer(String text, boolean for_transforming, int tokenizeOption);
	private native void freeMemory(long resPointer);
	private native int initialize(String dictPath);

	static {
		System.loadLibrary("coccoc_tokenizer_jni");
	}

	private static final class Loader {
		private static final Tokenizer INSTANCE = get();

		private static Tokenizer get() {
			Tokenizer instance = new Tokenizer(dictPath);
			return instance;
		}
	}

	public static Tokenizer getInstance() {
		return Loader.INSTANCE;
	}

	private Tokenizer(String dictPath) {
		int status = initialize(dictPath);
		if (0 > status) {
			throw new RuntimeException("Cannot initialize Tokenizer");
		}
	}

	public ArrayList<Token> segment(String text, boolean for_transforming, int tokenizeOption) {
		if (text == null) {
			throw new IllegalArgumentException("text is null");
		}
		long resPointer = segmentPointer(text, for_transforming, tokenizeOption);

		ArrayList<Token> res = new ArrayList<>();
		// Positions from JNI implementation .cpp file
		long normalizedStringPointer = Unsafe.UNSAFE.getLong(resPointer + 8);
		int rangesSize = (int) Unsafe.UNSAFE.getLong(resPointer + 8 * 2);
		long rangesDataPointer = Unsafe.UNSAFE.getLong(resPointer + 8 * 3);

		int spacePositionsSize = (int) Unsafe.UNSAFE.getLong(resPointer + 8 * 5);
		long spacePositionsDataPointer = Unsafe.UNSAFE.getLong(resPointer + 8 * 6);
		int[] spacePositions = new int[spacePositionsSize + 1];
		for (int i = 0; i < spacePositionsSize; ++i) {
			spacePositions[i] = Unsafe.UNSAFE.getInt(spacePositionsDataPointer + i * 4);
		}
		spacePositions[spacePositionsSize] = -1;

		int TOKEN_SIZE = 4 * 6;
		for (int i = 0, spacePos = 0; i < rangesSize; ++i) {
			// Positions of UNSAFE values are calculated from {struct Token} in tokenizer.hpp
			int startPos = Unsafe.UNSAFE.getInt(rangesDataPointer + i * TOKEN_SIZE);
			int endPos = Unsafe.UNSAFE.getInt(rangesDataPointer + i * TOKEN_SIZE + 4);
			int originalStartPos = Unsafe.UNSAFE.getInt(rangesDataPointer + i * TOKEN_SIZE + 8);
			int originalEndPos = Unsafe.UNSAFE.getInt(rangesDataPointer + i * TOKEN_SIZE + 12);
			int type = Unsafe.UNSAFE.getInt(rangesDataPointer + i * TOKEN_SIZE + 16);
			int segType = Unsafe.UNSAFE.getInt(rangesDataPointer + i * TOKEN_SIZE + 20);

			// Build substring from UNSAFE array of codepoints
			// TODO: Is there a faster way than using StringBuilder?
			StringBuilder sb = new StringBuilder();
			for (int j = startPos; j < endPos; ++j) {
				if (j == spacePositions[spacePos]) {
					sb.append(for_transforming ? '_' : ' ');
					spacePos++;
				}
				sb.appendCodePoint(Unsafe.UNSAFE.getInt(normalizedStringPointer + j * 4));
			}
			res.add(new Token(segType == 1 ? sb.toString().replace(',', '.') : sb.toString(),
					Token.Type.fromInt(type), Token.SegType.fromInt(segType), originalStartPos, originalEndPos));
		}
		if (for_transforming && tokenizeOption == TOKENIZE_NORMAL) {
			res.add(Token.FULL_STOP);
		}
		freeMemory(resPointer);
		return res;
	}

	public ArrayList<Token> segment(String text, int tokenizeOption) {
		return segment(text, false, tokenizeOption);
	}

	public ArrayList<Token> segment(String text, boolean for_transforming) {
		return segment(text, for_transforming, Tokenizer.TOKENIZE_NORMAL);
	}

	public ArrayList<Token> segment(String text) {
		return segment(text, false);
	}

	public ArrayList<String> segmentToStringList(String text) {
		return Token.toStringList(segment(text, false));
	}

	public ArrayList<Token> segmentUrl(String text) {
		return segment(text, false, TOKENIZE_URL);
	}

	public ArrayList<String> segmentUrlToStringList(String text) {
		return Token.toStringList(segment(text, false, TOKENIZE_URL));
	}

	public ArrayList<Token> segment4Transforming(String text) {
		return segment(text, true, TOKENIZE_NORMAL);
	}

	public ArrayList<Token> segment4Transforming(String text, int tokenizeOption) {
		return segment(text, true, tokenizeOption);
	}

	public static void main(String[] args) {
		for (String text : args) {
			for (Token it : getInstance().segment(text)) {
				System.out.print(it.getText() + "\t");
			}
			System.out.println();
		}
	}
}
