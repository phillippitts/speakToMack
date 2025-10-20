package com.phillippitts.speaktomack.service.stt;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for tokenizing text into normalized alpha tokens.
 *
 * <p>Tokenization rules:
 * <ul>
 *   <li>Split on non-alphabetic characters (regex: [^\p{Alpha}]+)</li>
 *   <li>Convert all tokens to lowercase</li>
 *   <li>Filter out blank tokens</li>
 *   <li>Return immutable list</li>
 * </ul>
 *
 * <p>Used by Whisper JSON parsing and parallel STT service for consistent
 * token extraction across reconciliation strategies.
 */
public final class TokenizerUtil {

    private TokenizerUtil() {
        // Prevent instantiation
    }

    /**
     * Tokenizes text into normalized alpha tokens.
     *
     * @param text input text to tokenize (may be null or blank)
     * @return immutable list of lowercase alpha tokens (empty if no valid tokens)
     */
    public static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] parts = text.toLowerCase().split("[^\\p{Alpha}]+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        return List.copyOf(tokens);
    }
}
