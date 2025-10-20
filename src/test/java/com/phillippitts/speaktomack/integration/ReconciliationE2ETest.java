package com.phillippitts.speaktomack.integration;

import com.phillippitts.speaktomack.config.orchestration.OrchestrationProperties;
import com.phillippitts.speaktomack.config.reconcile.ReconciliationProperties;
import com.phillippitts.speaktomack.config.stt.SttWatchdogProperties;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import com.phillippitts.speaktomack.service.audio.capture.AudioCaptureService;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyPressedEvent;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyReleasedEvent;
import com.phillippitts.speaktomack.service.orchestration.DualEngineOrchestrator;
import com.phillippitts.speaktomack.service.orchestration.event.TranscriptionCompletedEvent;
import com.phillippitts.speaktomack.service.reconcile.TranscriptReconciler;
import com.phillippitts.speaktomack.service.reconcile.impl.ConfidenceReconciler;
import com.phillippitts.speaktomack.service.reconcile.impl.SimplePreferenceReconciler;
import com.phillippitts.speaktomack.service.reconcile.impl.WordOverlapReconciler;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import com.phillippitts.speaktomack.service.stt.parallel.DefaultParallelSttService;
import com.phillippitts.speaktomack.service.stt.parallel.ParallelSttService;
import com.phillippitts.speaktomack.service.stt.watchdog.SttEngineWatchdog;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for Phase 4 reconciliation.
 *
 * <p>Tests the full pipeline: HotkeyPressed → AudioCapture → HotkeyReleased
 * → ParallelSTT → Reconciliation → TranscriptionCompletedEvent
 *
 * <p>Validates each reconciliation strategy and memory leak prevention.
 */
class ReconciliationE2ETest {

    /** Event capturing publisher for test verification */
    static class EventCapturingPublisher implements ApplicationEventPublisher {
        final List<Object> events = new CopyOnWriteArrayList<>();

        @Override
        public void publishEvent(ApplicationEvent event) {
            events.add(event);
        }

        @Override
        public void publishEvent(Object event) {
            events.add(event);
        }

        TranscriptionCompletedEvent findTranscriptionEvent() {
            return events.stream()
                    .filter(e -> e instanceof TranscriptionCompletedEvent)
                    .map(e -> (TranscriptionCompletedEvent) e)
                    .findFirst()
                    .orElse(null);
        }

        void clear() {
            events.clear();
        }
    }

    /** Fake audio capture service returning canned PCM data */
    static class FakeAudioCaptureService implements AudioCaptureService {
        UUID sessionId;
        boolean sessionStopped;

        @Override
        public UUID startSession() {
            sessionId = UUID.randomUUID();
            sessionStopped = false;
            return sessionId;
        }

        @Override
        public void stopSession(UUID id) {
            if (id.equals(sessionId)) {
                sessionStopped = true;
            }
        }

        @Override
        public byte[] readAll(UUID id) {
            if (id.equals(sessionId) && sessionStopped) {
                // Return fake PCM data (16-bit, 16kHz, mono, 1 second = 32000 bytes)
                return new byte[32000];
            }
            throw new IllegalStateException("Session not stopped or ID mismatch");
        }

        @Override
        public void cancelSession(UUID id) {
            sessionId = null;
        }
    }

    /** Fake STT engine with configurable transcription output */
    static class FakeSttEngine implements SttEngine {
        private final String engineName;
        private final String cannedText;
        private final double cannedConfidence;
        boolean healthy = true;

        FakeSttEngine(String name, String text, double confidence) {
            this.engineName = name;
            this.cannedText = text;
            this.cannedConfidence = confidence;
        }

        @Override
        public void initialize() {
            // No-op for fake
        }

        @Override
        public TranscriptionResult transcribe(byte[] pcmData) throws TranscriptionException {
            if (!healthy) {
                throw new TranscriptionException("Engine unhealthy", engineName);
            }
            return new TranscriptionResult(cannedText, cannedConfidence, Instant.now(), engineName);
        }

