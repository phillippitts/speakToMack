package com.phillippitts.speaktomack.config.stt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Configuration properties for the Whisper STT engine.
 * Binds to properties prefixed with "stt.whisper".
 *
 * <p>Example application.properties:
 * <pre>
 * stt.whisper.binary-path=tools/whisper.cpp/main
 * stt.whisper.model-path=models/ggml-base.en.bin
 * stt.whisper.timeout-seconds=10
 * stt.whisper.language=en
 * stt.whisper.threads=4
 * </pre>
 *
 * @param binaryPath Path to the whisper.cpp binary executable
 * @param modelPath Path to the GGML model file (.bin)
 * @param timeoutSeconds Maximum time to wait for transcription (in seconds)
 * @param language Language code for transcription (e.g., "en", "es", "fr")
 * @param threads Number of CPU threads to use for transcription
 */
@ConfigurationProperties(prefix = "stt.whisper")
@Validated
public record WhisperConfig(
        @NotBlank(message = "Whisper binary path must not be blank")
        String binaryPath,

        @NotBlank(message = "Whisper model path must not be blank")
        String modelPath,

        @Positive(message = "Timeout must be positive")
        int timeoutSeconds,

        @NotBlank(message = "Language code must not be blank")
        String language,

        @Positive(message = "Thread count must be positive")
        int threads
) {
    /**
     * Default constructor with standard values.
     */
    public WhisperConfig() {
        this("tools/whisper.cpp/main", "models/ggml-base.en.bin", 10, "en", 4);
    }
}
