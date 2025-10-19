package com.phillippitts.speaktomack.service.audio.capture;

import java.util.UUID;

/**
 * Microphone capture service.
 *
 * Contract:
 * - One active session at a time
 * - Returned data is raw PCM (16kHz, 16-bit, mono, little-endian)
 */
public interface AudioCaptureService {

    /** Starts a new capture session. Fails if another session is active. */
    UUID startSession();

    /** Stops the active session and finalizes capture. No-op if already stopped. */
    void stopSession(UUID sessionId);

    /** Cancels the active session and discards buffered data. No-op if already stopped. */
    void cancelSession(UUID sessionId);

    /**
     * Returns the full captured PCM buffer for the given session. Can be called once after stop.
     * Implementations may throw IllegalStateException if session not found or not stopped yet.
     */
    byte[] readAll(UUID sessionId);
}
