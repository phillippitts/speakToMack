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
     * Consumes and returns tokenized version of the last transcription result.
     *
     * <p>This method provides word-level tokens parsed from the engine's structured output.
     * Tokens are more accurate than simple space-splitting for reconciliation because they
     * preserve multi-word phrases and handle punctuation correctly.
     *
     * <p><b>Side Effect:</b> Clears the cached tokens after returning them (one-time consumption).
     * Subsequent calls will return an empty Optional until the next transcription.
     *
     * <p><b>Example Usage:</b>
     * <pre>
     * if (engine instanceof DetailedTranscriptionEngine detailedEngine) {
     *     Optional&lt;List&lt;String&gt;&gt; tokens = detailedEngine.consumeTokens();
     *     tokens.ifPresent(t -&gt; reconciler.setWhisperTokens(t));
     * }
     * </pre>
     *
     * @return Optional containing list of word tokens from last transcription,
     *         or empty if no transcription has occurred yet
     */
    Optional<List<String>> consumeTokens();

    /**
     * Consumes and returns raw JSON output from the last transcription.
     *
     * <p>This method provides access to the complete structured output from the engine,
     * useful for debugging, logging, or advanced processing beyond simple text extraction.
     *
     * <p><b>Side Effect:</b> Clears the cached JSON after returning it (one-time consumption).
     * Subsequent calls will return an empty Optional until the next transcription.
     *
     * <p><b>Example Usage:</b>
     * <pre>
     * if (engine instanceof DetailedTranscriptionEngine detailedEngine) {
     *     Optional&lt;String&gt; json = detailedEngine.consumeRawJson();
     *     json.ifPresent(j -&gt; logger.debug("Raw Whisper JSON: {}", j));
     * }
     * </pre>
     *
     * @return Optional containing raw JSON string from last transcription,
     *         or empty if no transcription has occurred yet
     */
    Optional<String> consumeRawJson();
}
