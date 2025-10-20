package com.phillippitts.speaktomack.service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TranscriptionMetricsTest {

    private MeterRegistry registry;
    private TranscriptionMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new TranscriptionMetrics(registry);
    }

    @Test
    void shouldRecordLatencyForVoskEngine() {
        long durationNanos = TimeUnit.MILLISECONDS.toNanos(100);

        metrics.recordLatency("vosk", durationNanos);

        Timer timer = registry.find("speaktomack.transcription.latency")
                .tag("engine", "vosk")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(durationNanos);
    }

    @Test
    void shouldRecordLatencyForWhisperEngine() {
        long durationNanos = TimeUnit.MILLISECONDS.toNanos(250);

        metrics.recordLatency("whisper", durationNanos);

        Timer timer = registry.find("speaktomack.transcription.latency")
                .tag("engine", "whisper")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(durationNanos);
    }

    @Test
    void shouldRecordMultipleLatenciesForSameEngine() {
        metrics.recordLatency("vosk", TimeUnit.MILLISECONDS.toNanos(100));
        metrics.recordLatency("vosk", TimeUnit.MILLISECONDS.toNanos(150));
        metrics.recordLatency("vosk", TimeUnit.MILLISECONDS.toNanos(200));

        Timer timer = registry.find("speaktomack.transcription.latency")
                .tag("engine", "vosk")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(3);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(450);
    }

    @Test
    void shouldIncrementSuccessCounterForVosk() {
        metrics.incrementSuccess("vosk");
        metrics.incrementSuccess("vosk");

        Counter counter = registry.find("speaktomack.transcription.success")
                .tag("engine", "vosk")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    void shouldIncrementSuccessCounterForWhisper() {
        metrics.incrementSuccess("whisper");

        Counter counter = registry.find("speaktomack.transcription.success")
                .tag("engine", "whisper")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldIncrementSuccessCounterForReconciled() {
        metrics.incrementSuccess("reconciled");

        Counter counter = registry.find("speaktomack.transcription.success")
                .tag("engine", "reconciled")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldIncrementFailureCounterWithReason() {
        metrics.incrementFailure("vosk", "timeout");

        Counter counter = registry.find("speaktomack.transcription.failure")
                .tag("engine", "vosk")
                .tag("reason", "timeout")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldIncrementFailureCounterMultipleTimes() {
        metrics.incrementFailure("whisper", "transcription_error");
        metrics.incrementFailure("whisper", "transcription_error");
        metrics.incrementFailure("whisper", "unexpected_error");

        Counter errorCounter = registry.find("speaktomack.transcription.failure")
                .tag("engine", "whisper")
                .tag("reason", "transcription_error")
                .counter();

        Counter unexpectedCounter = registry.find("speaktomack.transcription.failure")
                .tag("engine", "whisper")
                .tag("reason", "unexpected_error")
                .counter();

        assertThat(errorCounter).isNotNull();
        assertThat(errorCounter.count()).isEqualTo(2.0);
        assertThat(unexpectedCounter).isNotNull();
        assertThat(unexpectedCounter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldRecordReconciliationWithStrategy() {
        metrics.recordReconciliation("SIMPLE", "vosk");

        Counter counter = registry.find("speaktomack.transcription.reconciliation")
                .tag("strategy", "SIMPLE")
                .tag("selected", "vosk")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldRecordMultipleReconciliationStrategies() {
        metrics.recordReconciliation("SIMPLE", "vosk");
        metrics.recordReconciliation("CONFIDENCE", "whisper");
        metrics.recordReconciliation("OVERLAP", "vosk");

        Counter simpleCounter = registry.find("speaktomack.transcription.reconciliation")
                .tag("strategy", "SIMPLE")
                .tag("selected", "vosk")
                .counter();

        Counter confidenceCounter = registry.find("speaktomack.transcription.reconciliation")
                .tag("strategy", "CONFIDENCE")
                .tag("selected", "whisper")
                .counter();

        Counter overlapCounter = registry.find("speaktomack.transcription.reconciliation")
                .tag("strategy", "OVERLAP")
                .tag("selected", "vosk")
                .counter();

        assertThat(simpleCounter).isNotNull();
        assertThat(simpleCounter.count()).isEqualTo(1.0);
        assertThat(confidenceCounter).isNotNull();
        assertThat(confidenceCounter.count()).isEqualTo(1.0);
        assertThat(overlapCounter).isNotNull();
        assertThat(overlapCounter.count()).isEqualTo(1.0);
    }

    @Test
    void shouldIsolateMetricsByEngineTags() {
        metrics.incrementSuccess("vosk");
        metrics.incrementSuccess("vosk");
        metrics.incrementSuccess("whisper");

        Counter voskCounter = registry.find("speaktomack.transcription.success")
                .tag("engine", "vosk")
                .counter();

        Counter whisperCounter = registry.find("speaktomack.transcription.success")
                .tag("engine", "whisper")
                .counter();

        assertThat(voskCounter).isNotNull();
        assertThat(voskCounter.count()).isEqualTo(2.0);
        assertThat(whisperCounter).isNotNull();
        assertThat(whisperCounter.count()).isEqualTo(1.0);
    }
}
