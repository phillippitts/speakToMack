package com.phillippitts.speaktomack.service.stt.watchdog;

import com.phillippitts.speaktomack.config.stt.SttWatchdogProperties;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.PayloadApplicationEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SttEngineWatchdogTest {

    @Test
    void shouldRestartEngineOnFailureWithinBudget() {
        // Arrange minimal properties
        SttWatchdogProperties props = new SttWatchdogProperties();
        props.setWindowMinutes(60);
        props.setMaxRestartsPerWindow(3);
        props.setCooldownMinutes(1);
        RecordingEngine engine = new RecordingEngine("vosk");

        // Capture published events so we can manually deliver EngineRecoveredEvent
        List<Object> publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = publishedEvents::add;

        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        // Act: simulate failure
        watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "test fail", null, java.util.Map.of()));

        // Manually deliver EngineRecoveredEvent that was published
        Optional<EngineRecoveredEvent> recovery = publishedEvents.stream()
                .filter(e -> e instanceof EngineRecoveredEvent)
                .map(e -> (EngineRecoveredEvent) e)
                .findFirst();

        if (recovery.isPresent()) {
            watchdog.onRecovered(recovery.get());
        }

        // Assert: close and initialize should have been called once
        assertThat(engine.closedCount).isEqualTo(1);
        assertThat(engine.initCount).isEqualTo(1);
        assertThat(watchdog.getState("vosk")).isEqualTo(SttEngineWatchdog.EngineState.HEALTHY);
    }

    @Test
    void shouldDisableEngineAfterExceedingBudget() {
        SttWatchdogProperties props = new SttWatchdogProperties();
        props.setWindowMinutes(60);
        props.setMaxRestartsPerWindow(1); // small budget for test
        props.setCooldownMinutes(1);
        RecordingEngine engine = new RecordingEngine("whisper");
        ApplicationEventPublisher publisher = (event) -> { /* no-op */ };

        SttEngineWatchdog watchdog = new SttEngineWatchdog(List.of(engine), props, publisher);

        // First failure -> restart allowed
        watchdog.onFailure(new EngineFailureEvent("whisper", Instant.now(), "fail1", null, java.util.Map.of()));
        // Second failure within window -> should disable
        watchdog.onFailure(new EngineFailureEvent("whisper", Instant.now(), "fail2", null, java.util.Map.of()));

        assertThat(watchdog.getState("whisper")).isEqualTo(SttEngineWatchdog.EngineState.DISABLED);
        // When disabled, additional failures should not trigger more restarts
        int initAfterDisable = engine.initCount;
        watchdog.onFailure(new EngineFailureEvent("whisper", Instant.now(), "fail3", null, java.util.Map.of()));
        assertThat(engine.initCount).isEqualTo(initAfterDisable);
    }

    // --- Test double ---
    static class RecordingEngine implements SttEngine {
        final String name;
        int initCount = 0;
        int closedCount = 0;

        RecordingEngine(String name) { this.name = name; }

        @Override public void initialize() { initCount++; }
        @Override public TranscriptionResult transcribe(byte[] audioData) { return TranscriptionResult.of("", 1.0, name); }
        @Override public String getEngineName() { return name; }
        @Override public boolean isHealthy() { return true; }
        @Override public void close() { closedCount++; }
    }
}
