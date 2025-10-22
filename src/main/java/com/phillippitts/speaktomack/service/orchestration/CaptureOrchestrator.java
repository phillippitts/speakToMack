package com.phillippitts.speaktomack.service.orchestration;

import java.util.UUID;

/**
 * Manages audio capture session lifecycle.
 *
 * <p>This orchestrator encapsulates the state machine and service coordination required for
 * audio capture sessions. It provides a simplified API for starting, stopping, and cancelling
 * audio capture operations.
 *
 * <p><b>Thread Safety:</b> Implementations must be thread-safe to handle concurrent hotkey events.
 * Only one capture session can be active at a time.
 *
 * <p><b>Session Lifecycle:</b>
 * <ol>
 *   <li>Call {@link #startCapture()} to begin recording audio</li>
 *   <li>Call {@link #stopCapture(UUID)} to end recording and retrieve audio data</li>
 *   <li>Call {@link #cancelCapture(UUID)} to abort recording without retrieving data</li>
 * </ol>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * CaptureOrchestrator orchestrator = ...;
 *
 * // Start capture
 * UUID session = orchestrator.startCapture();
 *
 * // Later, stop and get audio
 * byte[] audio = orchestrator.stopCapture(session);
 * if (audio != null) {
 *     // Process audio...
 * }
 * }</pre>
 *
 * @since 1.1
 */
public interface CaptureOrchestrator {

    /**
     * Starts a new audio capture session.
     *
     * <p>If a capture session is already active, this method returns {@code null} and logs
     * a warning. Only one session can be active at a time.
     *
     * @return session ID if capture started successfully, {@code null} if a session is already active
     */
    UUID startCapture();

    /**
     * Stops an active capture session and retrieves the recorded audio data.
     *
     * <p>This method performs the following steps:
     * <ol>
     *   <li>Stops the audio capture service</li>
     *   <li>Updates the capture state machine</li>
     *   <li>Reads all captured PCM audio data</li>
     *   <li>Cleans up the session</li>
     * </ol>
     *
     * <p>If audio retrieval fails, the session is automatically cancelled to free resources.
     *
     * @param sessionId the session ID returned by {@link #startCapture()}
     * @return PCM audio data (16kHz, mono, 16-bit LE), or {@code null} if the session
     *         doesn't exist or audio retrieval failed
     */
    byte[] stopCapture(UUID sessionId);

    /**
     * Cancels an active capture session without retrieving audio data.
     *
     * <p>This method is used when an error occurs during capture (e.g., microphone
     * permission denied, device unavailable) or when capture needs to be aborted.
     *
     * <p>If no active session exists with the given ID, this method does nothing and
     * logs a debug message.
     *
     * @param sessionId the session ID to cancel, or {@code null} to cancel the current active session
     */
    void cancelCapture(UUID sessionId);

    /**
     * Checks if a capture session is currently active.
     *
     * @return {@code true} if capture is in progress, {@code false} otherwise
     */
    boolean isCapturing();
}
