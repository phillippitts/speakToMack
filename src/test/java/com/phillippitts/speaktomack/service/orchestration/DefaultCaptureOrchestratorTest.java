package com.phillippitts.speaktomack.service.orchestration;

import com.phillippitts.speaktomack.service.audio.capture.AudioCaptureService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DefaultCaptureOrchestrator}.
 */
class DefaultCaptureOrchestratorTest {

    @Test
    void shouldStartCaptureWhenNotActive() {
        // Arrange
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        CaptureStateMachine stateMachine = new CaptureStateMachine();
        DefaultCaptureOrchestrator orchestrator = new DefaultCaptureOrchestrator(captureService, stateMachine);

        // Act
        UUID sessionId = orchestrator.startCapture();

        // Assert
        assertThat(sessionId).isNotNull();
        assertThat(orchestrator.isCapturing()).isTrue();
        assertThat(captureService.startSessionCallCount).isEqualTo(1);
    }

    @Test
    void shouldReturnNullWhenStartCaptureCalledWhileActive() {
        // Arrange
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        CaptureStateMachine stateMachine = new CaptureStateMachine();
        DefaultCaptureOrchestrator orchestrator = new DefaultCaptureOrchestrator(captureService, stateMachine);

        UUID firstSession = orchestrator.startCapture();
        assertThat(firstSession).isNotNull();

        // Act
        UUID secondSession = orchestrator.startCapture();

        // Assert
        assertThat(secondSession).isNull();
        assertThat(orchestrator.isCapturing()).isTrue();
        assertThat(captureService.startSessionCallCount).isEqualTo(2); // Called but rejected by state machine
        assertThat(captureService.cancelSessionCallCount).isEqualTo(1); // Second session cancelled
    }

    @Test
    void shouldStopCaptureAndReturnAudioData() {
        // Arrange
        byte[] expectedAudio = new byte[]{1, 2, 3, 4};
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        captureService.setAudioData(expectedAudio);
        CaptureStateMachine stateMachine = new CaptureStateMachine();
        DefaultCaptureOrchestrator orchestrator = new DefaultCaptureOrchestrator(captureService, stateMachine);

        UUID sessionId = orchestrator.startCapture();
        assertThat(sessionId).isNotNull();

        // Act
        byte[] audio = orchestrator.stopCapture(sessionId);

        // Assert
        assertThat(audio).isEqualTo(expectedAudio);
        assertThat(orchestrator.isCapturing()).isFalse();
        assertThat(captureService.stopSessionCallCount).isEqualTo(1);
        assertThat(captureService.readAllCallCount).isEqualTo(1);
    }

    @Test
    void shouldReturnNullWhenStopCaptureCalledWithNullSessionId() {
        // Arrange
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        CaptureStateMachine stateMachine = new CaptureStateMachine();
        DefaultCaptureOrchestrator orchestrator = new DefaultCaptureOrchestrator(captureService, stateMachine);

        // Act
        byte[] audio = orchestrator.stopCapture(null);

        // Assert
        assertThat(audio).isNull();
        assertThat(captureService.stopSessionCallCount).isEqualTo(0);
    }

    @Test
    void shouldReturnNullWhenStopCaptureCalledWithMismatchedSessionId() {
        // Arrange
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        CaptureStateMachine stateMachine = new CaptureStateMachine();
        DefaultCaptureOrchestrator orchestrator = new DefaultCaptureOrchestrator(captureService, stateMachine);

        UUID activeSession = orchestrator.startCapture();
        assertThat(activeSession).isNotNull();

        UUID differentSessionId = UUID.randomUUID();

        // Act
        byte[] audio = orchestrator.stopCapture(differentSessionId);

        // Assert
        assertThat(audio).isNull();
        assertThat(orchestrator.isCapturing()).isTrue(); // Still capturing with original session
        assertThat(captureService.stopSessionCallCount).isEqualTo(0);
    }

