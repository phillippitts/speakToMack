package com.phillippitts.speaktomack.service.stt.watchdog;

import com.phillippitts.speaktomack.config.properties.SttWatchdogProperties;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.service.orchestration.event.TranscriptionCompletedEvent;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SttEngineWatchdogTest {

    @Test
    void shouldRestartEngineOnFailureWithinBudget() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 1, false, 60_000L, 0.3, 10, 5);
        RecordingEngine engine = new RecordingEngine("vosk");

        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = publishedEvents::add;

        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "test fail",
                null, java.util.Map.of()));

        Optional<EngineRecoveredEvent> recovery = publishedEvents.stream()
                .filter(e -> e instanceof EngineRecoveredEvent)
                .map(e -> (EngineRecoveredEvent) e)
                .findFirst();

        recovery.ifPresent(watchdog::onRecovered);

        assertThat(engine.closedCount).isEqualTo(1);
        assertThat(engine.initCount).isEqualTo(1);
        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.HEALTHY);
    }

    @Test
    void shouldDisableEngineAfterExceedingBudget() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 1, 1, false, 60_000L, 0.3, 10, 5);
        RecordingEngine engine = new RecordingEngine("whisper");
        ApplicationEventPublisher publisher = (event) -> { };

        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        // First failure -> restart allowed
        watchdog.onFailure(new EngineFailureEvent("whisper", Instant.now(), "fail1",
                null, java.util.Map.of()));
        // Second failure within window -> should disable
        watchdog.onFailure(new EngineFailureEvent("whisper", Instant.now(), "fail2",
                null, java.util.Map.of()));

        assertThat(watchdog.getState("whisper")).isEqualTo(SttEngineWatchdog.EngineState.DISABLED);
        int initAfterDisable = engine.initCount;
        watchdog.onFailure(new EngineFailureEvent("whisper", Instant.now(), "fail3",
                null, java.util.Map.of()));
        assertThat(engine.initCount).isEqualTo(initAfterDisable);
    }

    @Test
    void shouldNotBlacklistBeforeMinSamplesReached() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5);
        RecordingEngine engine = new RecordingEngine("vosk");
        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = publishedEvents::add;
        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        // Send 4 low-confidence events (below min samples of 5)
        for (int i = 0; i < 4; i++) {
            TranscriptionResult result = TranscriptionResult.of("text", 0.1, "vosk");
            watchdog.onTranscriptionCompleted(
                    new TranscriptionCompletedEvent(result, Instant.now(), "vosk"));
        }

        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.HEALTHY);
        assertThat(publishedEvents).filteredOn(e -> e instanceof EngineFailureEvent).isEmpty();
    }

    @Test
    void shouldBlacklistEngineWhenConfidenceBelowThreshold() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 1, false, 60_000L, 0.3, 10, 5);
        RecordingEngine engine = new RecordingEngine("vosk");
        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = publishedEvents::add;
        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        // Send 5 low-confidence events (meets min samples, avg 0.1 < threshold 0.3)
        for (int i = 0; i < 5; i++) {
            TranscriptionResult result = TranscriptionResult.of("text", 0.1, "vosk");
            watchdog.onTranscriptionCompleted(
                    new TranscriptionCompletedEvent(result, Instant.now(), "vosk"));
        }

        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.DEGRADED);
        assertThat(publishedEvents).filteredOn(e -> e instanceof EngineFailureEvent).hasSize(1);
    }

    @Test
    void shouldNotBlacklistWhenConfidenceAboveThreshold() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5);
        RecordingEngine engine = new RecordingEngine("vosk");
        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = publishedEvents::add;
        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        for (int i = 0; i < 10; i++) {
            TranscriptionResult result = TranscriptionResult.of("text", 0.8, "vosk");
            watchdog.onTranscriptionCompleted(
                    new TranscriptionCompletedEvent(result, Instant.now(), "vosk"));
        }

        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.HEALTHY);
        assertThat(publishedEvents).filteredOn(e -> e instanceof EngineFailureEvent).isEmpty();
    }

    @Test
    void shouldPruneConfidenceWindowToConfiguredSize() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 5, 5);
        RecordingEngine engine = new RecordingEngine("vosk");
        ApplicationEventPublisher publisher = event -> { };
        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        for (int i = 0; i < 8; i++) {
            TranscriptionResult result = TranscriptionResult.of("text", 0.9, "vosk");
            watchdog.onTranscriptionCompleted(
                    new TranscriptionCompletedEvent(result, Instant.now(), "vosk"));
        }

        Deque<Double> window = watchdog.getConfidenceMonitor().getWindow("vosk");
        assertThat(window).hasSize(5);
    }

    @Test
    void shouldClearConfidenceWindowOnRecovery() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 1, false, 60_000L, 0.3, 10, 5);
        RecordingEngine engine = new RecordingEngine("vosk");
        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = publishedEvents::add;
        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        for (int i = 0; i < 5; i++) {
            TranscriptionResult result = TranscriptionResult.of("text", 0.1, "vosk");
            watchdog.onTranscriptionCompleted(
                    new TranscriptionCompletedEvent(result, Instant.now(), "vosk"));
        }

        watchdog.onRecovered(new EngineRecoveredEvent("vosk", Instant.now()));

        Deque<Double> window = watchdog.getConfidenceMonitor().getWindow("vosk");
        assertThat(window).isEmpty();
        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.HEALTHY);
    }

    @Test
    void shouldIgnoreConfidenceForUnknownEngines() {
        SttWatchdogProperties props = new SttWatchdogProperties(
                true, 60, 3, 10, false, 60_000L, 0.3, 10, 5);
        RecordingEngine engine = new RecordingEngine("vosk");
        ApplicationEventPublisher publisher = event -> { };
        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        // "reconciled" is not a tracked engine — should be silently ignored
        TranscriptionResult result = TranscriptionResult.of("text", 0.1, "reconciled");
        watchdog.onTranscriptionCompleted(
                new TranscriptionCompletedEvent(result, Instant.now(), "reconciled"));

        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.HEALTHY);
    }

    // --- Test double ---
    static class RecordingEngine implements SttEngine {
        final String name;
        int initCount = 0;
        int closedCount = 0;

        RecordingEngine(String name) {
            this.name = name;
        }

        @Override
        public void initialize() {
            initCount++;
        }
        @Override
        public TranscriptionResult transcribe(byte[] audioData) {
            return TranscriptionResult.of("", 1.0, name);
        }
        @Override
        public String getEngineName() {
            return name;
        }
        @Override
        public boolean isHealthy() {
            return true;
        }
        @Override
        public void close() {
            closedCount++;
        }
    }
}