        @Override
        public boolean isHealthy() {
            return healthy;
        }

        @Override
        public String getEngineName() {
            return engineName;
        }

        @Override
        public void close() {
            // No-op for fake
        }
    }

    /** Helper to create a real watchdog for testing */
    static SttEngineWatchdog createWatchdog(SttEngine vosk, SttEngine whisper,
                                             ApplicationEventPublisher publisher) {
        SttWatchdogProperties props = new SttWatchdogProperties();
        props.setEnabled(true);
        props.setWindowMinutes(60);
        props.setMaxRestartsPerWindow(3);
        props.setCooldownMinutes(10);
        return new SttEngineWatchdog(List.of(vosk, whisper), props, publisher);
    }

    /** Synchronous executor for predictable test execution */
    static class SyncExecutor implements Executor {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    @Test
    void wordOverlapStrategySelectsHigherOverlap() {
        // Setup: Vosk returns "hello", Whisper returns "hello world"
        // Expected: Whisper has higher Jaccard similarity (2/2 vs 1/2)
        EventCapturingPublisher publisher = new EventCapturingPublisher();
        FakeAudioCaptureService capture = new FakeAudioCaptureService();
        FakeSttEngine vosk = new FakeSttEngine("vosk", "hello", 0.9);
        FakeSttEngine whisper = new FakeSttEngine("whisper", "hello world", 0.9);

        ReconciliationProperties recProps = new ReconciliationProperties(
                true, ReconciliationProperties.Strategy.OVERLAP, 0.5);

        ParallelSttService parallel = new DefaultParallelSttService(vosk, whisper,
                new SyncExecutor(), 10000);
        TranscriptReconciler reconciler = new WordOverlapReconciler(0.5);

        DualEngineOrchestrator orchestrator = new DualEngineOrchestrator(
                capture, vosk, whisper, createWatchdog(vosk, whisper, publisher),
                new OrchestrationProperties(OrchestrationProperties.PrimaryEngine.VOSK),
                publisher, parallel, reconciler, recProps
        );

        // Act: Full hotkey flow
        orchestrator.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        orchestrator.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        // Assert: Reconciliation picked Whisper (higher overlap)
        TranscriptionCompletedEvent event = publisher.findTranscriptionEvent();
        assertThat(event).isNotNull();
        assertThat(event.engineUsed()).isEqualTo("reconciled");
        assertThat(event.result().text()).isEqualTo("hello world");
        assertThat(event.result().confidence()).isEqualTo(0.9);
    }

    @Test
    void confidenceStrategySelectsHigherConfidence() {
        // Setup: Vosk 0.85 confidence, Whisper 0.95 confidence
        // Expected: Whisper selected due to higher confidence
        EventCapturingPublisher publisher = new EventCapturingPublisher();
        FakeAudioCaptureService capture = new FakeAudioCaptureService();
        FakeSttEngine vosk = new FakeSttEngine("vosk", "vosk text", 0.85);
        FakeSttEngine whisper = new FakeSttEngine("whisper", "whisper text", 0.95);

        ReconciliationProperties recProps = new ReconciliationProperties(
                true, ReconciliationProperties.Strategy.CONFIDENCE, 0.6);

        ParallelSttService parallel = new DefaultParallelSttService(vosk, whisper,
                new SyncExecutor(), 10000);
        TranscriptReconciler reconciler = new ConfidenceReconciler();

        DualEngineOrchestrator orchestrator = new DualEngineOrchestrator(
                capture, vosk, whisper, createWatchdog(vosk, whisper, publisher),
                new OrchestrationProperties(OrchestrationProperties.PrimaryEngine.VOSK),
                publisher, parallel, reconciler, recProps
        );

        // Act
        orchestrator.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        orchestrator.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        // Assert
        TranscriptionCompletedEvent event = publisher.findTranscriptionEvent();
        assertThat(event).isNotNull();
        assertThat(event.engineUsed()).isEqualTo("reconciled");
        assertThat(event.result().text()).isEqualTo("whisper text");
        assertThat(event.result().confidence()).isEqualTo(0.95);
    }

