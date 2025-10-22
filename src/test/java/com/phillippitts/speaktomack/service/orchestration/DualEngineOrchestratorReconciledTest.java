package com.phillippitts.speaktomack.service.orchestration;

import com.phillippitts.speaktomack.config.properties.OrchestrationProperties;
import com.phillippitts.speaktomack.config.properties.HotkeyProperties;
import com.phillippitts.speaktomack.config.hotkey.TriggerType;
import com.phillippitts.speaktomack.config.properties.ReconciliationProperties;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.service.audio.capture.AudioCaptureService;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyPressedEvent;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyReleasedEvent;
import com.phillippitts.speaktomack.service.orchestration.event.TranscriptionCompletedEvent;
import com.phillippitts.speaktomack.service.reconcile.TranscriptReconciler;
import com.phillippitts.speaktomack.service.stt.EngineResult;
import com.phillippitts.speaktomack.service.stt.parallel.ParallelSttService;
import com.phillippitts.speaktomack.service.stt.watchdog.SttEngineWatchdog;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies reconciled path behaviour when stt.reconciliation.enabled=true.
 */
class DualEngineOrchestratorReconciledTest {

    @Test
    void shouldPublishReconciledEventWhenEnabled() {
        // Arrange
        FakeCapture cap = new FakeCapture();
        cap.pcm = new byte[3200];
        SttEngine vosk = new StubEngine("vosk");
        SttEngine whisper = new StubEngine("whisper");
        FakeWatchdog wd = new FakeWatchdog(true, true);
        OrchestrationProperties props =
                new OrchestrationProperties(OrchestrationProperties.PrimaryEngine.VOSK);
        ReconciliationProperties rprops =
                new ReconciliationProperties(true,
                        ReconciliationProperties.Strategy.SIMPLE, 0.6, 0.7);

        // Parallel service returns two engine results
        ParallelSttService.EnginePair pair = new ParallelSttService.EnginePair(
                new EngineResult("a", 0.6, List.of("a"), 10, "vosk", null),
                new EngineResult("b", 0.9, List.of("b"), 20, "whisper", null)
        );
        ParallelSttService parallel = (pcm, timeoutMs) -> pair;

        // Reconciler picks whisper text "b"
        TranscriptReconciler reconciler =
                (v, w) -> TranscriptionResult.of(w.text(), w.confidence(), "reconciled");

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
                .parallelSttService(parallel)
                .transcriptReconciler(reconciler)
                .reconciliationProperties(rprops)
                .metricsPublisher(
                        new com.phillippitts.speaktomack.service.orchestration.TranscriptionMetricsPublisher(null))
                .captureStateMachine(new CaptureStateMachine())
                .engineSelector(new EngineSelectionStrategy(vosk, whisper, wd, props))
                .timingCoordinator(new TimingCoordinator(props))
                .build();

        // Act
        orch.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        orch.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        // Assert
        TranscriptionCompletedEvent evt = (TranscriptionCompletedEvent) events.stream()
                .filter(e -> e instanceof TranscriptionCompletedEvent)
                .findFirst().orElseThrow();
        assertThat(evt.engineUsed()).isEqualTo("reconciled");
        assertThat(evt.result().text()).isEqualTo("b");
    }

    @Test
    void shouldHandlePartialPair() {
        FakeCapture cap = new FakeCapture();
        cap.pcm = new byte[3200];
        SttEngine vosk = new StubEngine("vosk");
        SttEngine whisper = new StubEngine("whisper");
        FakeWatchdog wd = new FakeWatchdog(true, true);
        OrchestrationProperties props =
                new OrchestrationProperties(OrchestrationProperties.PrimaryEngine.VOSK);
        ReconciliationProperties rprops =
                new ReconciliationProperties(true,
                        ReconciliationProperties.Strategy.SIMPLE, 0.6, 0.7);

        // Only whisper succeeds
        ParallelSttService.EnginePair pair = new ParallelSttService.EnginePair(
                null,
                new EngineResult("only-whisper", 0.7, List.of("only","whisper"), 30, "whisper", null)
        );
        ParallelSttService parallel = (pcm, timeoutMs) -> pair;

        // Reconciler returns whisper when vosk null
        TranscriptReconciler reconciler =
                (v, w) -> TranscriptionResult.of(w == null ? "" : w.text(),
                        w == null ? 0.0 : w.confidence(), "reconciled");

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
                .parallelSttService(parallel)
                .transcriptReconciler(reconciler)
                .reconciliationProperties(rprops)
                .metricsPublisher(
                        new com.phillippitts.speaktomack.service.orchestration.TranscriptionMetricsPublisher(null))
                .captureStateMachine(new CaptureStateMachine())
                .engineSelector(new EngineSelectionStrategy(vosk, whisper, wd, props))
                .timingCoordinator(new TimingCoordinator(props))
                .build();
        orch.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        orch.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        TranscriptionCompletedEvent evt = (TranscriptionCompletedEvent) events.stream()
                .filter(e -> e instanceof TranscriptionCompletedEvent)
                .findFirst().orElseThrow();
        assertThat(evt.engineUsed()).isEqualTo("reconciled");
        assertThat(evt.result().text()).isEqualTo("only-whisper");
    }

