package com.phillippitts.speaktomack.service.orchestration;

/**
 * Orchestrates speech-to-text transcription using single or dual-engine modes.
 *
 * <p>This orchestrator encapsulates the complete transcription pipeline including engine
 * selection, transcription execution, confidence-based reconciliation upgrades, result
 * publishing, and metrics recording.
 *
 * <p><b>Transcription Modes:</b>
 * <ul>
 *   <li><b>Single-Engine Mode:</b> Uses one engine (Vosk or Whisper) based on primary
 *       preference and health status. Automatically upgrades to dual-engine reconciliation
 *       if Vosk confidence is below threshold.</li>
 *   <li><b>Reconciliation Mode:</b> Runs both engines in parallel and reconciles results
 *       using configurable strategy (SIMPLE, CONFIDENCE, or OVERLAP).</li>
 * </ul>
 *
 * <p><b>Smart Reconciliation:</b> When running in single-engine mode with Vosk as the
 * selected engine, if the confidence score falls below the configured threshold, the
 * orchestrator automatically upgrades to dual-engine reconciliation for improved accuracy.
 *
 * <p><b>Paragraph Breaks:</b> The orchestrator tracks timing between transcriptions and
 * automatically inserts paragraph breaks (newlines) when the gap exceeds a configured
 * threshold, improving readability of the transcribed text.
 *
 * <p><b>Error Handling:</b> Transcription failures are logged and empty results are
 * published to notify downstream consumers. Metrics are recorded for both success and
 * failure cases to support monitoring and alerting.
 *
 * <p><b>Metrics:</b> Records success/failure metrics for each transcription including
 * duration, engine used, and failure reasons. Supports observability and health monitoring.
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * TranscriptionOrchestrator orchestrator = ...;
 * byte[] pcm = ...; // 16kHz mono 16-bit PCM audio
 *
 * // Transcribe and publish result
 * orchestrator.transcribe(pcm);
 * }</pre>
 *
 * @see com.phillippitts.speaktomack.service.stt.SttEngine
 * @see com.phillippitts.speaktomack.service.orchestration.event.TranscriptionCompletedEvent
 * @see com.phillippitts.speaktomack.service.reconcile.TranscriptReconciler
 * @since 1.1
 */
public interface TranscriptionOrchestrator {

    /**
     * Transcribes PCM audio data and publishes the result as an event.
     *
     * <p>This method performs the complete transcription pipeline:
     * <ol>
     *   <li>Selects transcription mode (single-engine vs. reconciliation)</li>
     *   <li>Executes transcription using selected mode</li>
     *   <li>Evaluates confidence and upgrades to reconciliation if needed</li>
     *   <li>Adds paragraph breaks based on timing gaps</li>
     *   <li>Publishes {@link com.phillippitts.speaktomack.service.orchestration.event.TranscriptionCompletedEvent}</li>
     *   <li>Records metrics for monitoring</li>
     * </ol>
     *
     * <p><b>Error Handling:</b> If transcription fails (engine error, timeout, etc.),
     * an empty result is published to notify downstream consumers while preventing
     * potentially incorrect text from being emitted. Failures are logged with context.
     *
     * <p><b>Performance:</b> Single-engine transcription typically completes in 1-3
     * seconds. Dual-engine reconciliation takes 2-5 seconds depending on audio length.
     *
     * @param pcm PCM audio data (16kHz, mono, 16-bit little-endian)
     * @throws NullPointerException if pcm is null
     */
    void transcribe(byte[] pcm);
}
