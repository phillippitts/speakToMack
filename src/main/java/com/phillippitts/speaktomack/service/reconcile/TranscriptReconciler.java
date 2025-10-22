package com.phillippitts.speaktomack.service.reconcile;

import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.service.stt.EngineResult;

/**
 * Strategy interface for reconciling dual-engine transcription results (Phase 4).
 *
 * <p>Implementations of this interface define strategies for merging or selecting from
 * two concurrent transcription results (Vosk and Whisper) to produce a single, optimal
 * transcription. This enables hybrid approaches that leverage the strengths of both engines.
 *
 * <p><b>Available Strategies:</b>
 * <ul>
 *   <li>{@link com.phillippitts.speaktomack.service.reconcile.impl.SimplePreferenceReconciler} -
 *       Always prefers primary engine unless empty</li>
 *   <li>{@link com.phillippitts.speaktomack.service.reconcile.impl.ConfidenceReconciler} -
 *       Selects result with higher confidence score</li>
 *   <li>{@link com.phillippitts.speaktomack.service.reconcile.impl.WordOverlapReconciler} -
 *       Uses Jaccard similarity to pick best coverage</li>
 * </ul>
 *
 * <p><b>Null Handling Contract:</b> Implementations must gracefully handle null engine results,
 * which indicate engine failure or timeout. When both results are null, implementations should
 * return an empty {@link TranscriptionResult} with zero confidence.
 *
 * <p><b>Engine Name Convention:</b> Reconciled results should use {@code "reconciled"} as the
 * engine name to distinguish them from single-engine results.
 *
 * <p><b>Thread Safety:</b> Implementations should be stateless and thread-safe to support
 * concurrent reconciliation operations.
 *
 * @see EngineResult
 * @see TranscriptionResult
 * @see com.phillippitts.speaktomack.service.stt.parallel.ParallelSttService
 * @since 1.0 (Phase 4)
 */
public interface TranscriptReconciler {
    /**
     * Reconciles two engine results into a single transcription result.
     *
     * <p>This method is invoked after both engines complete transcription (or timeout/fail).
     * The implementation should apply its strategy to select or merge the results.
     *
     * @param vosk Vosk engine result (may be null if engine failed or timed out)
     * @param whisper Whisper engine result (may be null if engine failed or timed out)
     * @return reconciled transcription result with {@code "reconciled"} engine name
     */
    TranscriptionResult reconcile(EngineResult vosk, EngineResult whisper);
}
