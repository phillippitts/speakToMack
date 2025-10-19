package com.phillippitts.speaktomack.service.reconcile.impl;

import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.service.reconcile.TranscriptReconciler;
import com.phillippitts.speaktomack.service.stt.EngineResult;

/**
 * Picks the result with the higher confidence; breaks ties preferring non-empty text.
 */
public final class ConfidenceReconciler implements TranscriptReconciler {
    @Override
    public TranscriptionResult reconcile(EngineResult vosk, EngineResult whisper) {
        EngineResult a = vosk;
        EngineResult b = whisper;
        EngineResult pick;
        if (a == null) {
            pick = b;
        } else if (b == null) {
            pick = a;
        } else if (a.confidence() > b.confidence()) {
            pick = a;
        } else if (b.confidence() > a.confidence()) {
            pick = b;
        } else {
            // tie-breaker: prefer non-empty text
            boolean aNonEmpty = a.text() != null && !a.text().isBlank();
            boolean bNonEmpty = b.text() != null && !b.text().isBlank();
            if (aNonEmpty && !bNonEmpty) {
                pick = a;
            } else if (!aNonEmpty && bNonEmpty) {
                pick = b;
            } else {
                pick = a; // default to a
            }
        }
        return TranscriptionResult.of(pick == null ? "" : pick.text(), pick == null ? 0.0 : pick.confidence(),
                "reconciled");
    }
}