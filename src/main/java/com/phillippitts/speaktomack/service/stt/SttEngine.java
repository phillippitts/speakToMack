package com.phillippitts.speaktomack.service.stt;

import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.exception.ModelNotFoundException;
import com.phillippitts.speaktomack.exception.TranscriptionException;

import java.util.List;
import java.util.Optional;

/**
 * Contract for Speech-to-Text (STT) engine implementations.
 * Implementations must support the adapter pattern to wrap different STT libraries
 * (Vosk JNI, Whisper.cpp process) behind a unified interface.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Engine is constructed with configuration (model path, parameters)</li>
 *   <li>{@link #initialize()} loads the model and prepares the engine (may throw {@link ModelNotFoundException})</li>
 *   <li>{@link #transcribe(byte[])} processes audio and returns results (may throw {@link TranscriptionException})</li>
 *   <li>{@link #close()} releases resources when engine is no longer needed</li>
 * </ol>
 *
 * <p>Thread Safety: Implementations should be thread-safe for concurrent transcriptions.
 *
 * <p>Audio Format: All implementations must accept audio in the format defined by
 * {@link com.phillippitts.speaktomack.service.audio.AudioFormat}:
 * 16kHz, 16-bit signed PCM, mono, little-endian.
 *
 * @see com.phillippitts.speaktomack.domain.TranscriptionResult
 * @see ModelNotFoundException
 * @see TranscriptionException
 */
public interface SttEngine extends AutoCloseable {

    /**
     * Initializes the STT engine by loading the model and preparing resources.
     * This is typically called once at application startup.
     *
     * @throws ModelNotFoundException if the model file cannot be found or loaded
     * @throws TranscriptionException if engine initialization fails for other reasons
     */
    void initialize();

    /**
     * Transcribes the given audio data to text.
     *
     * <p>The audio must be in the required format (16kHz, 16-bit PCM, mono, little-endian).
     * Audio format validation should be performed before calling this method.
     *
     * @param audioData Raw PCM audio data in the required format
     * @return Transcription result with text, confidence, and metadata
     * @throws TranscriptionException if transcription fails (timeout, engine error, etc.)
     * @throws IllegalArgumentException if audioData is null or empty
     */
    TranscriptionResult transcribe(byte[] audioData);

    /**
     * Returns the name of this STT engine for logging and monitoring.
     *
     * @return Engine name (e.g., "vosk", "whisper")
     */
    String getEngineName();

    /**
     * Checks if the engine is currently available and healthy.
     * This can be used by circuit breakers and health checks.
     *
     * @return true if the engine is operational, false otherwise
     */
    boolean isHealthy();

    /**
     * Releases all resources held by this engine.
     * After calling this method, the engine should not be used again.
     *
     * <p>Implementations should handle cleanup gracefully, even if initialization failed.
     */
    @Override
    void close();

    /**
     * Consumes and returns tokenized version of the last transcription result, if available.
     * This is an optional capability - engines that support detailed tokenization can override
     * this method to provide token-level information for improved reconciliation strategies.
     *
     * <p>Default implementation returns empty Optional. Engines like Whisper that provide
     * JSON output with token-level timing can override to return actual tokens.
     *
     * @return Optional containing list of tokens from last transcription, or empty if not supported
     */
    default Optional<List<String>> consumeTokens() {
        return Optional.empty();
    }

    /**
     * Consumes and returns raw JSON output from the last transcription, if available.
     * This is an optional capability - engines that produce structured output can override
     * this method to provide access to the raw response for debugging or advanced processing.
     *
     * <p>Default implementation returns empty Optional. Engines like Whisper in JSON mode
     * can override to return the complete JSON response.
     *
     * @return Optional containing raw JSON string from last transcription, or empty if not supported
     */
    default Optional<String> consumeRawJson() {
        return Optional.empty();
    }
}
