package com.phillippitts.speaktomack.service.orchestration;

import com.phillippitts.speaktomack.config.properties.HotkeyProperties;
import com.phillippitts.speaktomack.config.hotkey.TriggerType;
import com.phillippitts.speaktomack.config.properties.OrchestrationProperties;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import com.phillippitts.speaktomack.service.audio.capture.AudioCaptureService;
import com.phillippitts.speaktomack.service.audio.capture.CaptureErrorEvent;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyPressedEvent;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyReleasedEvent;
import com.phillippitts.speaktomack.service.orchestration.event.TranscriptionCompletedEvent;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import com.phillippitts.speaktomack.service.stt.watchdog.SttEngineWatchdog;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DualEngineOrchestratorTest {

    @Test
    void usesPrimaryWhenHealthy() {
        // Arrange
        FakeCapture cap = new FakeCapture();
        cap.pcm = new byte[3200]; // 100ms of PCM
        SttEngine vosk = new StubEngine("vosk");
        SttEngine whisper = new StubEngine("whisper");
        FakeWatchdog wd = new FakeWatchdog(true, true);
        OrchestrationProperties props = new OrchestrationProperties(OrchestrationProperties.PrimaryEngine.VOSK);
        List<Object> events = new ArrayList<>();
        ApplicationEventPublisher pub = events::add;
        DualEngineOrchestrator orch = DualEngineOrchestratorBuilder.builder()
                .captureService(cap)
                .voskEngine(vosk)
                .whisperEngine(whisper)
                .watchdog(wd)
                .orchestrationProperties(props)
                .hotkeyProperties(fakeHotkeyProps())
                .publisher(pub)
                .captureStateMachine(new CaptureStateMachine())
                .engineSelector(new EngineSelectionStrategy(vosk, whisper, wd, props))
                .timingCoordinator(new TimingCoordinator(props))
                .build();

        // Act
        orch.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        orch.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        // Assert
        boolean published = events.stream().anyMatch(e -> e instanceof TranscriptionCompletedEvent);
        assertThat(published).isTrue();
        TranscriptionCompletedEvent evt = (TranscriptionCompletedEvent) events.stream()
                .filter(e -> e instanceof TranscriptionCompletedEvent).findFirst().orElseThrow();
        assertThat(evt.result().engineName()).isEqualTo("vosk");
        assertThat(evt.engineUsed()).isEqualTo("vosk");
        assertThat(evt.timestamp()).isNotNull();
    }

    @Test
    void fallsBackWhenPrimaryDisabled() {
        FakeCapture cap = new FakeCapture();
        cap.pcm = new byte[3200];
        SttEngine vosk = new StubEngine("vosk");
        SttEngine whisper = new StubEngine("whisper");
        FakeWatchdog wd = new FakeWatchdog(false, true); // vosk disabled, whisper enabled
        OrchestrationProperties props = new OrchestrationProperties(OrchestrationProperties.PrimaryEngine.VOSK);
        List<Object> events = new ArrayList<>();
        ApplicationEventPublisher pub = events::add;
        DualEngineOrchestrator orch = DualEngineOrchestratorBuilder.builder()
                .captureService(cap)
                .voskEngine(vosk)
                .whisperEngine(whisper)
                .watchdog(wd)
                .orchestrationProperties(props)
                .hotkeyProperties(fakeHotkeyProps())
                .publisher(pub)
                .captureStateMachine(new CaptureStateMachine())
                .engineSelector(new EngineSelectionStrategy(vosk, whisper, wd, props))
                .timingCoordinator(new TimingCoordinator(props))
                .build();

        orch.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        orch.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        TranscriptionCompletedEvent evt = (TranscriptionCompletedEvent) events.stream()
                .filter(e -> e instanceof TranscriptionCompletedEvent).findFirst().orElseThrow();
        assertThat(evt.result().engineName()).isEqualTo("whisper");
        assertThat(evt.engineUsed()).isEqualTo("whisper");
        assertThat(evt.timestamp()).isNotNull();
    }

    @Test
    void throwsWhenBothDisabled() {
        FakeCapture cap = new FakeCapture();
        cap.pcm = new byte[3200];
        SttEngine vosk = new StubEngine("vosk");
        SttEngine whisper = new StubEngine("whisper");
        FakeWatchdog wd = new FakeWatchdog(false, false);
        OrchestrationProperties props = new OrchestrationProperties(OrchestrationProperties.PrimaryEngine.VOSK);
        ApplicationEventPublisher pub = e -> { };
        DualEngineOrchestrator orch = DualEngineOrchestratorBuilder.builder()
                .captureService(cap)
                .voskEngine(vosk)
                .whisperEngine(whisper)
                .watchdog(wd)
                .orchestrationProperties(props)
                .hotkeyProperties(fakeHotkeyProps())
                .publisher(pub)
                .captureStateMachine(new CaptureStateMachine())
                .engineSelector(new EngineSelectionStrategy(vosk, whisper, wd, props))
                .timingCoordinator(new TimingCoordinator(props))
                .build();

        orch.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        assertThatThrownBy(() -> orch.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now())))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("Both engines unavailable");
    }

    @Test
    void handlesCaptureError() {
        FakeCapture cap = new FakeCapture();
        cap.pcm = new byte[3200];
        SttEngine vosk = new StubEngine("vosk");
        SttEngine whisper = new StubEngine("whisper");
        FakeWatchdog wd = new FakeWatchdog(true, true);
        OrchestrationProperties props = new OrchestrationProperties(OrchestrationProperties.PrimaryEngine.VOSK);
        ApplicationEventPublisher pub = e -> { };
        DualEngineOrchestrator orch = DualEngineOrchestratorBuilder.builder()
                .captureService(cap)
                .voskEngine(vosk)
                .whisperEngine(whisper)
                .watchdog(wd)
                .orchestrationProperties(props)
                .hotkeyProperties(fakeHotkeyProps())
                .publisher(pub)
                .captureStateMachine(new CaptureStateMachine())
                .engineSelector(new EngineSelectionStrategy(vosk, whisper, wd, props))
                .timingCoordinator(new TimingCoordinator(props))
                .build();

        // Start session, then receive capture error
        orch.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        orch.onCaptureError(new CaptureErrorEvent("Microphone permission denied", Instant.now()));

        // Verify session was canceled
        assertThat(cap.canceledSession).isNotNull();
        assertThat(cap.canceledSession).isEqualTo(cap.id);
    }

    // ---- Test fakes ----

    static class FakeCapture implements AudioCaptureService {
        byte[] pcm;
        UUID id;
        UUID canceledSession;
        @Override
        public UUID startSession() {
            id = UUID.randomUUID();
            return id;
        }
        @Override
        public void stopSession(UUID sessionId) {
            /* no-op */
        }
        @Override
        public void cancelSession(UUID sessionId) {
            canceledSession = sessionId;
        }
        @Override
        public byte[] readAll(UUID sessionId) {
            return pcm;
        }
    }

    static class StubEngine implements SttEngine {
        final String name;
        StubEngine(String n) {
            this.name = n;
        }
        @Override
        public void initialize() {
        }
        @Override
        public TranscriptionResult transcribe(byte[] audioData) {
            return TranscriptionResult.of("text", 1.0, name);
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
        }
    }

    static class FakeWatchdog extends SttEngineWatchdog {
        final boolean voskEnabled;
        final boolean whisperEnabled;
        FakeWatchdog(boolean voskEnabled, boolean whisperEnabled) {
            super(java.util.List.of(),
                    new com.phillippitts.speaktomack.config.properties.SttWatchdogProperties(),
                    e -> { });
            this.voskEnabled = voskEnabled;
            this.whisperEnabled = whisperEnabled;
        }
        @Override
        public boolean isEngineEnabled(String engine) {
            return switch (engine) {
                case "vosk" -> voskEnabled;
                case "whisper" -> whisperEnabled;
                default -> false;
            };
        }
    }

    private static HotkeyProperties fakeHotkeyProps() {
        return new HotkeyProperties(TriggerType.MODIFIER_COMBO, "J", 300, List.of("META"), List.of(), false);
    }
}
