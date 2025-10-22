package com.phillippitts.speaktomack.service.orchestration;

import com.phillippitts.speaktomack.service.audio.capture.AudioCaptureService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.UUID;

/**
 * Default implementation of {@link CaptureOrchestrator} using {@link AudioCaptureService}
 * and {@link CaptureStateMachine}.
 *
 * <p>This implementation coordinates the audio capture service with the capture state machine
 * to ensure only one capture session is active at a time. All operations are thread-safe.
 *
 * <p><b>State Management:</b> The capture state machine tracks the active session ID and
 * prevents race conditions when multiple hotkey events arrive concurrently.
 *
 * <p><b>Error Handling:</b> Failed audio retrieval automatically triggers session cancellation
 * to prevent resource leaks. All errors are logged with context for debugging.
 *
 * @since 1.1
 */
public class DefaultCaptureOrchestrator implements CaptureOrchestrator {

    private static final Logger LOG = LogManager.getLogger(DefaultCaptureOrchestrator.class);

    private final AudioCaptureService captureService;
    private final CaptureStateMachine stateMachine;

    /**
     * Constructs a DefaultCaptureOrchestrator.
     *
     * @param captureService service for audio capture operations
     * @param stateMachine state machine for tracking active sessions
     * @throws NullPointerException if any parameter is null
     */
    public DefaultCaptureOrchestrator(AudioCaptureService captureService,
                                      CaptureStateMachine stateMachine) {
        this.captureService = Objects.requireNonNull(captureService, "captureService must not be null");
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine must not be null");
    }

    @Override
    public UUID startCapture() {
        // Always start a new session from the capture service
        UUID sessionId = captureService.startSession();

        // Try to register it with the state machine
        if (stateMachine.startCapture(sessionId)) {
            LOG.info("Capture session started (session={})", sessionId);
            return sessionId;
        } else {
            // Race condition or already active: another session started between checks
            LOG.debug("Failed to start capture session {}, another session already active", sessionId);
            captureService.cancelSession(sessionId);
            return null;
        }
    }

    @Override
    public byte[] stopCapture(UUID sessionId) {
        if (sessionId == null) {
            LOG.debug("stopCapture called with null sessionId; ignoring");
            return null;
        }

        if (!sessionId.equals(stateMachine.getActiveSession())) {
            LOG.warn("stopCapture called for session {} but active session is {}; ignoring",
                    sessionId, stateMachine.getActiveSession());
            return null;
        }

        captureService.stopSession(sessionId);
        if (!stateMachine.stopCapture(sessionId)) {
            LOG.warn("Failed to stop capture session {} - session ID mismatch or not active", sessionId);
        }

        return readCapturedAudio(sessionId);
    }

    /**
     * Reads captured audio data from the session and handles cleanup on failure.
     *
     * @param sessionId the capture session ID
     * @return PCM audio data, or null if read failed
     */
    private byte[] readCapturedAudio(UUID sessionId) {
        try {
            byte[] pcm = captureService.readAll(sessionId);
            LOG.debug("Read {} bytes from capture session {}", pcm == null ? 0 : pcm.length, sessionId);
            return pcm;
        } catch (Exception e) {
            LOG.warn("Failed to read audio from capture session {}: {}", sessionId, e.toString());
            captureService.cancelSession(sessionId);
            return null;
        }
    }

    @Override
    public void cancelCapture(UUID sessionId) {
        // Always get the active session from state machine
        UUID activeSession = stateMachine.getActiveSession();

        // If sessionId is provided (not null), verify it matches the active session
        if (sessionId != null && !sessionId.equals(activeSession)) {
            LOG.debug("cancelCapture called with sessionId {} but active session is {}; ignoring",
                    sessionId, activeSession);
            return;
        }

        // Cancel the active session (if any)
        UUID cancelledSession = stateMachine.cancelCapture();
        if (cancelledSession != null) {
            LOG.info("Cancelled capture session {}", cancelledSession);
            captureService.cancelSession(cancelledSession);
        } else {
            LOG.debug("cancelCapture called but no active session to cancel");
        }
    }

    @Override
    public boolean isCapturing() {
        return stateMachine.isActive();
    }
}
