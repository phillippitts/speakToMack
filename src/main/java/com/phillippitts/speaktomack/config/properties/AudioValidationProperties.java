package com.phillippitts.speaktomack.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Positive;

/**
 * Configurable audio validation thresholds.
 * Defaults target good UX: minimum ~250 ms; maximum 5 minutes.
 *
 * <p>Note: Bean created via {@link com.phillippitts.speaktomack.SpeakToMackApplication#EnableConfigurationProperties}.
 */
@ConfigurationProperties(prefix = "audio.validation")
@Validated
public class AudioValidationProperties {

    /** Minimum duration in milliseconds for a valid clip (UX guard, not security). */
    @Positive(message = "Minimum duration must be positive")
    private int minDurationMs = 250;          // ~0.25s

    /** Maximum duration in milliseconds for a valid clip (security cap). */
    @Positive(message = "Maximum duration must be positive")
    private int maxDurationMs = 300_000;      // 5 minutes

    /**
     * Maximum file size in bytes for audio payloads (security guard against memory exhaustion).
     * Default: 100 MB (reasonable upper bound for 5 min audio with WAV overhead).
     */
    @Positive(message = "Maximum file size must be positive")
    private int maxFileSizeBytes = 100 * 1024 * 1024;  // 100 MB

    public int getMinDurationMs() {
        return minDurationMs;
    }

    public void setMinDurationMs(int minDurationMs) {
        this.minDurationMs = minDurationMs;
    }

    public int getMaxDurationMs() {
        return maxDurationMs;
    }

    public void setMaxDurationMs(int maxDurationMs) {
        this.maxDurationMs = maxDurationMs;
    }

    public int getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(int maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }
}
