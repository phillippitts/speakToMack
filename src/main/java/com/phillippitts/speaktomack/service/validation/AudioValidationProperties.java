package com.phillippitts.speaktomack.service.validation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configurable audio validation thresholds.
 * Defaults target good UX: minimum ~250 ms; maximum 5 minutes.
 */
@Component
@ConfigurationProperties(prefix = "audio.validation")
public class AudioValidationProperties {

    /** Minimum duration in milliseconds for a valid clip (UX guard, not security). */
    private int minDurationMs = 250;          // ~0.25s

    /** Maximum duration in milliseconds for a valid clip (security cap). */
    private int maxDurationMs = 300_000;      // 5 minutes

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
}
