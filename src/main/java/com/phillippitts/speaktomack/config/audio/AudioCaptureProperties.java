package com.phillippitts.speaktomack.config.audio;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

/**
 * Typed properties for microphone capture.
 *
 * Required format (enforced by service): 16 kHz, 16-bit PCM, mono, little-endian.
 */
@Validated
@ConfigurationProperties(prefix = "audio.capture")
public class AudioCaptureProperties {

    /** Size of a read chunk from the TargetDataLine in milliseconds. */
    @Min(10)
    @Max(200)
    private final int chunkMillis;

    /** Maximum capture duration in milliseconds (hard stop). */
    @Min(100)
    @Max(600_000)
    private final int maxDurationMs;

    /** Optional input device name hint; falls back to system default when null/blank. */
    private final String deviceName;

    @ConstructorBinding
    public AudioCaptureProperties(@NotNull Integer chunkMillis,
                                  @NotNull Integer maxDurationMs,
                                  String deviceName) {
        this.chunkMillis = chunkMillis;
        this.maxDurationMs = maxDurationMs;
        this.deviceName = (deviceName == null || deviceName.isBlank()) ? null : deviceName;
    }

    public int getChunkMillis() { return chunkMillis; }
    public int getMaxDurationMs() { return maxDurationMs; }
    public String getDeviceName() { return deviceName; }
}