    @Test
    void preferenceStrategyRespectsVoskPrimary() {
        // Setup: Primary=VOSK, both engines return non-empty text
        // Expected: Vosk selected per preference strategy
        EventCapturingPublisher publisher = new EventCapturingPublisher();
        FakeAudioCaptureService capture = new FakeAudioCaptureService();
        FakeSttEngine vosk = new FakeSttEngine("vosk", "vosk text", 0.9);
        FakeSttEngine whisper = new FakeSttEngine("whisper", "whisper text", 0.95);

        ReconciliationProperties recProps = new ReconciliationProperties(
                true, ReconciliationProperties.Strategy.SIMPLE, 0.6);

        ParallelSttService parallel = new DefaultParallelSttService(vosk, whisper,
                new SyncExecutor(), 10000);
        TranscriptReconciler reconciler = new SimplePreferenceReconciler(
                OrchestrationProperties.PrimaryEngine.VOSK
        );

        DualEngineOrchestrator orchestrator = new DualEngineOrchestrator(
                capture, vosk, whisper, createWatchdog(vosk, whisper, publisher),
                new OrchestrationProperties(OrchestrationProperties.PrimaryEngine.VOSK),
                publisher, parallel, reconciler, recProps
        );

        // Act
        orchestrator.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        orchestrator.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        // Assert
        TranscriptionCompletedEvent event = publisher.findTranscriptionEvent();
        assertThat(event).isNotNull();
        assertThat(event.engineUsed()).isEqualTo("reconciled");
        assertThat(event.result().text()).isEqualTo("vosk text");
        assertThat(event.result().confidence()).isEqualTo(0.9);
    }

    @Test
    void preferenceStrategyRespectsWhisperPrimary() {
        // Setup: Primary=WHISPER, both engines return non-empty text
        // Expected: Whisper selected per preference strategy
        EventCapturingPublisher publisher = new EventCapturingPublisher();
        FakeAudioCaptureService capture = new FakeAudioCaptureService();
        FakeSttEngine vosk = new FakeSttEngine("vosk", "vosk text", 0.9);
        FakeSttEngine whisper = new FakeSttEngine("whisper", "whisper text", 0.95);

        ReconciliationProperties recProps = new ReconciliationProperties(
                true, ReconciliationProperties.Strategy.SIMPLE, 0.6);

        ParallelSttService parallel = new DefaultParallelSttService(vosk, whisper,
                new SyncExecutor(), 10000);
        TranscriptReconciler reconciler = new SimplePreferenceReconciler(
                OrchestrationProperties.PrimaryEngine.WHISPER
        );

        DualEngineOrchestrator orchestrator = new DualEngineOrchestrator(
                capture, vosk, whisper, createWatchdog(vosk, whisper, publisher),
                new OrchestrationProperties(OrchestrationProperties.PrimaryEngine.WHISPER),
                publisher, parallel, reconciler, recProps
        );

        // Act
        orchestrator.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        orchestrator.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        // Assert
        TranscriptionCompletedEvent event = publisher.findTranscriptionEvent();
        assertThat(event).isNotNull();
        assertThat(event.engineUsed()).isEqualTo("reconciled");
        assertThat(event.result().text()).isEqualTo("whisper text");
        assertThat(event.result().confidence()).isEqualTo(0.95);
    }

