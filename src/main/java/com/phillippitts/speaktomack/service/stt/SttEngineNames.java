package com.phillippitts.speaktomack.service.stt;

/**
 * Centralized constants for STT engine name identifiers.
 *
 * <p>This utility class provides type-safe constant definitions for all STT engine names
 * used throughout the application. Centralizing these constants prevents typos, ensures
 * consistency, and provides a single source of truth for engine identification.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * TranscriptionResult.of(text, confidence, SttEngineNames.VOSK);
 * if (engineName.equals(SttEngineNames.WHISPER)) { ... }
 * }</pre>
 *
 * @since 1.0
 */
public final class SttEngineNames {

    /**
     * Identifier for the Vosk speech-to-text engine.
     *
     * <p>Vosk provides offline speech recognition with good accuracy
     * for streaming real-time transcription.
     */
    public static final String VOSK = "vosk";

    /**
     * Identifier for the Whisper speech-to-text engine.
     *
     * <p>Whisper (whisper.cpp) provides high-accuracy offline speech recognition
     * with optional word-level token support for advanced reconciliation.
     */
    public static final String WHISPER = "whisper";

    /**
     * Identifier for reconciled transcription results.
     *
     * <p>Used when transcription results are produced by combining outputs
     * from multiple engines using a reconciliation strategy.
     */
    public static final String RECONCILED = "reconciled";

    private SttEngineNames() {
        // Utility class - prevent instantiation
    }
}
