package com.phillippitts.speaktomack.service.stt;

import java.util.List;
import java.util.Optional;

/**
 * Extended STT engine interface for implementations that provide detailed transcription
 * metadata such as word-level tokens and structured JSON output.
 *
 * <p>This interface follows the Interface Segregation Principle (ISP) by separating
 * advanced capabilities from the base {@link SttEngine} contract. Engines that only
 * provide basic transcription (like Vosk) can implement {@link SttEngine} directly,
 * while engines with enhanced output (like Whisper in JSON mode) implement this interface.
 *
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li><b>Token-level reconciliation:</b> Word tokens improve overlap detection in
 *       {@link com.phillippitts.speaktomack.service.reconcile.impl.WordOverlapReconciler}</li>
 *   <li><b>Debugging and analysis:</b> Raw JSON provides complete engine output for
 *       troubleshooting and advanced processing</li>
 * </ul>
 *
 * <p><b>Consumption Pattern:</b> The methods in this interface consume (clear) their
 * cached values after returning them. This ensures each result is retrieved exactly once,
 * preventing stale data from being used across multiple transcriptions.
 *
 * <p><b>Thread Safety:</b> Implementations should ensure thread-safe access to cached
 * token and JSON data, typically using synchronization.
 *
 * @see SttEngine
 * @see com.phillippitts.speaktomack.service.stt.whisper.WhisperSttEngine
 * @since 1.3
 */
public interface DetailedTranscriptionEngine extends SttEngine {

    /**
     * Transcribes audio and returns detailed output including tokens and raw JSON.
     *
     * <p>Preferred over {@link #consumeTokens()} / {@link #consumeRawJson()} as it
     * returns all data in a single immutable value, eliminating ThreadLocal state.
     *
     * @param audioData PCM audio data
     * @return transcription output with result, tokens, and optional raw JSON
     * @since 1.4
     */
    TranscriptionOutput transcribeDetailed(byte[] audioData);

    /**
     * @deprecated Use {@link #transcribeDetailed(byte[])} instead. Will be removed in a future release.
     */
    @Deprecated(forRemoval = true)
    Optional<List<String>> consumeTokens();

    /**
     * @deprecated Use {@link #transcribeDetailed(byte[])} instead. Will be removed in a future release.
     */
    @Deprecated(forRemoval = true)
    Optional<String> consumeRawJson();
}
