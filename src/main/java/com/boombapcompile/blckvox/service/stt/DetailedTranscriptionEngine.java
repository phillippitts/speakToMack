package com.boombapcompile.blckvox.service.stt;

import com.boombapcompile.blckvox.service.reconcile.impl.WordOverlapReconciler;
import com.boombapcompile.blckvox.service.stt.whisper.WhisperSttEngine;

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
 *       {@link WordOverlapReconciler}</li>
 *   <li><b>Debugging and analysis:</b> Raw JSON provides complete engine output for
 *       troubleshooting and advanced processing</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> Implementations should ensure thread-safe access to
 * transcription data, typically using synchronization.
 *
 * @see SttEngine
 * @see WhisperSttEngine
 * @since 1.3
 */
public interface DetailedTranscriptionEngine extends SttEngine {

    /**
     * Transcribes audio and returns detailed output including tokens and raw JSON.
     *
     * <p>Returns all data in a single immutable value.
     *
     * @param audioData PCM audio data
     * @return transcription output with result, tokens, and optional raw JSON
     * @since 1.4
     */
    TranscriptionOutput transcribeDetailed(byte[] audioData);
}