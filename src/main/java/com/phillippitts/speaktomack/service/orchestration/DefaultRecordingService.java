package com.phillippitts.speaktomack.service.orchestration;

import com.phillippitts.speaktomack.service.orchestration.event.TranscriptionCompletedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.event.EventListener;

import java.util.Objects;
import java.util.UUID;

/**
 * Default implementation of {@link RecordingService} that delegates to
 * {@link CaptureOrchestrator} and {@link TranscriptionOrchestrator}.
 *
 * <p>Manages session lifecycle and application state transitions.
 * Thread-safe via synchronized methods for state mutation.
 *
 * @since 1.2
 */
public class DefaultRecordingService implements RecordingService {

    private static final Logger LOG = LogManager.getLogger(DefaultRecordingService.class);

    private final CaptureOrchestrator captureOrchestrator;
    private final TranscriptionOrchestrator transcriptionOrchestrator;
    private final ApplicationStateTracker stateTracker;

    private UUID activeSessionId;

    public DefaultRecordingService(CaptureOrchestrator captureOrchestrator,
                                   TranscriptionOrchestrator transcriptionOrchestrator,
                                   ApplicationStateTracker stateTracker) {
        this.captureOrchestrator = Objects.requireNonNull(captureOrchestrator);
        this.transcriptionOrchestrator = Objects.requireNonNull(transcriptionOrchestrator);
        this.stateTracker = Objects.requireNonNull(stateTracker);
    }

    @Override
    public synchronized boolean startRecording() {
        if (captureOrchestrator.isCapturing()) {
            LOG.debug("Already recording, ignoring start request");
            return false;
        }

        UUID sessionId = captureOrchestrator.startCapture();
        if (sessionId == null) {
            LOG.warn("Failed to start capture session");
            return false;
        }

        activeSessionId = sessionId;
        stateTracker.transitionTo(ApplicationState.RECORDING);
        LOG.info("Recording started (session={})", sessionId);
        return true;
    }

    @Override
    public synchronized boolean stopRecording() {
        return stopRecordingInternal();
    }

    @Override
    public synchronized void cancelRecording() {
        if (activeSessionId != null) {
            LOG.info("Cancelling recording (session={})", activeSessionId);
            captureOrchestrator.cancelCapture(activeSessionId);
            activeSessionId = null;
            stateTracker.transitionTo(ApplicationState.IDLE);
        }
    }

    @Override
    public ApplicationState getState() {
        return stateTracker.getState();
    }

    @Override
    public boolean isRecording() {
        return stateTracker.getState() == ApplicationState.RECORDING;
    }

    @Override
    public boolean toggleRecording() {
        synchronized (this) {
            if (activeSessionId != null) {
                // Currently recording — stop
                return stopRecordingInternal();
            }
        }
        // Not recording — start
        return startRecording();
    }

    /**
     * Internal stop logic extracted so {@link #toggleRecording()} can call it
     * while already holding the monitor (avoiding double-lock).
     */
    private boolean stopRecordingInternal() {
        // Caller must hold synchronized(this)
        if (activeSessionId == null) {
            LOG.debug("No active recording session to stop");
            return false;
        }

        byte[] pcm = captureOrchestrator.stopCapture(activeSessionId);
        UUID stoppedSession = activeSessionId;
        activeSessionId = null;

        if (pcm == null) {
            LOG.warn("Failed to retrieve audio data from session {}", stoppedSession);
            stateTracker.transitionTo(ApplicationState.IDLE);
            return false;
        }

        stateTracker.transitionTo(ApplicationState.TRANSCRIBING);
        LOG.info("Recording stopped, transcribing (session={})", stoppedSession);
        transcriptionOrchestrator.transcribe(pcm);
        return true;
    }

    /**
     * Listens for transcription completion to transition back to IDLE.
     */
    @EventListener
    public void onTranscriptionCompleted(TranscriptionCompletedEvent event) {
        if (stateTracker.getState() == ApplicationState.TRANSCRIBING) {
            stateTracker.transitionTo(ApplicationState.IDLE);
        }
    }
}
