package com.phillippitts.speaktomack.service.orchestration;

import com.phillippitts.speaktomack.service.metrics.TranscriptionMetrics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Centralizes transcription metrics recording for orchestration workflows.
 *
 * <p>This service encapsulates all metrics tracking logic previously scattered throughout
 * {@link DualEngineOrchestrator}, providing a clean separation of concerns between
 * orchestration logic and observability.
 *
 * <p><b>Responsibilities:</b>
 * <ul>
 *   <li>Record transcription latency for all engine types</li>
 *   <li>Track success/failure counts per engine</li>
 *   <li>Record reconciliation strategy choices</li>
 *   <li>Handle null metrics gracefully (test mode support)</li>
 * </ul>
 *
 * <p><b>Null Safety:</b> All methods handle null {@link TranscriptionMetrics} instances
 * gracefully, allowing the orchestrator to run without metrics in test environments.
 *
 * @since 1.1
 * @see TranscriptionMetrics
 * @see DualEngineOrchestrator
 */
@Component
public final class TranscriptionMetricsPublisher {

    private static final Logger LOG = LogManager.getLogger(TranscriptionMetricsPublisher.class);

    /**
     * Singleton no-op instance for test environments and builder defaults.
     *
     * <p>This instance is safe to use when metrics tracking is not required. It:
     * <ul>
     *   <li>Never throws exceptions</li>
     *   <li>Performs no operations (all methods are no-ops)</li>
     *   <li>Always reports as disabled via {@link #isEnabled()}</li>
     * </ul>
     *
     * @since 1.1
     */
    public static final TranscriptionMetricsPublisher NOOP = new TranscriptionMetricsPublisher(null);

    private final TranscriptionMetrics metrics;

    /**
     * Constructs a metrics publisher with optional metrics support.
     *
     * @param metrics metrics tracking service (nullable for test mode)
     */
    public TranscriptionMetricsPublisher(TranscriptionMetrics metrics) {
        this.metrics = metrics;
        if (metrics == null) {
            LOG.debug("TranscriptionMetricsPublisher created without metrics (test mode)");
        }
    }

    /**
     * Records successful transcription with latency and reconciliation metadata.
     *
     * @param engineName name of the engine used (vosk, whisper, or reconciled)
     * @param durationNanos transcription duration in nanoseconds
     * @param strategy reconciliation strategy used (nullable for single-engine)
     */
    public void recordSuccess(String engineName, long durationNanos, String strategy) {
        if (metrics == null) {
            return;
        }

        metrics.recordLatency(engineName, durationNanos);
        metrics.incrementSuccess(engineName);

        if (strategy != null) {
            metrics.recordReconciliation(strategy, engineName);
        }
    }

    /**
     * Records transcription failure with error categorization.
     *
     * @param engineName name of the engine that failed
     * @param errorCategory categorization of failure (e.g., "transcription_error", "unexpected_error")
     */
    public void recordFailure(String engineName, String errorCategory) {
        if (metrics == null) {
            return;
        }

        metrics.incrementFailure(engineName, errorCategory);
    }

    /**
     * Checks if metrics tracking is enabled.
     *
     * @return true if metrics are available, false if running in test mode
     */
    public boolean isEnabled() {
        return metrics != null;
    }
}
