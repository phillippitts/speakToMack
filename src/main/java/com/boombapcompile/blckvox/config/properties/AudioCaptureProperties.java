package com.boombapcompile.blckvox.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed properties for microphone capture.
 * Required format (enforced by service): 16 kHz, 16-bit PCM, mono, little-endian.
 */
@Validated
@ConfigurationProperties(prefix = "audio.capture")
public record AudioCaptureProperties(

        // Size of a read chunk from the TargetDataLine in milliseconds.
        @Min(10)
        @Max(200)
        int chunkMillis,

        // Maximum capture duration in milliseconds (hard stop).
        @Min(100)
        @Max(600_000)
        int maxDurationMs,

        // Optional input device name hint; falls back to system default when null/blank.
        String deviceName
) {

    public AudioCaptureProperties {
        if (deviceName != null && deviceName.isBlank()) {
            deviceName = null;
        }
    }

    public int getChunkMillis() {
        return chunkMillis;
    }

    public int getMaxDurationMs() {
        return maxDurationMs;
    }

    public String getDeviceName() {
        return deviceName;
    }
}
