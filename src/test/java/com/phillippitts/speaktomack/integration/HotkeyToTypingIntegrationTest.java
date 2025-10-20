package com.phillippitts.speaktomack.integration;

import com.phillippitts.speaktomack.config.orchestration.OrchestrationProperties;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import com.phillippitts.speaktomack.service.audio.capture.AudioCaptureService;
import com.phillippitts.speaktomack.service.fallback.FallbackManager;
import com.phillippitts.speaktomack.service.fallback.TypingService;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyPressedEvent;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyReleasedEvent;
import com.phillippitts.speaktomack.service.orchestration.event.TranscriptionCompletedEvent;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying the full happy-path flow from hotkey events to typing.
 *
 * <p>Flow: HotkeyPressedEvent → Audio Capture → HotkeyReleasedEvent → STT Transcription
 * → TranscriptionCompletedEvent → FallbackManager → TypingService.
 *
 * <p>Uses test doubles (fakes) to avoid dependencies on real hardware/binaries.
 */
class HotkeyToTypingIntegrationTest {

    /** Captures all published events for verification. */
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
    }

    /** Fake audio capture that returns canned PCM data. */
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

    /** Fake STT engine that returns canned transcription. */
    static class FakeSttEngine implements SttEngine {
        private final String engineName;
        String cannedText; // Mutable for test scenarios
        boolean healthy = true;

        FakeSttEngine(String name, String text) {
            this.engineName = name;
            this.cannedText = text;
        }

        @Override
        public void initialize() {
            // No-op for fake
        }

        @Override
        public TranscriptionResult transcribe(byte[] pcmData) throws TranscriptionException {
            if (!healthy) {
                throw new TranscriptionException("Engine unhealthy");
            }
            return new TranscriptionResult(cannedText, 0.95, Instant.now(), engineName);
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

    /** Fake typing service that records typed text. */
    static class FakeTypingService implements TypingService {
        final List<String> typedTexts = new CopyOnWriteArrayList<>();

        @Override
        public boolean paste(String text) {
            typedTexts.add(text);
            return true;
        }
    }

    /**
     * Minimal fake watchdog for integration test - simply returns true for enabled engines.
     * Note: In production, SttEngineWatchdog is a complex @Component with failure tracking.
     */
    static class MinimalWatchdogFake {
        boolean isEngineEnabled(String engineName) {
            return true; // All engines enabled in test
        }
    }

    @Test
    void fullFlowFromHotkeyToTyping() {
        // Setup: Create all components with test doubles
        EventCapturingPublisher publisher = new EventCapturingPublisher();
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        FakeSttEngine voskEngine = new FakeSttEngine("vosk", "hello world");
        FakeSttEngine whisperEngine = new FakeSttEngine("whisper", "backup text");

        // Note: We can't fake SttEngineWatchdog easily since it's a concrete class
        // For this integration test, we'll use a lambda-based watchdog checker
        OrchestrationProperties orchProps = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK
        );

        // Create a simple watchdog-aware orchestrator test
        // Since we can't easily inject a fake watchdog, we'll test with engines directly
        TestableOrchestrator orchestrator = new TestableOrchestrator(
                captureService,
                voskEngine,
                whisperEngine,
                orchProps,
                publisher
        );

        FakeTypingService typingService = new FakeTypingService();
        FallbackManager fallbackManager = new FallbackManager(typingService);

        // Act: Simulate full hotkey flow
        Instant pressTime = Instant.now();
        Instant releaseTime = pressTime.plusMillis(500);

        // Step 1: User presses hotkey
        orchestrator.onHotkeyPressed(new HotkeyPressedEvent(pressTime));

        // Verify capture started
        assertThat(captureService.sessionId).isNotNull();
        assertThat(captureService.sessionStopped).isFalse();

        // Step 2: User releases hotkey
        orchestrator.onHotkeyReleased(new HotkeyReleasedEvent(releaseTime));

        // Verify capture stopped
        assertThat(captureService.sessionStopped).isTrue();

        // Step 3: Verify TranscriptionCompletedEvent was published
        TranscriptionCompletedEvent transcriptionEvent = publisher.findTranscriptionEvent();
        assertThat(transcriptionEvent).isNotNull();
        assertThat(transcriptionEvent.result().text()).isEqualTo("hello world");
        assertThat(transcriptionEvent.result().confidence()).isEqualTo(0.95);
        assertThat(transcriptionEvent.engineUsed()).isEqualTo("vosk");
        assertThat(transcriptionEvent.timestamp()).isNotNull();

        // Step 4: Simulate FallbackManager receiving the event
        fallbackManager.onTranscription(transcriptionEvent);

        // Step 5: Verify text was typed via TypingService
        assertThat(typingService.typedTexts).hasSize(1);
        assertThat(typingService.typedTexts.get(0)).isEqualTo("hello world");
    }

    /**
     * Simplified orchestrator for testing without full watchdog wiring.
     * Uses engines directly and checks isHealthy() instead of watchdog.
     */
    static class TestableOrchestrator {
        private final AudioCaptureService captureService;
        private final SttEngine primaryEngine;
        private final SttEngine secondaryEngine;
        private final ApplicationEventPublisher publisher;
        private final Object lock = new Object();
        private UUID activeSession;

        TestableOrchestrator(AudioCaptureService captureService,
                            SttEngine primary,
                            SttEngine secondary,
                            OrchestrationProperties props,
                            ApplicationEventPublisher publisher) {
            this.captureService = captureService;
            this.primaryEngine = primary;
            this.secondaryEngine = secondary;
            this.publisher = publisher;
        }

        void onHotkeyPressed(HotkeyPressedEvent evt) {
            synchronized (lock) {
                if (activeSession != null) {
                    return;
                }
                activeSession = captureService.startSession();
            }
        }

        void onHotkeyReleased(HotkeyReleasedEvent evt) {
            UUID session;
            synchronized (lock) {
                session = activeSession;
                if (session == null) {
                    return;
                }
                captureService.stopSession(session);
                activeSession = null;
            }

            try {
                byte[] pcm = captureService.readAll(session);
                SttEngine engine = primaryEngine.isHealthy() ? primaryEngine : secondaryEngine;
                TranscriptionResult result = engine.transcribe(pcm);
                publisher.publishEvent(new TranscriptionCompletedEvent(
                        result,
                        Instant.now(),
                        engine.getEngineName()
                ));
            } catch (Exception e) {
                // Ignore errors in test
            }
        }
    }

    @Test
    void fallsBackToSecondaryEngineWhenPrimaryUnhealthy() {
        // Setup: Primary (Vosk) unhealthy, Whisper healthy
        EventCapturingPublisher publisher = new EventCapturingPublisher();
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        FakeSttEngine voskEngine = new FakeSttEngine("vosk", "unused");
        voskEngine.healthy = false; // Primary unhealthy
        FakeSttEngine whisperEngine = new FakeSttEngine("whisper", "fallback transcription");
        OrchestrationProperties orchProps = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK
        );

        TestableOrchestrator orchestrator = new TestableOrchestrator(
                captureService,
                voskEngine,
                whisperEngine,
                orchProps,
                publisher
        );

        FakeTypingService typingService = new FakeTypingService();
        FallbackManager fallbackManager = new FallbackManager(typingService);

        // Act: Simulate hotkey flow
        orchestrator.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        orchestrator.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        // Assert: Should use Whisper (secondary) since Vosk (primary) is unhealthy
        TranscriptionCompletedEvent event = publisher.findTranscriptionEvent();
        assertThat(event).isNotNull();
        assertThat(event.result().text()).isEqualTo("fallback transcription");
        assertThat(event.engineUsed()).isEqualTo("whisper");

        // Verify text was typed
        fallbackManager.onTranscription(event);
        assertThat(typingService.typedTexts).containsExactly("fallback transcription");
    }

    @Test
    void handlesMultipleHotkeyPresses() {
        // Setup
        EventCapturingPublisher publisher = new EventCapturingPublisher();
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        FakeSttEngine voskEngine = new FakeSttEngine("vosk", "first");
        FakeSttEngine whisperEngine = new FakeSttEngine("whisper", "unused");
        OrchestrationProperties orchProps = new OrchestrationProperties(
                OrchestrationProperties.PrimaryEngine.VOSK
        );

        TestableOrchestrator orchestrator = new TestableOrchestrator(
                captureService,
                voskEngine,
                whisperEngine,
                orchProps,
                publisher
        );

        FakeTypingService typingService = new FakeTypingService();
        FallbackManager fallbackManager = new FallbackManager(typingService);

        // Act: Press 1 → Release 1 → Press 2 → Release 2
        orchestrator.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        UUID session1 = captureService.sessionId;
        orchestrator.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        // Process first transcription
        TranscriptionCompletedEvent event1 = publisher.findTranscriptionEvent();
        fallbackManager.onTranscription(event1);

        publisher.events.clear(); // Clear for second press

        // Change canned text for second press
        voskEngine.cannedText = "second";
        orchestrator.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        UUID session2 = captureService.sessionId;
        orchestrator.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        // Process second transcription
        TranscriptionCompletedEvent event2 = publisher.findTranscriptionEvent();
        fallbackManager.onTranscription(event2);

        // Assert: Both sessions processed independently
        assertThat(session1).isNotEqualTo(session2);
        assertThat(typingService.typedTexts).containsExactly("first", "second");
    }
}
