package com.phillippitts.blckvox.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Positive;

/**
 * Configurable audio validation thresholds.
 * Defaults target good UX: minimum ~250 ms; maximum 5 minutes.
 *
 * <p>Note: Bean created via {@code @EnableConfigurationProperties} in BlckvoxApplication.
 */
@ConfigurationProperties(prefix = "audio.validation")
@Validated
public record AudioValidationProperties(

        // Minimum duration in milliseconds for a valid clip (UX guard, not security).
        @DefaultValue("250")
        @Positive(message = "Minimum duration must be positive")
        int minDurationMs,

        // Maximum duration in milliseconds for a valid clip (security cap).
        @DefaultValue("300000")
        @Positive(message = "Maximum duration must be positive")
        int maxDurationMs,

        // Maximum file size in bytes for audio payloads (security guard against memory exhaustion).
        // Default: 100 MB (reasonable upper bound for 5 min audio with WAV overhead).
        @DefaultValue("104857600")
        @Positive(message = "Maximum file size must be positive")
        int maxFileSizeBytes
) {

    public int getMinDurationMs() {
        return minDurationMs;
    }

    public int getMaxDurationMs() {
        return maxDurationMs;
    }

    public int getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }
}
