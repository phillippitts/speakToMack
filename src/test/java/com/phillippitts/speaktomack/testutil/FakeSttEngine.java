package com.phillippitts.speaktomack.testutil;

import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import com.phillippitts.speaktomack.service.stt.SttEngine;

import java.time.Instant;

/**
 * Test double for SttEngine with configurable transcription output.
 *
 * <p>Allows tests to control:
 * <ul>
 *   <li>Returned text (mutable via {@code cannedText} field)</li>
 *   <li>Confidence score</li>
 *   <li>Health status (can simulate engine failures)</li>
 * </ul>
 *
 * <p><b>Mutable fields:</b> {@code cannedText} and {@code healthy} are public
 * to allow dynamic modification in multi-iteration tests.
 */
public class FakeSttEngine implements SttEngine {
    private final String engineName;
    public String cannedText; // Mutable for dynamic test scenarios
    private final double cannedConfidence;
    public boolean healthy = true;

    /**
     * Creates a fake STT engine with configurable output.
     *
     * @param name engine name (e.g., "vosk", "whisper")
     * @param text canned transcription text to return
     * @param confidence canned confidence score (0.0 to 1.0)
     */
    public FakeSttEngine(String name, String text, double confidence) {
        this.engineName = name;
        this.cannedText = text;
        this.cannedConfidence = confidence;
    }

    @Override
    public void initialize() {
        // No-op for fake
    }

    @Override
    public TranscriptionResult transcribe(byte[] pcmData) throws TranscriptionException {
        if (!healthy) {
            throw new TranscriptionException("Engine unhealthy", engineName);
        }
        return new TranscriptionResult(cannedText, cannedConfidence, Instant.now(), engineName);
    }

    @Override
    public boolean isHealthy() {
        return healthy;
    }

    @Override
    public String getEngineName() {
        return engineName;
    }

    @Override
    public void close() {
        // No-op for fake
    }
}
