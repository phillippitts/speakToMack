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
                .build();

        // Start session, then receive capture error
        orch.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        orch.onCaptureError(new CaptureErrorEvent("Microphone permission denied", Instant.now()));

        // Verify session was canceled
        assertThat(cap.canceledSession).isNotNull();
        assertThat(cap.canceledSession).isEqualTo(cap.id);
    }

    @Test
    void shouldHandleRapidTogglePresses() {
        // Test that rapid hotkey presses don't create race conditions
        FakeCapture cap = new FakeCapture();
        cap.pcm = new byte[3200];
        SttEngine vosk = new StubEngine("vosk");
        SttEngine whisper = new StubEngine("whisper");
        FakeWatchdog wd = new FakeWatchdog(true, true);
        OrchestrationProperties props = new OrchestrationProperties(OrchestrationProperties.PrimaryEngine.VOSK);
        List<Object> events = new ArrayList<>();
        ApplicationEventPublisher pub = events::add;
        CaptureStateMachine csm = new CaptureStateMachine();
        DualEngineOrchestrator orch = DualEngineOrchestratorBuilder.builder()
                .captureService(cap)
                .voskEngine(vosk)
                .whisperEngine(whisper)
                .watchdog(wd)
                .orchestrationProperties(props)
                .hotkeyProperties(fakeHotkeyProps())
                .publisher(pub)
                .captureStateMachine(csm)
                .engineSelector(new EngineSelectionStrategy(vosk, whisper, wd, props))
                .build();

        // Simulate: press (start), press again (ignored - already active), release (complete)
        orch.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        UUID firstSession = cap.id;
        assertThat(firstSession).isNotNull();
        assertThat(csm.isActive()).isTrue();

        // Second press should be ignored since session is already active
        orch.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        assertThat(cap.id).isEqualTo(firstSession); // Same session, not a new one
        assertThat(csm.isActive()).isTrue();

        // Release completes the transcription
        orch.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));
        assertThat(csm.isActive()).isFalse();

        // Verify only one transcription completed
        long completedCount = events.stream()
                .filter(e -> e instanceof TranscriptionCompletedEvent)
                .count();
        assertThat(completedCount).isEqualTo(1);
    }

    @org.junit.jupiter.api.Disabled("TimingCoordinator removed - pause detection now done by STT engines")
    @Test
    void shouldPrependParagraphBreakAfterSilenceGap() throws InterruptedException {
        // Arrange
        FakeCapture cap = new FakeCapture();
        cap.pcm = new byte[3200];
        SttEngine vosk = new StubEngine("vosk");
        SttEngine whisper = new StubEngine("whisper");
        FakeWatchdog wd = new FakeWatchdog(true, true);
        OrchestrationProperties props = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK,
                100 // 100ms silence gap threshold
        );
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
                .build();

        // Act: First transcription - no paragraph break
        orch.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        orch.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        TranscriptionCompletedEvent evt1 = (TranscriptionCompletedEvent) events.stream()
                .filter(e -> e instanceof TranscriptionCompletedEvent)
                .findFirst().orElseThrow();
        assertThat(evt1.result().text()).isEqualTo("text"); // No newline prepended
        events.clear();

        // Wait longer than silence gap threshold
        Thread.sleep(150);

        // Second transcription - should have paragraph break prepended
        orch.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        orch.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        TranscriptionCompletedEvent evt2 = (TranscriptionCompletedEvent) events.stream()
                .filter(e -> e instanceof TranscriptionCompletedEvent)
                .findFirst().orElseThrow();
        assertThat(evt2.result().text()).startsWith("\n"); // Newline prepended
        assertThat(evt2.result().text()).isEqualTo("\ntext");
    }

    @org.junit.jupiter.api.Disabled("TimingCoordinator removed - pause detection now done by STT engines")
    @Test
    void shouldNotAddDoubleNewlineWhenTextAlreadyStartsWithNewline() throws InterruptedException {
        // Arrange
        FakeCapture cap = new FakeCapture();
        cap.pcm = new byte[3200];
        SttEngine vosk = createVoskEngineWithNewlinePrefix();
        List<Object> events = new ArrayList<>();

        DualEngineOrchestrator orch = buildOrchestratorForParagraphBreakTest(cap, vosk, events);

        // First transcription
        orch.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        orch.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));
        events.clear();

        // Wait for silence gap
        Thread.sleep(150);

        // Second transcription - engine returns text starting with newline
        orch.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        orch.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        TranscriptionCompletedEvent evt = (TranscriptionCompletedEvent) events.stream()
                .filter(e -> e instanceof TranscriptionCompletedEvent)
                .findFirst().orElseThrow();

        // Should NOT have double newline
        assertThat(evt.result().text()).isEqualTo("\nalready-has-newline");
        assertThat(evt.result().text()).doesNotStartWith("\n\n");
    }

    private SttEngine createVoskEngineWithNewlinePrefix() {
        return new SttEngine() {
            @Override
            public void initialize() { }
            @Override
            public TranscriptionResult transcribe(byte[] audioData) {
                return TranscriptionResult.of("\nalready-has-newline", 1.0, "vosk");
            }
            @Override
            public String getEngineName() {
                return "vosk";
            }
            @Override
            public boolean isHealthy() {
                return true;
            }
            @Override
            public void close() { }
        };
    }

    private DualEngineOrchestrator buildOrchestratorForParagraphBreakTest(
            FakeCapture cap, SttEngine vosk, List<Object> events) {
        SttEngine whisper = new StubEngine("whisper");
        FakeWatchdog wd = new FakeWatchdog(true, true);
        OrchestrationProperties props = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK,
                100
        );
        ApplicationEventPublisher pub = events::add;

        return DualEngineOrchestratorBuilder.builder()
                .captureService(cap)
                .voskEngine(vosk)
                .whisperEngine(whisper)
                .watchdog(wd)
                .orchestrationProperties(props)
                .hotkeyProperties(fakeHotkeyProps())
                .publisher(pub)
                .captureStateMachine(new CaptureStateMachine())
                .engineSelector(new EngineSelectionStrategy(vosk, whisper, wd, props))
                .build();
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
