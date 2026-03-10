package com.phillippitts.blckvox.testutil;

import com.phillippitts.blckvox.service.audio.capture.AudioCaptureService;

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
            return generateNonSilentPcm(32000);
        }
        throw new IllegalStateException("Session not stopped or ID mismatch");
    }

    /**
     * Generates non-silent PCM16LE data with alternating +10000/−10000 samples.
     * RMS ≈ 10000, well above the silence-detection threshold (200).
     */
    public static byte[] generateNonSilentPcm(int byteCount) {
        byte[] pcm = new byte[byteCount];
        for (int i = 0; i + 1 < byteCount; i += 2) {
            short sample = (i / 2 % 2 == 0) ? (short) 10000 : (short) -10000;
            pcm[i]     = (byte) (sample & 0xFF);
            pcm[i + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return pcm;
    }

    @Override
    public void cancelSession(UUID id) {
        sessionId = null;
    }
}
