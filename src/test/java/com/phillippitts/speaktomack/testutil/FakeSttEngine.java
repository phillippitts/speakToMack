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
 *   <li>Delay simulation (for timeout testing)</li>
 *   <li>Explicit failure mode (throws exception on transcribe)</li>
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
    private final int delayMs; // Simulated processing delay
    private final boolean shouldFail; // Explicit failure mode

    /**
     * Creates a fake STT engine with configurable output.
     *
     * @param name engine name (e.g., "vosk", "whisper")
     * @param text canned transcription text to return
     * @param confidence canned confidence score (0.0 to 1.0)
     */
    public FakeSttEngine(String name, String text, double confidence) {
        this(name, text, confidence, 0);
    }

    /**
     * Creates a fake STT engine with configurable output and delay.
     *
     * @param name engine name (e.g., "vosk", "whisper")
     * @param text canned transcription text to return
     * @param confidence canned confidence score (0.0 to 1.0)
     * @param delayMs simulated processing delay in milliseconds
     */
    public FakeSttEngine(String name, String text, double confidence, int delayMs) {
        this(name, text, confidence, false, delayMs);
    }

    /**
     * Creates a fake STT engine with configurable output, failure mode, and delay.
     *
     * @param name engine name (e.g., "vosk", "whisper")
     * @param text canned transcription text to return
     * @param confidence canned confidence score (0.0 to 1.0)
     * @param shouldFail if true, transcribe() always throws exception
     */
    public FakeSttEngine(String name, String text, double confidence, boolean shouldFail) {
        this(name, text, confidence, shouldFail, 0);
    }

    /**
     * Creates a fake STT engine with full configurability.
     *
     * @param name engine name (e.g., "vosk", "whisper")
     * @param text canned transcription text to return
     * @param confidence canned confidence score (0.0 to 1.0)
     * @param shouldFail if true, transcribe() always throws exception
     * @param delayMs simulated processing delay in milliseconds
     */
    private FakeSttEngine(String name, String text, double confidence, boolean shouldFail, int delayMs) {
        this.engineName = name;
        this.cannedText = text;
        this.cannedConfidence = confidence;
        this.shouldFail = shouldFail;
        this.delayMs = delayMs;
    }

    @Override
    public void initialize() {
        // No-op for fake
    }

    @Override
    public TranscriptionResult transcribe(byte[] pcmData) throws TranscriptionException {
        // Simulate processing delay if configured
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TranscriptionException("Transcription interrupted", engineName, e);
            }
        }

        // Check explicit failure mode first
        if (shouldFail) {
            throw new TranscriptionException("Engine configured to fail", engineName);
        }

        // Then check health status
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