    @Test
    void reconciliationHandlesOneEngineFailing() {
        // Setup: Vosk fails, Whisper succeeds
        // Expected: Whisper result selected
        EventCapturingPublisher publisher = new EventCapturingPublisher();
        FakeAudioCaptureService capture = new FakeAudioCaptureService();
        FakeSttEngine vosk = new FakeSttEngine("vosk", "unused", 0.9);
        vosk.healthy = false; // Make Vosk fail
        FakeSttEngine whisper = new FakeSttEngine("whisper", "whisper text", 0.95);

        ReconciliationProperties recProps = new ReconciliationProperties(
                true, ReconciliationProperties.Strategy.CONFIDENCE, 0.6);

        ParallelSttService parallel = new DefaultParallelSttService(vosk, whisper,
                new SyncExecutor(), 10000);
        TranscriptReconciler reconciler = new ConfidenceReconciler();

        DualEngineOrchestrator orchestrator = new DualEngineOrchestrator(
                capture, vosk, whisper, createWatchdog(vosk, whisper, publisher),
                new OrchestrationProperties(OrchestrationProperties.PrimaryEngine.VOSK),
                publisher, parallel, reconciler, recProps
        );

        // Act
        orchestrator.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        orchestrator.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        // Assert: Should still get Whisper result
        TranscriptionCompletedEvent event = publisher.findTranscriptionEvent();
        assertThat(event).isNotNull();
        assertThat(event.engineUsed()).isEqualTo("reconciled");
        assertThat(event.result().text()).isEqualTo("whisper text");
    }

    @Test
    void reconciliationMemoryLeakTest() {
        // Setup: Run 100 iterations to verify no memory leaks
        EventCapturingPublisher publisher = new EventCapturingPublisher();
        FakeAudioCaptureService capture = new FakeAudioCaptureService();
        FakeSttEngine vosk = new FakeSttEngine("vosk", "test text", 0.9);
        FakeSttEngine whisper = new FakeSttEngine("whisper", "test text", 0.95);

        ReconciliationProperties recProps = new ReconciliationProperties(
                true, ReconciliationProperties.Strategy.CONFIDENCE, 0.6);

        ParallelSttService parallel = new DefaultParallelSttService(vosk, whisper,
                new SyncExecutor(), 10000);
        TranscriptReconciler reconciler = new ConfidenceReconciler();

        DualEngineOrchestrator orchestrator = new DualEngineOrchestrator(
                capture, vosk, whisper, createWatchdog(vosk, whisper, publisher),
                new OrchestrationProperties(OrchestrationProperties.PrimaryEngine.VOSK),
                publisher, parallel, reconciler, recProps
        );

        // Measure memory before
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // Act: Run 100 iterations
        for (int i = 0; i < 100; i++) {
            orchestrator.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
            orchestrator.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));
            publisher.clear(); // Clear events to prevent publisher from hoarding memory
        }

        // Measure memory after
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long growthMB = (memoryAfter - memoryBefore) / 1024 / 1024;

        // Assert: Memory growth should be less than 50MB
        assertThat(growthMB).isLessThan(50);
    }

    @Test
    void reconciliationDisabledFallsBackToSingleEngine() {
        // Setup: Reconciliation disabled
        // Expected: Single-engine mode (no "reconciled" engine name)
        EventCapturingPublisher publisher = new EventCapturingPublisher();
        FakeAudioCaptureService capture = new FakeAudioCaptureService();
        FakeSttEngine vosk = new FakeSttEngine("vosk", "vosk text", 0.9);
        FakeSttEngine whisper = new FakeSttEngine("whisper", "whisper text", 0.95);

        ReconciliationProperties recProps = new ReconciliationProperties(
                false, ReconciliationProperties.Strategy.CONFIDENCE, 0.6); // Disabled

        ParallelSttService parallel = new DefaultParallelSttService(vosk, whisper,
                new SyncExecutor(), 10000);
        TranscriptReconciler reconciler = new ConfidenceReconciler();

        DualEngineOrchestrator orchestrator = new DualEngineOrchestrator(
                capture, vosk, whisper, createWatchdog(vosk, whisper, publisher),
                new OrchestrationProperties(OrchestrationProperties.PrimaryEngine.VOSK),
                publisher, parallel, reconciler, recProps
        );

        // Act
        orchestrator.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        orchestrator.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        // Assert: Should use single engine (vosk, since it's primary)
        TranscriptionCompletedEvent event = publisher.findTranscriptionEvent();
        assertThat(event).isNotNull();
        assertThat(event.engineUsed()).isEqualTo("vosk"); // NOT "reconciled"
        assertThat(event.result().text()).isEqualTo("vosk text");
    }
}
