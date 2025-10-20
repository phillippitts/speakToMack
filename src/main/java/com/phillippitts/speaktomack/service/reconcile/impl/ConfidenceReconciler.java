package com.phillippitts.speaktomack.service.reconcile.impl;

import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.service.reconcile.TranscriptReconciler;
import com.phillippitts.speaktomack.service.stt.EngineResult;

/**
 * Reconciles two engine results by selecting the one with higher confidence.
 *
 * <p>Tie-breaking strategy: If confidences are equal, prefers non-empty text.
 * If both are empty or both non-empty, defaults to Vosk result.
 *
 * <p>This strategy is optimal when both engines are equally accurate but
 * their confidence scores reliably indicate transcription quality.
 */
public final class ConfidenceReconciler implements TranscriptReconciler {

    /**
     * Reconciles two engine results by selecting the one with higher confidence.
     *
     * @param vosk Vosk engine result (may be null if engine failed)
     * @param whisper Whisper engine result (may be null if engine failed)
     * @return reconciled transcription result with "reconciled" engine name
     */
    @Override
    public TranscriptionResult reconcile(EngineResult vosk, EngineResult whisper) {
        // Handle null cases early
        if (vosk == null) {
            return toResult(whisper);
        }
        if (whisper == null) {
            return toResult(vosk);
        }

        // Pick by confidence
        if (vosk.confidence() > whisper.confidence()) {
            return toResult(vosk);
        }
        if (whisper.confidence() > vosk.confidence()) {
            return toResult(whisper);
        }

        // Tie-breaker: prefer non-empty text
        boolean voskNonEmpty = isNonEmpty(vosk.text());
        boolean whisperNonEmpty = isNonEmpty(whisper.text());

        if (voskNonEmpty && !whisperNonEmpty) {
            return toResult(vosk);
        }
        if (whisperNonEmpty && !voskNonEmpty) {
            return toResult(whisper);
        }

        // Both empty or both non-empty: default to Vosk
        return toResult(vosk);
    }

    /**
     * Converts an engine result to a transcription result.
     *
     * @param result engine result (may be null)
     * @return transcription result with "reconciled" engine name
     */
    private TranscriptionResult toResult(EngineResult result) {
        return TranscriptionResult.of(
                result == null ? "" : result.text(),
                result == null ? 0.0 : result.confidence(),
                "reconciled"
        );
    }

    /**
     * Checks if text is non-empty (not null and not blank).
     *
     * @param text text to check
     * @return true if text is non-empty
     */
    private boolean isNonEmpty(String text) {
        return text != null && !text.isBlank();
    }
}