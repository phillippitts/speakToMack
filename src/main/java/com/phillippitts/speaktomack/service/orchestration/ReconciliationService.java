package com.phillippitts.speaktomack.service.orchestration;

import com.phillippitts.speaktomack.domain.TranscriptionResult;

/**
 * Service for dual-engine reconciliation of transcription results.
 *
 * <p>This service encapsulates the complete dual-engine reconciliation workflow:
 * <ol>
 *   <li>Running both Vosk and Whisper engines in parallel</li>
 *   <li>Reconciling the results using a configured strategy</li>
 *   <li>Managing reconciliation configuration and enablement</li>
 * </ol>
 *
 * <p><b>Reconciliation Modes:</b>
 * <ul>
 *   <li><b>Disabled:</b> {@link #isEnabled()} returns false, reconciliation is not available</li>
 *   <li><b>Enabled:</b> Reconciliation is available and will be used when requested</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> Implementations must be thread-safe as reconciliation
 * can be triggered from multiple concurrent transcription requests.
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * ReconciliationService reconciliation = ...;
 * byte[] pcm = ...; // 16kHz mono 16-bit PCM audio
 *
 * if (reconciliation.isEnabled()) {
 *     TranscriptionResult result = reconciliation.reconcile(pcm);
 *     // Use reconciled result
 * }
 * }</pre>
 *
 * @see com.phillippitts.speaktomack.service.stt.parallel.ParallelSttService
 * @see com.phillippitts.speaktomack.service.reconcile.TranscriptReconciler
 * @see com.phillippitts.speaktomack.config.properties.ReconciliationProperties
 * @since 1.1
 */
public interface ReconciliationService {

    /**
     * Checks if reconciliation mode is enabled with all required dependencies.
     *
     * <p>Reconciliation is considered enabled when:
     * <ul>
     *   <li>ReconciliationProperties.enabled is true</li>
     *   <li>ParallelSttService is available (not null)</li>
     *   <li>TranscriptReconciler is available (not null)</li>
     * </ul>
     *
     * @return true if reconciliation is enabled and all services are available
     */
    boolean isEnabled();

    /**
     * Reconciles transcription results from both engines using the configured strategy.
     *
     * <p>This method performs the complete reconciliation workflow:
     * <ol>
     *   <li>Runs both Vosk and Whisper engines in parallel on the audio</li>
     *   <li>Waits for both engines to complete (with timeout)</li>
     *   <li>Reconciles the results using the configured strategy</li>
     *   <li>Returns the final reconciled result</li>
     * </ol>
     *
     * <p><b>Error Handling:</b> If reconciliation fails (engine error, timeout, etc.),
     * this method throws a TranscriptionException. Callers should handle this and
     * decide whether to fall back to single-engine mode or emit an empty result.
     *
     * @param pcm PCM audio data (16kHz, mono, 16-bit little-endian)
     * @return reconciled transcription result
     * @throws com.phillippitts.speaktomack.exception.TranscriptionException if reconciliation fails
     * @throws NullPointerException if pcm is null
     * @throws IllegalStateException if reconciliation is not enabled
     */
    TranscriptionResult reconcile(byte[] pcm);

    /**
     * Returns the confidence threshold for smart reconciliation upgrades.
     *
     * <p>When running in single-engine mode with Vosk, if the confidence score
     * falls below this threshold, the orchestrator can upgrade to dual-engine
     * reconciliation for improved accuracy.
     *
     * @return confidence threshold (0.0-1.0)
     * @throws IllegalStateException if reconciliation is not enabled
     */
    double getConfidenceThreshold();

    /**
     * Returns the reconciliation strategy name for metrics/logging.
     *
     * @return strategy name (e.g., "SIMPLE", "CONFIDENCE", "OVERLAP")
     * @throws IllegalStateException if reconciliation is not enabled
     */
    String getStrategy();
}
