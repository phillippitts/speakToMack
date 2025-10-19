package com.phillippitts.speaktomack.service.reconcile;

import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.service.stt.EngineResult;

/**
 * Strategy interface to reconcile two engine results into a final TranscriptionResult.
 */
public interface TranscriptReconciler {
    TranscriptionResult reconcile(EngineResult vosk, EngineResult whisper);
}