    @Test
    void shouldTriggerSmartReconciliationWhenVoskConfidenceBelowThreshold() {
        // Arrange
        FakeCapture cap = new FakeCapture();
        cap.pcm = new byte[3200];

        // Vosk returns low confidence (0.5 < threshold 0.6)
        SttEngine vosk = createLowConfidenceVoskEngine();
        SttEngine whisper = new StubEngine("whisper");

        final int[] parallelCallCount = {0};
        List<Object> events = new ArrayList<>();

        DualEngineOrchestrator orch = buildOrchestratorForSmartReconciliation(
                cap, vosk, whisper, parallelCallCount, events);

        // Act
        orch.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        orch.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        // Assert
        // 1. Reconciliation was triggered exactly once (not twice - once for single-engine, once for upgrade)
        assertThat(parallelCallCount[0]).isEqualTo(1);

        // 2. Final result is from reconciliation with high confidence text
        TranscriptionCompletedEvent evt = (TranscriptionCompletedEvent) events.stream()
                .filter(e -> e instanceof TranscriptionCompletedEvent)
                .findFirst().orElseThrow();
        assertThat(evt.engineUsed()).isEqualTo("reconciled");
        assertThat(evt.result().text()).isEqualTo("high-confidence-text");
        assertThat(evt.result().confidence()).isEqualTo(0.9);
    }

    private SttEngine createLowConfidenceVoskEngine() {
        return new SttEngine() {
            @Override
            public void initialize() { }
            @Override
            public TranscriptionResult transcribe(byte[] audioData) {
                return TranscriptionResult.of("low-confidence-text", 0.5, "vosk");
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

    private DualEngineOrchestrator buildOrchestratorForSmartReconciliation(
            FakeCapture cap, SttEngine vosk, SttEngine whisper,
            int[] parallelCallCount, List<Object> events) {
        FakeWatchdog wd = new FakeWatchdog(true, true);
        OrchestrationProperties props =
                new OrchestrationProperties(OrchestrationProperties.PrimaryEngine.VOSK);
        ReconciliationProperties rprops =
                new ReconciliationProperties(true,
                        ReconciliationProperties.Strategy.SIMPLE,
                        0.6,  // confidence threshold
                        0.7); // overlap threshold

        ParallelSttService.EnginePair pair = new ParallelSttService.EnginePair(
                new EngineResult("low-confidence-text", 0.5,
                        List.of("low", "confidence", "text"), 10, "vosk", null),
                new EngineResult("high-confidence-text", 0.9,
                        List.of("high", "confidence", "text"), 20, "whisper", null)
        );
        ParallelSttService parallel = (pcm, timeoutMs) -> {
            parallelCallCount[0]++;
            return pair;
        };

        TranscriptReconciler reconciler =
                (v, w) -> TranscriptionResult.of(w.text(), w.confidence(), "reconciled");

        ApplicationEventPublisher pub = events::add;

        return DualEngineOrchestratorBuilder.builder()
                .captureService(cap)
                .voskEngine(vosk)
                .whisperEngine(whisper)
                .watchdog(wd)
                .orchestrationProperties(props)
                .hotkeyProperties(fakeHotkeyProps())
                .publisher(pub)
                .parallelSttService(parallel)
                .transcriptReconciler(reconciler)
                .reconciliationProperties(rprops)
                .metricsPublisher(
                        new com.phillippitts.speaktomack.service.orchestration.TranscriptionMetricsPublisher(null))
                .captureStateMachine(new CaptureStateMachine())
                .engineSelector(new EngineSelectionStrategy(vosk, whisper, wd, props))
                .timingCoordinator(new TimingCoordinator(props))
                .build();
    }

    // ---- Test fakes reused ----

    static class FakeCapture implements AudioCaptureService {
        byte[] pcm;
        UUID id = UUID.randomUUID();
        boolean stopped;
        @Override
        public UUID startSession() {
            return id;
        }
        @Override
        public void stopSession(UUID sessionId) {
            stopped = true;
        }
        @Override
        public void cancelSession(UUID sessionId) {
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
            return TranscriptionResult.of(name + "-text", 1.0, name);
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
        return new HotkeyProperties(TriggerType.MODIFIER_COMBO, "J", 300,
                java.util.List.of("META"), java.util.List.of(), false);
    }
}
