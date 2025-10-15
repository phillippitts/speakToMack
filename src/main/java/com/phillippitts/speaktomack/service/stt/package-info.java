/**
 * Speech-to-Text (STT) engine abstractions and implementations.
 *
 * <p>This package contains the adapter pattern implementations for different STT engines:
 * <ul>
 *   <li>Vosk - Fast, JNI-based STT engine (~100ms latency)</li>
 *   <li>Whisper - Accurate, process-based STT engine (~1-2s latency)</li>
 * </ul>
 *
 * <p>Architecture:
 * <ul>
 *   <li>{@link com.phillippitts.speaktomack.service.stt.SttEngine} - Unified interface for all engines</li>
 *   <li>VoskSttEngine (Phase 2) - Vosk JNI adapter</li>
 *   <li>WhisperSttEngine (Phase 2) - Whisper.cpp process adapter</li>
 * </ul>
 *
 * <p>All implementations:
 * <ul>
 *   <li>Accept audio in 16kHz, 16-bit PCM, mono format</li>
 *   <li>Return {@link com.phillippitts.speaktomack.domain.TranscriptionResult} objects</li>
 *   <li>Are thread-safe for concurrent transcription</li>
 *   <li>Implement health checks via {@code isHealthy()}</li>
 *   <li>Support graceful shutdown via {@code close()}</li>
 * </ul>
 *
 * @see com.phillippitts.speaktomack.service.stt.SttEngine
 * @see com.phillippitts.speaktomack.domain.TranscriptionResult
 * @see com.phillippitts.speaktomack.service.audio.AudioFormat
 * @since 1.0
 */
package com.phillippitts.speaktomack.service.stt;
