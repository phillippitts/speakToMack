package com.phillippitts.speaktomack.config.stt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Configuration properties for the Vosk STT engine.
 * Binds to properties prefixed with "stt.vosk".
 *
 * <p>Example application.properties:
 * <pre>
 * stt.vosk.model-path=models/vosk-model-small-en-us-0.15
 * stt.vosk.sample-rate=16000
 * stt.vosk.max-alternatives=1
 * </pre>
 *
 * @param modelPath Path to the Vosk model directory (must exist)
 * @param sampleRate Audio sample rate in Hz (typically 16000)
 * @param maxAlternatives Maximum number of alternative transcriptions to return
 */
@ConfigurationProperties(prefix = "stt.vosk")
@Validated
public record VoskConfig(
        @NotBlank(message = "Vosk model path must not be blank")
        String modelPath,

        @Positive(message = "Sample rate must be positive")
        int sampleRate,

        @Positive(message = "Max alternatives must be positive")
        int maxAlternatives
) {
    /**
     * Default constructor with standard values.
     */
    public VoskConfig() {
        this("models/vosk-model-small-en-us-0.15", 16_000, 1);
    }
}
