package com.phillippitts.speaktomack.service.audio;

/**
 * Test constants for audio processing tests.
 * All values based on 16kHz, 16-bit, mono, little-endian PCM format.
 */
public final class AudioTestConstants {

    private AudioTestConstants() {
        // Utility class
    }

    /**
     * 100ms of PCM audio data (1600 samples * 2 bytes/sample = 3200 bytes).
     * Useful for creating minimal valid audio buffers in tests.
     */
    public static final int PCM_100MS_BYTES = 3200;

    /**
     * 50ms of PCM audio data (800 samples * 2 bytes/sample = 1600 bytes).
     * Minimum duration for some STT engines.
     */
    public static final int PCM_50MS_BYTES = 1600;

    /**
     * Confidence tolerance for floating-point comparisons in test assertions.
     */
    public static final double CONFIDENCE_TOLERANCE = 0.01;
}
