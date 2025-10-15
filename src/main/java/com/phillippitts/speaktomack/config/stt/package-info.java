/**
 * Configuration properties for Speech-to-Text (STT) engines.
 *
 * <p>This package contains type-safe configuration records that bind to {@code application.properties}
 * via Spring Boot's {@link org.springframework.boot.context.properties.ConfigurationProperties}.
 *
 * <p>Configuration Records:
 * <ul>
 *   <li>{@link com.phillippitts.speaktomack.config.stt.VoskConfig} - Binds to {@code stt.vosk.*}
 *       properties (model path, sample rate, max alternatives)</li>
 *   <li>{@link com.phillippitts.speaktomack.config.stt.WhisperConfig} - Binds to {@code stt.whisper.*}
 *       properties (binary path, model path, timeout)</li>
 * </ul>
 *
 * <p>All configuration records:
 * <ul>
 *   <li>Are immutable (Java records)</li>
 *   <li>Include Jakarta Bean Validation constraints ({@code @NotBlank}, {@code @Positive})</li>
 *   <li>Provide sensible defaults via no-arg constructors</li>
 *   <li>Comply with Checkstyle ParameterNumber rule (max 3 parameters)</li>
 * </ul>
 *
 * <p>Example application.properties:
 * <pre>
 * stt.vosk.model-path=models/vosk-model-small-en-us-0.15
 * stt.vosk.sample-rate=16000
 * stt.vosk.max-alternatives=1
 *
 * stt.whisper.binary-path=models/whisper.cpp/main
 * stt.whisper.model-path=models/ggml-base.en.bin
 * stt.whisper.timeout-seconds=10
 * </pre>
 *
 * @see com.phillippitts.speaktomack.config.stt.VoskConfig
 * @see com.phillippitts.speaktomack.config.stt.WhisperConfig
 * @see org.springframework.boot.context.properties.ConfigurationProperties
 * @since 1.0
 */
package com.phillippitts.speaktomack.config.stt;
