package com.phillippitts.blckvox.service.orchestration;

import org.springframework.stereotype.Component;

/**
 * Centralizes transcription metrics recording for orchestration workflows.
 *
 * <p>All methods are no-ops. The class is retained so that orchestration code
 * can call it unconditionally without null checks.
 *
 * @since 1.1
 * @see HotkeyRecordingAdapter
 */
@Component
public final class TranscriptionMetricsPublisher {

    /**
     * Singleton no-op instance for builder defaults and tests.
     */
    public static final TranscriptionMetricsPublisher NOOP = new TranscriptionMetricsPublisher();

    /**
     * Records successful transcription (no-op).
     */
    public void recordSuccess(String engineName, long durationNanos, String strategy) {
        // no-op
    }

    /**
     * Records transcription failure (no-op).
     */
    public void recordFailure(String engineName, String errorCategory) {
        // no-op
    }

    /**
     * Records the processing-time-to-audio-duration ratio (no-op).
     */
    public void recordProcessingRatio(String engineName, double ratio) {
        // no-op
    }

    /**
     * Checks if metrics tracking is enabled.
     *
     * @return always false
     */
    public boolean isEnabled() {
        return false;
    }
}
