package com.phillippitts.speaktomack.service.orchestration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit tests for {@link TranscriptionMetricsPublisher#NOOP}.
 *
 * <p>These tests ensure that the no-op publisher constant is safe to use as a builder default,
 * preventing NPEs and documenting the expected behavior when metrics are not configured.
 */
class NoopTranscriptionMetricsPublisherTest {

    @Test
    void noopConstantShouldNotBeNull() {
        assertNotNull(TranscriptionMetricsPublisher.NOOP,
                "NOOP should be a valid singleton constant");
    }

    @Test
    void noopShouldBeSingleton() {
        TranscriptionMetricsPublisher first = TranscriptionMetricsPublisher.NOOP;
        TranscriptionMetricsPublisher second = TranscriptionMetricsPublisher.NOOP;

        assertSame(first, second, "NOOP should return the same singleton instance");
    }

    @Test
    void isEnabledShouldReturnFalse() {
        assertFalse(TranscriptionMetricsPublisher.NOOP.isEnabled(),
                "No-op publisher should always report as disabled");
    }

    @Test
    void recordSuccessShouldNotThrowNPE() {
        assertDoesNotThrow(() ->
            TranscriptionMetricsPublisher.NOOP.recordSuccess("vosk", 1000000L, null),
            "recordSuccess should handle null strategy without throwing");

        assertDoesNotThrow(() ->
            TranscriptionMetricsPublisher.NOOP.recordSuccess("whisper", 2000000L, "simple"),
            "recordSuccess should handle valid inputs without throwing");

        assertDoesNotThrow(() ->
            TranscriptionMetricsPublisher.NOOP.recordSuccess(null, 0L, null),
            "recordSuccess should handle all null inputs without throwing");
    }

    @Test
    void recordFailureShouldNotThrowNPE() {
        assertDoesNotThrow(() ->
            TranscriptionMetricsPublisher.NOOP.recordFailure("vosk", "timeout"),
            "recordFailure should handle valid inputs without throwing");

        assertDoesNotThrow(() ->
            TranscriptionMetricsPublisher.NOOP.recordFailure(null, null),
            "recordFailure should handle null inputs without throwing");
    }

    @Test
    void multipleCallsShouldNotAccumulateState() {
        TranscriptionMetricsPublisher publisher = TranscriptionMetricsPublisher.NOOP;

        // Call methods multiple times
        for (int i = 0; i < 100; i++) {
            publisher.recordSuccess("vosk", i * 1000L, "simple");
            publisher.recordFailure("whisper", "error");
        }

        // Should still report as disabled (no state change)
        assertFalse(publisher.isEnabled(),
                "No-op publisher should remain stateless after multiple calls");
    }
}
