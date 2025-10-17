package com.phillippitts.speaktomack.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable domain model representing the result of a speech-to-text transcription.
 * Contains the transcribed text, confidence score, and metadata.
 *
 * @param text       The transcribed text (must not be null)
 * @param confidence Confidence score between 0.0 and 1.0 (1.0 = highest confidence)
 * @param timestamp  When the transcription was completed
 * @param engineName Name of the STT engine that produced this result (e.g., "vosk", "whisper")
 */
public record TranscriptionResult(
        String text,
        double confidence,
        Instant timestamp,
        String engineName
) {

    /**
     * Compact constructor with validation.
     *
     * <p>Note: Empty text is valid (e.g., silence or unclear audio may produce no transcription).
     *
     * @throws IllegalArgumentException if text is null or confidence is out of range
     * @throws NullPointerException if text, timestamp, or engineName is null
     */
    public TranscriptionResult {
        Objects.requireNonNull(text, "Transcription text must not be null");
        // Empty text is valid (silence may produce empty transcription)
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException(
                    "Confidence must be between 0.0 and 1.0, got: " + confidence
            );
        }
        Objects.requireNonNull(timestamp, "Timestamp must not be null");
        Objects.requireNonNull(engineName, "Engine name must not be null");
    }

    /**
     * Creates a TranscriptionResult with current timestamp.
     *
     * @param text       The transcribed text
     * @param confidence Confidence score between 0.0 and 1.0
     * @param engineName Name of the STT engine
     * @return A new TranscriptionResult instance
     */
    public static TranscriptionResult of(String text, double confidence, String engineName) {
        return new TranscriptionResult(text, confidence, Instant.now(), engineName);
    }
}
