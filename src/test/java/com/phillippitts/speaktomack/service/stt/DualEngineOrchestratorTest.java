package com.phillippitts.speaktomack.service.stt;

import com.phillippitts.speaktomack.config.stt.SttWatchdogProperties;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import com.phillippitts.speaktomack.service.stt.watchdog.EngineFailureEvent;
import com.phillippitts.speaktomack.service.stt.watchdog.SttEngineWatchdog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for DualEngineOrchestrator fallback logic.
 */
class DualEngineOrchestratorTest {

    private SttEngineWatchdog watchdog;
    private MockEngine voskEngine;
    private MockEngine whisperEngine;
    private DualEngineOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        // Minimal watchdog config
        SttWatchdogProperties props = new SttWatchdogProperties();
        props.setWindowMinutes(60);
        props.setMaxRestartsPerWindow(3);
        props.setCooldownMinutes(1);

        voskEngine = new MockEngine("vosk");
        whisperEngine = new MockEngine("whisper");

        // No-op publisher for test (we won't trigger recovery events)
        ApplicationEventPublisher publisher = (event) -> { /* no-op */ };

        watchdog = new SttEngineWatchdog(List.of(voskEngine, whisperEngine), props, publisher);
        orchestrator = new DualEngineOrchestrator(voskEngine, whisperEngine, watchdog);
    }

    @Test
    void shouldRouteToVoskWhenHealthy() {
        // Arrange
        byte[] audio = new byte[]{1, 2, 3};

        // Act
        TranscriptionResult result = orchestrator.transcribe(audio);

        // Assert - should use Vosk (primary)
        assertThat(result.engineName()).isEqualTo("vosk");
        assertThat(voskEngine.transcribeCalls).isEqualTo(1);
        assertThat(whisperEngine.transcribeCalls).isEqualTo(0);
    }

    @Test
    void shouldFallbackToWhisperWhenVoskDisabled() {
        // Arrange
        byte[] audio = new byte[]{1, 2, 3};

        // Disable Vosk by exceeding budget (3 failures with maxRestartsPerWindow=3)
        for (int i = 0; i < 4; i++) {
            watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "fail" + i, null, java.util.Map.of()));
        }

        // Verify Vosk is disabled
        assertThat(watchdog.isEngineEnabled("vosk")).isFalse();

        // Act
        TranscriptionResult result = orchestrator.transcribe(audio);

        // Assert - should fall back to Whisper
        assertThat(result.engineName()).isEqualTo("whisper");
        assertThat(whisperEngine.transcribeCalls).isEqualTo(1);
    }

    @Test
    void shouldThrowWhenBothEnginesDisabled() {
        // Arrange
        byte[] audio = new byte[]{1, 2, 3};

        // Disable both engines by exceeding budget
        for (int i = 0; i < 4; i++) {
            watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "fail" + i, null, java.util.Map.of()));
            watchdog.onFailure(new EngineFailureEvent("whisper", Instant.now(), "fail" + i, null, java.util.Map.of()));
        }

        // Verify both engines are disabled
        assertThat(watchdog.isEngineEnabled("vosk")).isFalse();
        assertThat(watchdog.isEngineEnabled("whisper")).isFalse();

        // Act & Assert
        assertThatThrownBy(() -> orchestrator.transcribe(audio))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("All STT engines disabled");
    }

    @Test
    void shouldRejectNullAudio() {
        assertThatThrownBy(() -> orchestrator.transcribe(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audioData must not be null or empty");
    }

    @Test
    void shouldRejectEmptyAudio() {
        assertThatThrownBy(() -> orchestrator.transcribe(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audioData must not be null or empty");
    }

    @Test
    void shouldReportHealthyWhenAtLeastOneEngineHealthy() {
        // Arrange - both healthy
        assertThat(orchestrator.isHealthy()).isTrue();

        // Degrade Vosk
        watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "fail", null, java.util.Map.of()));
        assertThat(orchestrator.isHealthy()).isTrue(); // Still healthy because Whisper is up

        // Disable both
        for (int i = 0; i < 4; i++) {
            watchdog.onFailure(new EngineFailureEvent("vosk", Instant.now(), "fail" + i, null, java.util.Map.of()));
            watchdog.onFailure(new EngineFailureEvent("whisper", Instant.now(), "fail" + i, null, java.util.Map.of()));
        }
        assertThat(orchestrator.isHealthy()).isFalse();
    }

    @Test
    void shouldFallbackWhenVoskUnhealthy() {
        // Arrange
        byte[] audio = new byte[]{1, 2, 3};
        voskEngine.healthy = false; // Mark engine as unhealthy (but not disabled by watchdog)

        // Act
        TranscriptionResult result = orchestrator.transcribe(audio);

        // Assert - should fall back to Whisper even though Vosk is "enabled" by watchdog
        assertThat(result.engineName()).isEqualTo("whisper");
        assertThat(whisperEngine.transcribeCalls).isEqualTo(1);
    }

    @Test
    void shouldReturnCorrectEngineName() {
        assertThat(orchestrator.getEngineName()).isEqualTo("dual-orchestrator");
    }

    // --- Test double: mock engine that tracks calls ---
    static class MockEngine implements SttEngine {
        final String name;
        int transcribeCalls = 0;
        boolean healthy = true;

        MockEngine(String name) {
            this.name = name;
        }

        @Override
        public void initialize() {
            // no-op
        }

        @Override
        public TranscriptionResult transcribe(byte[] audioData) {
            transcribeCalls++;
            return TranscriptionResult.of("mock text from " + name, 1.0, name);
        }

        @Override
        public String getEngineName() {
            return name;
        }

        @Override
        public boolean isHealthy() {
            return healthy;
        }

        @Override
        public void close() {
            // no-op
        }
    }
}
