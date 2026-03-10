package com.phillippitts.blckvox.service.orchestration;

import com.phillippitts.blckvox.domain.TranscriptionResult;
import com.phillippitts.blckvox.service.orchestration.event.TranscriptionCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultRecordingServiceTest {

    private FakeCaptureOrchestrator captureOrchestrator;
    private FakeTranscriptionOrchestrator transcriptionOrchestrator;
    private List<Object> events;
    private DefaultRecordingService service;

    @BeforeEach
    void setUp() {
        captureOrchestrator = new FakeCaptureOrchestrator();
        transcriptionOrchestrator = new FakeTranscriptionOrchestrator();
        events = new ArrayList<>();
        ApplicationStateTracker stateTracker = new ApplicationStateTracker(events::add);
        service = new DefaultRecordingService(captureOrchestrator, transcriptionOrchestrator, stateTracker);
    }

    @Test
    void startRecordingSucceeds() {
        boolean started = service.startRecording();

        assertThat(started).isTrue();
        assertThat(service.isRecording()).isTrue();
        assertThat(service.getState()).isEqualTo(ApplicationState.RECORDING);
    }

    @Test
    void startRecordingReturnsFalseWhenAlreadyRecording() {
        service.startRecording();
        boolean secondStart = service.startRecording();

        assertThat(secondStart).isFalse();
    }

    @Test
    void stopRecordingTransitionsToTranscribing() {
        service.startRecording();
        events.clear();

        boolean stopped = service.stopRecording();

        assertThat(stopped).isTrue();
        assertThat(service.getState()).isEqualTo(ApplicationState.TRANSCRIBING);
        assertThat(transcriptionOrchestrator.transcribeCalled).isTrue();
    }

    @Test
    void stopRecordingReturnsFalseWhenNotRecording() {
        boolean stopped = service.stopRecording();
        assertThat(stopped).isFalse();
    }

    @Test
    void cancelRecordingTransitionsToIdle() {
        service.startRecording();
        events.clear();

        service.cancelRecording();

        assertThat(service.getState()).isEqualTo(ApplicationState.IDLE);
        assertThat(captureOrchestrator.cancelled).isTrue();
    }

    @Test
    void transcriptionCompletedTransitionsToIdle() {
        service.startRecording();
        service.stopRecording();
        assertThat(service.getState()).isEqualTo(ApplicationState.TRANSCRIBING);

        service.onTranscriptionCompleted(
                new TranscriptionCompletedEvent(
                        TranscriptionResult.of("hello", 1.0, "vosk"),
                        Instant.now(), "vosk"));

        assertThat(service.getState()).isEqualTo(ApplicationState.IDLE);
    }

    @Test
    void stopRecordingWithNullAudioTransitionsToIdle() {
        captureOrchestrator.audioData = null;
        service.startRecording();
        events.clear();

        boolean stopped = service.stopRecording();

        assertThat(stopped).isFalse();
        assertThat(service.getState()).isEqualTo(ApplicationState.IDLE);
    }

    @Test
    void stopRecordingTransitionsToIdleWhenTranscriptionFails() {
        transcriptionOrchestrator.shouldThrow = true;
        service.startRecording();
        events.clear();

        try {
            service.stopRecording();
        } catch (RuntimeException ignored) {
            // Transcription threw — expected in this defensive test
        }

        // State is TRANSCRIBING (set inside synchronized block before transcribe call)
        assertThat(service.getState()).isEqualTo(ApplicationState.TRANSCRIBING);

        // Simulate the empty TranscriptionCompletedEvent that the fixed
        // DefaultTranscriptionOrchestrator would publish on failure
        service.onTranscriptionCompleted(
                new TranscriptionCompletedEvent(
                        TranscriptionResult.of("", 0.0, "unknown"),
                        Instant.now(), "unknown"));

        assertThat(service.getState()).isEqualTo(ApplicationState.IDLE);
    }

    @Test
    void stopRecordingShouldTransitionToIdleWhenStopCaptureThrows() {
        captureOrchestrator.shouldThrowOnStop = true;
        service.startRecording();
        events.clear();

        boolean stopped = service.stopRecording();

        assertThat(stopped).isFalse();
        assertThat(service.getState()).isEqualTo(ApplicationState.IDLE);
    }

    // --- Test fakes ---

    private static class FakeCaptureOrchestrator implements CaptureOrchestrator {
        byte[] audioData = new byte[3200];
        boolean capturing = false;
        boolean cancelled = false;
        boolean shouldThrowOnStop = false;
        UUID sessionId;

        @Override
        public UUID startCapture() {
            if (capturing) {
                return null;
            }
            capturing = true;
            sessionId = UUID.randomUUID();
            return sessionId;
        }

        @Override
        public byte[] stopCapture(UUID sid) {
            capturing = false;
            if (shouldThrowOnStop) {
                throw new RuntimeException("Audio line error");
            }
            return audioData;
        }

        @Override
        public void cancelCapture(UUID sid) {
            capturing = false;
            cancelled = true;
        }

        @Override
        public boolean isCapturing() {
            return capturing;
        }
    }

    private static class FakeTranscriptionOrchestrator implements TranscriptionOrchestrator {
        boolean transcribeCalled = false;
        boolean shouldThrow = false;

        @Override
        public void transcribe(byte[] pcm) {
            transcribeCalled = true;
            if (shouldThrow) {
                throw new RuntimeException("Transcription failed");
            }
        }
    }
}
