/**
 * Copyright (c) 2015, JULIE Lab.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the GNU Affero General Public License (LGPL) v3.0
 */

package de.julielab.jcore.ae.lingpipegazetteer.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import com.aliasi.tokenizer.Tokenizer;
import com.aliasi.tokenizer.TokenizerFactory;

public class StringNormalizerForChunking {

	public enum Mode {
		/** Punctuation characters are deleted completely, shrinking the string. */
		DELETE, /** Punctuation characters are replaced by white spaces. */
		REPLACE
	}

	private static Set<Character> charsToDelete = new HashSet<>();
	static {
		charsToDelete.add('-');
		charsToDelete.add('+');
		charsToDelete.add(',');
		charsToDelete.add('.');
		charsToDelete.add(':');
		charsToDelete.add(';');
		charsToDelete.add('?');
		charsToDelete.add('!');
		charsToDelete.add('*');
		charsToDelete.add('§');
		charsToDelete.add('$');
		charsToDelete.add('%');
		charsToDelete.add('&');
		charsToDelete.add('/');
		charsToDelete.add('\\');
		charsToDelete.add('(');
		charsToDelete.add(')');
		charsToDelete.add('<');
		charsToDelete.add('>');
		charsToDelete.add('[');
		charsToDelete.add(']');
		charsToDelete.add('=');
		charsToDelete.add('\'');
		charsToDelete.add('`');
		charsToDelete.add('´');
		charsToDelete.add('"');
		charsToDelete.add('#');
	}

	public static class NormalizedString {
		public String string;
		public Map<Integer, Integer> offsetMap;

		public Integer getOriginalOffset(int newOffset) {
			return offsetMap.get(newOffset);
		}
	}

	/**
	 * This method was meant for text normalization by just deleting punctuation
	 * characters. However, the approach turned out to be suboptimal in cases
	 * where a dictionary entry would be "SHP-1" and the text form would be
	 * "SHP 1". That is, when in the text there is just a whitespace where there
	 * is a punctuation character in the dictionary, we won't recognize the
	 * dictionary entry. Thus, a different normalization was developed, namely
	 * in the other normalization method. It is supposed to be used together
	 * with an approximate chunker.
	 * 
	 * @param str
	 * @return
	 */
	public static NormalizedString normalizeString(String str) {
		NormalizedString ns = new NormalizedString();
		ns.offsetMap = new HashMap<>();
		StringBuilder sb = new StringBuilder();
		int deletedChars = 0;

		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			if (charsToDelete.contains(c)) {
				deletedChars++;
				// switch (mode) {
				// case REPLACE: sb.append(" "); break;
				// case DELETE: deletedChars++; break;
				// }
			} else {
				sb.append(c);
			}
			int newOffset = Math.max(0, i - deletedChars);
			if (null == ns.offsetMap.get(newOffset))
				ns.offsetMap.put(newOffset, i);
		}
		ns.string = sb.toString();
		return ns;
	}

	/**
	 * This normalization method uses a porter stemmer and additionally removes
	 * possessive 's constructions. Dashes and other punctuation is left
	 * untouched. By using an approximate chunker, one can also handle
	 * punctuation.
	 * 
	 * @param str
	 * @param tokenizerFactory
	 * @return
	 */
	public static NormalizedString normalizeString(String str,
			TokenizerFactory tokenizerFactory) {
		//	boolean stemming = tokenizerFactory instanceof PorterStemmerTokenizerFactory;

		NormalizedString ns = new NormalizedString();
		ns.offsetMap = new HashMap<>();

		char[] strChars = str.toCharArray();
		Tokenizer tokenizer = tokenizerFactory.tokenizer(strChars, 0,
				strChars.length);
		StringBuilder sb = new StringBuilder();
		Stack<String> tokenS = new Stack<>();
		Map<Integer, Integer> deleteCandidateOffsetMap = new HashMap<>();
		// According to the lingpipe API documentation, one starts with the next whitespace.
		sb.append(tokenizer.nextWhitespace());
		ns.offsetMap.put(0, 0);
		String token;
		while ((token = tokenizer.nextToken()) != null) {
			// Handle possessive 's (like Parkinson's). It will be deleted. In case we have accidentally deleted some
			// tokens, those are stored in the stack and their offsets are stored, too. In case it was an error, the
			// tokens are later added again in the "else" path.
			if (token.equals("'")) {
				int newStartOffset = sb.length() + sumOfStack(tokenS);
				int newEndOffset = sb.length() + sumOfStack(tokenS)
						+ token.length();
				deleteCandidateOffsetMap.put(newStartOffset,
						tokenizer.lastTokenStartPosition());
				deleteCandidateOffsetMap.put(newEndOffset,
						tokenizer.lastTokenEndPosition());
				tokenS.push(token + tokenizer.nextWhitespace());
			} else if (token.equals("s") && tokenS.size() == 1) {
				int newStartOffset = sb.length() + sumOfStack(tokenS);
				int newEndOffset = sb.length() + sumOfStack(tokenS)
						+ token.length();
				deleteCandidateOffsetMap.put(newStartOffset,
						tokenizer.lastTokenStartPosition());
				deleteCandidateOffsetMap.put(newEndOffset,
						tokenizer.lastTokenEndPosition());
				tokenS.push(token);
				String ws = tokenizer.nextWhitespace();
				if (ws.length() > 0) {
					sb.append(ws);
					tokenS.clear();
					deleteCandidateOffsetMap.clear();
				}
			} else {
				if (!tokenS.isEmpty()) {
					for (int i = 0; i < tokenS.size(); i++)
						sb.append(tokenS.get(i));
					tokenS.clear();
					ns.offsetMap.putAll(deleteCandidateOffsetMap);
					deleteCandidateOffsetMap.clear();
				}
				// plural s, only when no stemming is done
				//				if (!stemming && token.endsWith("s"))
				//					token = token.substring(0, token.length() - 1);
				sb.append(token);
				int newStartOffset = sb.length() - token.length();
				int newEndOffset = sb.length();
				ns.offsetMap.put(newStartOffset,
						tokenizer.lastTokenStartPosition());
				ns.offsetMap
						.put(newEndOffset, tokenizer.lastTokenEndPosition());
				sb.append(tokenizer.nextWhitespace());
			}
		}
		ns.string = sb.toString();
		return ns;
	}

	private static int sumOfStack(Stack<String> stack) {
		int sum = 0;
		for (String i : stack)
			sum += i.length();
		return sum;
	}
}
