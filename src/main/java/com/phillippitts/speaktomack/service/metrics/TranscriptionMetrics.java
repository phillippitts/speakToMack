package com.phillippitts.speaktomack.service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Centralized metrics tracking for speech-to-text operations.
 *
 * <p>Provides instrumentation for:
 * <ul>
 *   <li>Transcription latency per engine (vosk, whisper, reconciled)</li>
 *   <li>Success/failure rates per engine</li>
 *   <li>Reconciliation strategy selection counts</li>
 * </ul>
 *
 * <p>All metrics are exposed via Micrometer and available at /actuator/prometheus.
 *
 * @see io.micrometer.core.instrument.MeterRegistry
 */
@Component
public class TranscriptionMetrics {

    private static final String METRIC_PREFIX = "speaktomack.transcription";
    private static final Set<String> ALLOWED_ENGINES = Set.of("vosk", "whisper", "reconciled", "unknown");
    private static final Set<String> ALLOWED_STRATEGIES = Set.of("SIMPLE", "CONFIDENCE", "OVERLAP");
    private static final String UNKNOWN_TAG = "unknown";

    private final MeterRegistry registry;

    public TranscriptionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Records transcription latency for a specific engine.
     *
     * @param engineName name of the engine (vosk, whisper, reconciled)
     * @param durationNanos duration in nanoseconds
     */
    public void recordLatency(String engineName, long durationNanos) {
        Timer.builder(METRIC_PREFIX + ".latency")
                .description("Time taken to transcribe audio")
                .tag("engine", sanitizeEngine(engineName))
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Increments the success counter for a specific engine.
     *
     * @param engineName name of the engine (vosk, whisper, reconciled)
     */
    public void incrementSuccess(String engineName) {
        Counter.builder(METRIC_PREFIX + ".success")
                .description("Number of successful transcriptions")
                .tag("engine", sanitizeEngine(engineName))
                .register(registry)
                .increment();
    }

    /**
     * Increments the failure counter for a specific engine.
     *
     * @param engineName name of the engine (vosk, whisper, reconciled)
     * @param reason failure reason (timeout, error, etc.)
     */
    public void incrementFailure(String engineName, String reason) {
        Counter.builder(METRIC_PREFIX + ".failure")
                .description("Number of failed transcriptions")
                .tag("engine", sanitizeEngine(engineName))
                .tag("reason", sanitizeReason(reason))
                .register(registry)
                .increment();
    }

    /**
     * Records reconciliation strategy selection.
     *
     * @param strategy reconciliation strategy used (SIMPLE, CONFIDENCE, OVERLAP)
     * @param selectedEngine which engine was selected by the strategy (vosk, whisper)
     */
    public void recordReconciliation(String strategy, String selectedEngine) {
        Counter.builder(METRIC_PREFIX + ".reconciliation")
                .description("Number of reconciliations by strategy and selected engine")
                .tag("strategy", sanitizeStrategy(strategy))
                .tag("selected", sanitizeEngine(selectedEngine))
                .register(registry)
                .increment();
    }

    private static String sanitizeEngine(String engineName) {
        return engineName != null && ALLOWED_ENGINES.contains(engineName) ? engineName : UNKNOWN_TAG;
    }

    private static String sanitizeStrategy(String strategy) {
        return strategy != null && ALLOWED_STRATEGIES.contains(strategy) ? strategy : UNKNOWN_TAG;
    }

    private static String sanitizeReason(String reason) {
        // Constrain to known short reasons to prevent cardinality bombs
        if (reason == null || reason.length() > 50) {
            return UNKNOWN_TAG;
        }
        // Only allow simple alphanumeric/underscore reasons
        return reason.matches("[a-zA-Z0-9_-]+") ? reason : UNKNOWN_TAG;
    }
}