    @Test
    void shouldCancelSessionWhenAudioReadFails() {
        // Arrange
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        captureService.setReadAllException(new RuntimeException("Audio device error"));
        CaptureStateMachine stateMachine = new CaptureStateMachine();
        DefaultCaptureOrchestrator orchestrator = new DefaultCaptureOrchestrator(captureService, stateMachine);

        UUID sessionId = orchestrator.startCapture();
        assertThat(sessionId).isNotNull();

        // Act
        byte[] audio = orchestrator.stopCapture(sessionId);

        // Assert
        assertThat(audio).isNull();
        assertThat(orchestrator.isCapturing()).isFalse();
        assertThat(captureService.cancelSessionCallCount).isEqualTo(1);
    }

    @Test
    void shouldCancelSpecifiedSession() {
        // Arrange
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        CaptureStateMachine stateMachine = new CaptureStateMachine();
        DefaultCaptureOrchestrator orchestrator = new DefaultCaptureOrchestrator(captureService, stateMachine);

        UUID sessionId = orchestrator.startCapture();
        assertThat(sessionId).isNotNull();

        // Act
        orchestrator.cancelCapture(sessionId);

        // Assert
        assertThat(orchestrator.isCapturing()).isFalse();
        assertThat(captureService.cancelSessionCallCount).isEqualTo(1);
    }

    @Test
    void shouldCancelActiveSessionWhenCancelCalledWithNull() {
        // Arrange
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        CaptureStateMachine stateMachine = new CaptureStateMachine();
        DefaultCaptureOrchestrator orchestrator = new DefaultCaptureOrchestrator(captureService, stateMachine);

        UUID sessionId = orchestrator.startCapture();
        assertThat(sessionId).isNotNull();

        // Act
        orchestrator.cancelCapture(null);

        // Assert
        assertThat(orchestrator.isCapturing()).isFalse();
        assertThat(captureService.cancelSessionCallCount).isEqualTo(1);
    }

    @Test
    void shouldHandleCancelWhenNoActiveSession() {
        // Arrange
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        CaptureStateMachine stateMachine = new CaptureStateMachine();
        DefaultCaptureOrchestrator orchestrator = new DefaultCaptureOrchestrator(captureService, stateMachine);

        // Act
        orchestrator.cancelCapture(UUID.randomUUID());

        // Assert
        assertThat(orchestrator.isCapturing()).isFalse();
        assertThat(captureService.cancelSessionCallCount).isEqualTo(0);
    }

    @Test
    void shouldReturnCapturingStateFromStateMachine() {
        // Arrange
        FakeAudioCaptureService captureService = new FakeAudioCaptureService();
        CaptureStateMachine stateMachine = new CaptureStateMachine();
        DefaultCaptureOrchestrator orchestrator = new DefaultCaptureOrchestrator(captureService, stateMachine);

        // Assert - initially not capturing
        assertThat(orchestrator.isCapturing()).isFalse();

        // Start capture
        UUID sessionId = orchestrator.startCapture();
        assertThat(orchestrator.isCapturing()).isTrue();

        // Stop capture
        orchestrator.stopCapture(sessionId);
        assertThat(orchestrator.isCapturing()).isFalse();
    }

    /**
     * Fake implementation of AudioCaptureService for testing.
     */
    static class FakeAudioCaptureService implements AudioCaptureService {
        int startSessionCallCount = 0;
        int stopSessionCallCount = 0;
        int cancelSessionCallCount = 0;
        int readAllCallCount = 0;

        private byte[] audioData = new byte[0];
        private RuntimeException readAllException;
        private final Map<UUID, byte[]> sessions = new HashMap<>();

        void setAudioData(byte[] data) {
            this.audioData = data;
        }

        void setReadAllException(RuntimeException exception) {
            this.readAllException = exception;
        }

        @Override
        public UUID startSession() {
            startSessionCallCount++;
            UUID sessionId = UUID.randomUUID();
            sessions.put(sessionId, audioData);
            return sessionId;
        }

        @Override
        public void stopSession(UUID sessionId) {
            stopSessionCallCount++;
        }

        @Override
        public void cancelSession(UUID sessionId) {
            cancelSessionCallCount++;
            sessions.remove(sessionId);
        }

        @Override
        public byte[] readAll(UUID sessionId) {
            readAllCallCount++;
            if (readAllException != null) {
                throw readAllException;
            }
            return sessions.get(sessionId);
        }
    }
}
