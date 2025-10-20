package com.phillippitts.speaktomack.service.reconcile.impl;

import com.phillippitts.speaktomack.config.orchestration.OrchestrationProperties;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.service.reconcile.TranscriptReconciler;
import com.phillippitts.speaktomack.service.stt.EngineResult;

import java.util.Objects;

/**
 * Reconciles two engine results by preferring the configured primary engine.
 *
 * <p>This simple strategy always prefers the primary engine's result unless
 * its text is empty, in which case it falls back to the secondary engine.
 *
 * <p>This strategy is useful when you have a clear preference for one engine
 * based on accuracy, speed, or other factors, but want fallback protection
 * when the primary engine fails or produces no output.
 */
public final class SimplePreferenceReconciler implements TranscriptReconciler {
    private final OrchestrationProperties.PrimaryEngine primary;

    /**
     * Creates a simple preference reconciler.
     *
     * @param primary the preferred engine (VOSK or WHISPER)
     * @throws NullPointerException if primary is null
     */
    public SimplePreferenceReconciler(OrchestrationProperties.PrimaryEngine primary) {
        this.primary = Objects.requireNonNull(primary);
    }

    /**
     * Reconciles two engine results by preferring the primary engine.
     *
     * @param vosk Vosk engine result (may be null if engine failed)
     * @param whisper Whisper engine result (may be null if engine failed)
     * @return reconciled transcription result with "reconciled" engine name
     */
    @Override
    public TranscriptionResult reconcile(EngineResult vosk, EngineResult whisper) {
        EngineResult first = primary == OrchestrationProperties.PrimaryEngine.VOSK ? vosk : whisper;
        EngineResult second = primary == OrchestrationProperties.PrimaryEngine.VOSK ? whisper : vosk;
        EngineResult pick = pickNonEmpty(first, second);
        return TranscriptionResult.of(
                pick == null ? "" : pick.text(),
                pick == null ? 0.0 : pick.confidence(),
                "reconciled"
        );
    }

    /**
     * Picks the first non-empty result, or falls back to second, or null.
     *
     * @param first first choice result
     * @param second second choice result (fallback)
     * @return first if non-empty, otherwise second, otherwise null
     */
    private EngineResult pickNonEmpty(EngineResult first, EngineResult second) {
        if (first != null && first.text() != null && !first.text().isBlank()) {
            return first;
        }
        if (second != null && second.text() != null && !second.text().isBlank()) {
            return second;
        }
        return first != null ? first : second; // both empty or one null -> default to first if not null
    }
}