package com.phillippitts.speaktomack.testutil;

import com.phillippitts.speaktomack.service.audio.capture.AudioCaptureService;

import java.util.UUID;

/**
 * Test double for AudioCaptureService that returns canned PCM data.
 *
 * <p>Simulates a 1-second recording session (32000 bytes = 16-bit, 16kHz, mono).
 *
 * <p><b>Public fields:</b> {@code sessionId} and {@code sessionStopped} are exposed
 * to allow tests to verify capture session state.
 */
public class FakeAudioCaptureService implements AudioCaptureService {
    public UUID sessionId;
    public boolean sessionStopped;

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
