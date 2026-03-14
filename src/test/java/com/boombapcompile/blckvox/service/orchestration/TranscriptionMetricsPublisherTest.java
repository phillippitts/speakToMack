package com.boombapcompile.blckvox.service.orchestration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TranscriptionMetricsPublisherTest {

    @Test
    void recordSuccessDoesNotThrow() {
        TranscriptionMetricsPublisher publisher = new TranscriptionMetricsPublisher();
        assertThatCode(() -> publisher.recordSuccess("vosk", 1000L, "single"))
                .doesNotThrowAnyException();
    }

    @Test
    void recordFailureDoesNotThrow() {
        TranscriptionMetricsPublisher publisher = new TranscriptionMetricsPublisher();
        assertThatCode(() -> publisher.recordFailure("vosk", "timeout"))
                .doesNotThrowAnyException();
    }

    @Test
    void recordProcessingRatioDoesNotThrow() {
        TranscriptionMetricsPublisher publisher = new TranscriptionMetricsPublisher();
        assertThatCode(() -> publisher.recordProcessingRatio("vosk", 0.5))
                .doesNotThrowAnyException();
    }

    @Test
    void isEnabledReturnsFalse() {
        assertThat(new TranscriptionMetricsPublisher().isEnabled()).isFalse();
    }

    @Test
    void noopInstanceIsNotNull() {
        assertThat(TranscriptionMetricsPublisher.NOOP).isNotNull();
        assertThat(TranscriptionMetricsPublisher.NOOP.isEnabled()).isFalse();
    }
}
