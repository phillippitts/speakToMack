package com.phillippitts.speaktomack.service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

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
                .tag("engine", engineName)
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
                .tag("engine", engineName)
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
                .tag("engine", engineName)
                .tag("reason", reason)
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
                .tag("strategy", strategy)
                .tag("selected", selectedEngine)
                .register(registry)
                .increment();
    }
}
