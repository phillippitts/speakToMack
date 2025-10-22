package com.phillippitts.speaktomack.integration;

import com.phillippitts.speaktomack.config.properties.OrchestrationProperties;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.service.audio.capture.AudioCaptureService;
import com.phillippitts.speaktomack.service.fallback.FallbackManager;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyPressedEvent;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyReleasedEvent;
import com.phillippitts.speaktomack.service.orchestration.event.TranscriptionCompletedEvent;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import com.phillippitts.speaktomack.testutil.EventCapturingPublisher;
import com.phillippitts.speaktomack.testutil.FakeAudioCaptureService;
import com.phillippitts.speaktomack.testutil.FakeSttEngine;
import com.phillippitts.speaktomack.testutil.FakeTypingService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying the full happy-path flow from hotkey events to typing.
 *
 * <p>Flow: HotkeyPressedEvent → Audio Capture → HotkeyReleasedEvent → STT Transcription
 * → TranscriptionCompletedEvent → FallbackManager → TypingService.
 *
 * <p>Uses test doubles from {@link com.phillippitts.speaktomack.testutil} to avoid
 * dependencies on real hardware/binaries.
 *
 * <p><b>Architecture Note:</b> This test uses a simplified {@link TestableOrchestrator}
 * instead of the real {@link com.phillippitts.speaktomack.service.orchestration.DualEngineOrchestrator}
 * to avoid complex watchdog wiring. The real orchestrator is tested separately in unit tests.
 */
class HotkeyToTypingIntegrationTest {

    @Test
    void fullFlowFromHotkeyToTyping() {
        // Setup: Create all components with test doubles
        TestContext ctx = createTestContext("hello world", "backup text");

        // Act: Simulate full hotkey flow
        Instant pressTime = Instant.now();
        Instant releaseTime = pressTime.plusMillis(500);

        // Step 1: User presses hotkey
        ctx.orchestrator.onHotkeyPressed(new HotkeyPressedEvent(pressTime));

        // Verify capture started
        assertThat(ctx.captureService.sessionId).isNotNull();
        assertThat(ctx.captureService.sessionStopped).isFalse();

        // Step 2: User releases hotkey
        ctx.orchestrator.onHotkeyReleased(new HotkeyReleasedEvent(releaseTime));

        // Verify capture stopped
        assertThat(ctx.captureService.sessionStopped).isTrue();

        // Step 3: Verify TranscriptionCompletedEvent was published
        TranscriptionCompletedEvent transcriptionEvent = ctx.publisher.findTranscriptionEvent();
        assertThat(transcriptionEvent).isNotNull();
        assertThat(transcriptionEvent.result().text()).isEqualTo("hello world");
        assertThat(transcriptionEvent.result().confidence()).isEqualTo(0.95);
        assertThat(transcriptionEvent.engineUsed()).isEqualTo("vosk");
        assertThat(transcriptionEvent.timestamp()).isNotNull();

        // Step 4: Simulate FallbackManager receiving the event
        ctx.fallbackManager.onTranscription(transcriptionEvent);

        // Step 5: Verify text was typed via TypingService
        assertThat(ctx.typingService.typedTexts).hasSize(1);
        assertThat(ctx.typingService.typedTexts.get(0)).isEqualTo("hello world");
    }

    @Test
    void fallsBackToSecondaryEngineWhenPrimaryUnhealthy() {
        // Setup: Primary (Vosk) unhealthy, Whisper healthy
        TestContext ctx = createTestContext("unused", "fallback transcription");
        ctx.voskEngine.healthy = false; // Primary unhealthy

        // Act: Simulate hotkey flow
        ctx.orchestrator.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        ctx.orchestrator.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        // Assert: Should use Whisper (secondary) since Vosk (primary) is unhealthy
        TranscriptionCompletedEvent event = ctx.publisher.findTranscriptionEvent();
        assertThat(event).isNotNull();
        assertThat(event.result().text()).isEqualTo("fallback transcription");
        assertThat(event.engineUsed()).isEqualTo("whisper");

        // Verify text was typed
        ctx.fallbackManager.onTranscription(event);
        assertThat(ctx.typingService.typedTexts).containsExactly("fallback transcription");
    }

