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
 * stt.whisper.binary-path=models/whisper.cpp/main
 * stt.whisper.model-path=models/ggml-base.en.bin
 * stt.whisper.timeout-seconds=10
 * </pre>
 *
 * @param binaryPath Path to the whisper.cpp binary executable
 * @param modelPath Path to the GGML model file (.bin)
 * @param timeoutSeconds Maximum time to wait for transcription (in seconds)
 */
@ConfigurationProperties(prefix = "stt.whisper")
@Validated
public record WhisperConfig(
        @NotBlank(message = "Whisper binary path must not be blank")
        String binaryPath,

        @NotBlank(message = "Whisper model path must not be blank")
        String modelPath,

        @Positive(message = "Timeout must be positive")
        int timeoutSeconds
) {
    /**
     * Default constructor with standard values.
     */
    public WhisperConfig() {
        this("models/whisper.cpp/main", "models/ggml-base.en.bin", 10);
    }
}
