package com.phillippitts.speaktomack.service.reconcile.impl;

import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.service.reconcile.AbstractReconciler;
import com.phillippitts.speaktomack.service.stt.EngineResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reconciles two engine results using Jaccard word-overlap similarity.
 *
 * <p>Chooses the text whose tokens have higher Jaccard similarity to the union.
 * If both results have low overlap (< threshold), falls back to selecting the longer text.
 *
 * <p>Jaccard similarity = |A ∩ B| / |A ∪ B|
 *
 * <p>This strategy is useful when both engines may produce slightly different but
 * semantically similar transcriptions, and you want to prefer the one with better
 * coverage of the shared vocabulary.
 */
public final class WordOverlapReconciler extends AbstractReconciler {
    private final double threshold;

    /**
     * Creates a word overlap reconciler with the given threshold.
     *
     * @param threshold minimum Jaccard similarity threshold (0.0 to 1.0)
     * @throws IllegalArgumentException if threshold is not in [0,1]
     */
    public WordOverlapReconciler(double threshold) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("threshold in [0,1]");
        }
        this.threshold = threshold;
    }

    /**
     * Reconciles two non-null engine results by comparing word overlap.
     *
     * <p>Null handling is performed by {@link AbstractReconciler}.
     *
     * @param vosk Vosk engine result (never null)
     * @param whisper Whisper engine result (never null)
     * @return reconciled transcription result with "reconciled" engine name
     */
    @Override
    protected TranscriptionResult doReconcile(EngineResult vosk, EngineResult whisper) {
        // Calculate Jaccard similarity for each result
        Set<String> union = union(vosk.tokens(), whisper.tokens());
        double voskSimilarity = jaccard(vosk.tokens(), union);
        double whisperSimilarity = jaccard(whisper.tokens(), union);

        // Pick result with higher similarity, or fall back to longer text if both are below threshold
        EngineResult pick;
        if (Math.max(voskSimilarity, whisperSimilarity) < threshold) {
            // Both have low overlap: pick the longer non-empty text as a fallback
            pick = (len(vosk.text()) >= len(whisper.text())) ? vosk : whisper;
        } else {
            pick = voskSimilarity >= whisperSimilarity ? vosk : whisper;
        }

        return toResult(pick);
    }

    /**
     * Returns the length of a string, or 0 if null.
     *
     * @param s string to measure
     * @return length of string, or 0 if null
     */
    private static int len(String s) {
        return s == null ? 0 : s.length();
    }

    /**
     * Creates the union of two token lists as a set.
     *
     * @param tokens1 first token list
     * @param tokens2 second token list
     * @return set containing all unique tokens from both lists
     */
    private static Set<String> union(List<String> tokens1, List<String> tokens2) {
        Set<String> result = new HashSet<>();
        if (tokens1 != null) {
            result.addAll(tokens1);
        }
        if (tokens2 != null) {
            result.addAll(tokens2);
        }
        return result;
    }

    /**
     * Calculates Jaccard similarity between token list and union set.
     *
     * <p>Optimized using set intersection for O(n) performance.
     *
     * @param tokens token list to measure
     * @param union union of all tokens
     * @return Jaccard similarity (0.0 to 1.0)
     */
    private static double jaccard(List<String> tokens, Set<String> union) {
        if (union.isEmpty()) {
            return 0.0;
        }
        if (tokens == null || tokens.isEmpty()) {
            return 0.0;
        }

        // Use set intersection for efficient calculation
        Set<String> tokenSet = new HashSet<>(tokens);
        tokenSet.retainAll(union); // Set intersection: O(n)
        return tokenSet.size() / (double) union.size();
    }
}