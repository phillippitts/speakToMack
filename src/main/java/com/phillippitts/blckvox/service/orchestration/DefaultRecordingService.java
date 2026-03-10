package com.phillippitts.blckvox.service.orchestration;

import com.phillippitts.blckvox.service.orchestration.event.TranscriptionCompletedEvent;
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
        if (stateTracker.getState() != ApplicationState.IDLE) {
            LOG.debug("Cannot start recording in state {}", stateTracker.getState());
            return false;
        }
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
    public boolean stopRecording() {
        byte[] pcm;
        UUID stoppedSession;
        synchronized (this) {
            if (activeSessionId == null) {
                LOG.debug("No active recording session to stop");
                return false;
            }
            stoppedSession = activeSessionId;
            try {
                pcm = captureOrchestrator.stopCapture(stoppedSession);
            } catch (Exception e) {
                LOG.error("stopCapture threw for session {}", stoppedSession, e);
                activeSessionId = null;
                stateTracker.transitionTo(ApplicationState.IDLE);
                return false;
            }
            activeSessionId = null;
        }
        // Transcribe outside the monitor to avoid blocking hotkey events
        return doTranscribe(pcm, stoppedSession);
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
        byte[] pcm = null;
        UUID stoppedSession = null;
        boolean shouldStart = false;

        synchronized (this) {
            if (activeSessionId != null) {
                // Currently recording — stop (extract PCM under lock)
                stoppedSession = activeSessionId;
                try {
                    pcm = captureOrchestrator.stopCapture(stoppedSession);
                } catch (Exception e) {
                    LOG.error("stopCapture threw during toggle for session {}", stoppedSession, e);
                    activeSessionId = null;
                    stateTracker.transitionTo(ApplicationState.IDLE);
                    return false;
                }
                activeSessionId = null;
            } else {
                shouldStart = true;
            }
        }

        if (shouldStart) {
            return startRecording();
        }
        // Transcribe outside the monitor
        return doTranscribe(pcm, stoppedSession);
    }

    /**
     * Performs transcription outside the synchronized block to avoid blocking
     * hotkey events during processing.
     */
    private boolean doTranscribe(byte[] pcm, UUID stoppedSession) {
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
    public synchronized void onTranscriptionCompleted(TranscriptionCompletedEvent event) {
        if (stateTracker.getState() == ApplicationState.TRANSCRIBING) {
            stateTracker.transitionTo(ApplicationState.IDLE);
        }
    }
}
