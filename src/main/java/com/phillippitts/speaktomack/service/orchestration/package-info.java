/**
 * Orchestration services for multi-engine STT coordination.
 *
 * <p>This package contains services that coordinate multiple STT engines for parallel
 * execution, fallback logic, and result reconciliation.
 *
 * <p>Key Components:
 * <ul>
 *   <li>{@link com.phillippitts.speaktomack.service.orchestration.ParallelTranscriptionService} -
 *       Executes Vosk and Whisper engines in parallel and returns results from both
 *       for comparison and validation</li>
 * </ul>
 *
 * <p>Design Patterns:
 * <ul>
 *   <li><b>Parallel Execution:</b> Uses {@link java.util.concurrent.CompletableFuture}
 *       for non-blocking concurrent transcription</li>
 *   <li><b>Timeout Handling:</b> Enforces configurable timeouts to prevent hanging</li>
 *   <li><b>Error Translation:</b> Converts low-level completion exceptions to domain
 *       {@link com.phillippitts.speaktomack.exception.TranscriptionException}</li>
 *   <li><b>Observability:</b> Comprehensive logging of timing, errors, and completion status</li>
 * </ul>
 *
 * <p>Use Cases:
 * <ul>
 *   <li>Testing: Compare output from both engines for accuracy validation</li>
 *   <li>Performance: Reduce latency by running engines concurrently</li>
 *   <li>Fallback: Use fast engine (Vosk) as primary, accurate engine (Whisper) as fallback</li>
 * </ul>
 *
 * <p>Future Enhancements (Phase 3):
 * <ul>
 *   <li>Result reconciliation with confidence-based selection</li>
 *   <li>Circuit breaker for failing engines</li>
 *   <li>Automatic fallback when primary engine is unhealthy</li>
 *   <li>Metrics collection for engine performance comparison</li>
 * </ul>
 *
 * @see com.phillippitts.speaktomack.service.stt
 * @see com.phillippitts.speaktomack.service.validation
 * @since 1.0
 */
package com.phillippitts.speaktomack.service.orchestration;
