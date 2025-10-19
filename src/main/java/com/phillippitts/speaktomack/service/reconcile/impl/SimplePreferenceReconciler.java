package com.phillippitts.speaktomack.service.reconcile.impl;

import com.phillippitts.speaktomack.config.orchestration.OrchestrationProperties;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.service.reconcile.TranscriptReconciler;
import com.phillippitts.speaktomack.service.stt.EngineResult;

import java.util.Objects;

/**
 * Prefers primary engine result unless its text is empty, then choose the other.
 */
public final class SimplePreferenceReconciler implements TranscriptReconciler {
    private final OrchestrationProperties.PrimaryEngine primary;

    public SimplePreferenceReconciler(OrchestrationProperties.PrimaryEngine primary) {
        this.primary = Objects.requireNonNull(primary);
    }

    @Override
    public TranscriptionResult reconcile(EngineResult vosk, EngineResult whisper) {
        EngineResult first = primary == OrchestrationProperties.PrimaryEngine.VOSK ? vosk : whisper;
        EngineResult second = primary == OrchestrationProperties.PrimaryEngine.VOSK ? whisper : vosk;
        EngineResult pick = pickNonEmpty(first, second);
        return TranscriptionResult.of(pick == null ? "" : pick.text(), pick == null ? 0.0 : pick.confidence(),
                "reconciled");
    }

    private EngineResult pickNonEmpty(EngineResult a, EngineResult b) {
        if (a != null && a.text() != null && !a.text().isBlank()) {
            return a;
        }
        if (b != null && b.text() != null && !b.text().isBlank()) {
            return b;
        }
        return a != null ? a : b; // both empty or one null -> default to a if not null
    }
}