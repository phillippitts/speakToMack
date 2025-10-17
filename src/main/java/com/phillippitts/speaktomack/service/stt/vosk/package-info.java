/**
 * Vosk speech-to-text engine implementation.
 *
 * <p>This package provides a JNI-based STT engine adapter for the Vosk library,
 * offering fast offline transcription with low latency (~100ms).
 *
 * <p>Key Components:
 * <ul>
 *   <li>{@link com.phillippitts.speaktomack.service.stt.vosk.VoskSttEngine} - Spring
 *       component implementing {@link com.phillippitts.speaktomack.service.stt.SttEngine}
 *       for Vosk integration</li>
 * </ul>
 *
 * <p>Vosk Characteristics:
 * <ul>
 *   <li><b>Speed:</b> Fast (~100ms per 3-second clip)</li>
 *   <li><b>Accuracy:</b> Good for short commands and phrases</li>
 *   <li><b>Threading:</b> Thread-safe via per-call recognizer creation</li>
 *   <li><b>Resource Model:</b> Single shared model (loaded once), recognizers per call</li>
 *   <li><b>Concurrency:</b> Lightweight semaphore (configurable via {@code stt.concurrency.vosk-max})</li>
 *   <li><b>Model Format:</b> Vosk models (e.g., vosk-model-small-en-us-0.15)</li>
 * </ul>
 *
 * <p>Configuration (application.properties):
 * <pre>
 * stt.vosk.model-path=models/vosk-model-small-en-us-0.15
 * stt.vosk.sample-rate=16000
 * stt.vosk.max-alternatives=1
 * stt.concurrency.vosk-max=4
 * </pre>
 *
 * <p>Initialization Flow:
 * <ol>
 *   <li>Model validation at startup via {@code ModelValidationService}</li>
 *   <li>Lazy model loading on first {@code initialize()} call</li>
 *   <li>Per-call recognizer creation in {@code transcribe()} for thread-safety</li>
 *   <li>Automatic recognizer cleanup via try-with-resources</li>
 * </ol>
 *
 * @see com.phillippitts.speaktomack.service.stt.SttEngine
 * @see com.phillippitts.speaktomack.config.stt.VoskConfig
 * @since 1.0
 */
package com.phillippitts.speaktomack.service.stt.vosk;