    @Test
    void handlesMultipleHotkeyPresses() {
        // Setup
        TestContext ctx = createTestContext("first", "unused");

        // Act: Press 1 → Release 1 → Press 2 → Release 2
        ctx.orchestrator.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        UUID session1 = ctx.captureService.sessionId;
        ctx.orchestrator.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        // Process first transcription
        TranscriptionCompletedEvent event1 = ctx.publisher.findTranscriptionEvent();
        ctx.fallbackManager.onTranscription(event1);

        ctx.publisher.clear(); // Clear for second press

        // Change canned text for second press
        ctx.voskEngine.cannedText = "second";
        ctx.orchestrator.onHotkeyPressed(new HotkeyPressedEvent(Instant.now()));
        UUID session2 = ctx.captureService.sessionId;
        ctx.orchestrator.onHotkeyReleased(new HotkeyReleasedEvent(Instant.now()));

        // Process second transcription
        TranscriptionCompletedEvent event2 = ctx.publisher.findTranscriptionEvent();
        ctx.fallbackManager.onTranscription(event2);

        // Assert: Both sessions processed independently
        assertThat(session1).isNotEqualTo(session2);
        assertThat(ctx.typingService.typedTexts).containsExactly("first", "second");
    }

    /**
     * Creates a test context with all necessary components wired together.
     *
     * @param voskText text that Vosk engine will return
     * @param whisperText text that Whisper engine will return
     * @return fully configured test context
     */
    private TestContext createTestContext(String voskText, String whisperText) {
        EventCapturingPublisher publisher = new EventCapturingPublisher();
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        FakeSttEngine voskEngine = new FakeSttEngine("vosk", voskText, 0.95);
        FakeSttEngine whisperEngine = new FakeSttEngine("whisper", whisperText, 0.95);
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

        return new TestContext(
                publisher,
                captureService,
                voskEngine,
                whisperEngine,
                orchestrator,
                typingService,
                fallbackManager
        );
    }

    /**
     * Test context holding all components for integration testing.
     */
    private record TestContext(
            EventCapturingPublisher publisher,
            FakeAudioCaptureService captureService,
            FakeSttEngine voskEngine,
            FakeSttEngine whisperEngine,
            TestableOrchestrator orchestrator,
            FakeTypingService typingService,
            FallbackManager fallbackManager
    ) {
    }

    /**
     * Simplified orchestrator for integration testing without full watchdog wiring.
     *
     * <p><b>Why this exists:</b> The real
     * {@link com.phillippitts.speaktomack.service.orchestration.DualEngineOrchestrator}
     * requires a fully configured
     * {@link com.phillippitts.speaktomack.service.stt.watchdog.SttEngineWatchdog},
     * which is a complex Spring component with its own lifecycle and dependencies. For integration
     * testing the happy path, we use this simplified version that checks engine health directly.
     *
     * <p><b>Differences from production:</b>
     * <ul>
     *   <li>No watchdog dependency - uses {@link SttEngine#isHealthy()} directly</li>
     *   <li>No reconciliation support - single engine selection only</li>
     *   <li>Simplified error handling - swallows exceptions for test clarity</li>
     * </ul>
     *
     * <p><b>Coverage:</b> The real orchestrator is tested separately in unit tests with proper
     * watchdog mocking. This class focuses on the end-to-end happy path.
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
                    return; // Ignore duplicate presses
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
                SttEngine engine = selectEngine();
                TranscriptionResult result = engine.transcribe(pcm);
                publisher.publishEvent(new TranscriptionCompletedEvent(
                        result,
                        Instant.now(),
                        engine.getEngineName()
                ));
            } catch (Exception e) {
                // Swallow exceptions in test orchestrator for clarity
            }
        }

        /**
         * Selects engine based on health status (simplified vs. production watchdog logic).
         */
        private SttEngine selectEngine() {
            return primaryEngine.isHealthy() ? primaryEngine : secondaryEngine;
        }
    }
}
