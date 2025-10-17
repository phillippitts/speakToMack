package com.phillippitts.speaktomack.config.stt;

/**
 * Constants related to Speech-to-Text models and binaries.
 */
public final class SttModelConstants {

    /**
     * Minimum reasonable Whisper model size in bytes.
     * Base English model (ggml-base.en.bin) is ~150MB; anything below 100MB is considered invalid/corrupt.
     */
    public static final long MIN_WHISPER_MODEL_SIZE_BYTES = 100L * 1024 * 1024; // 100 MB

    private SttModelConstants() {}
}
