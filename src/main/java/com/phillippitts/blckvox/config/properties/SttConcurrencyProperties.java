package com.phillippitts.blckvox.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * Configurable per-engine concurrency caps to prevent process/thread storms during parallel runs.
 *
 * <p>When concurrency limits are reached, engines will wait for the configured timeout
 * before rejecting the request. This allows brief spikes to succeed while still
 * protecting against sustained overload.
 *
 * <p>Defaults are conservative and can be tuned per environment via properties.
 *
 * <p>Properties:
 * <ul>
 *   <li>stt.concurrency.vosk-max - Maximum parallel Vosk calls (default: 4)</li>
 *   <li>stt.concurrency.whisper-max - Maximum parallel Whisper calls (default: 2)</li>
 *   <li>stt.concurrency.acquire-timeout-ms - Semaphore wait timeout in ms (default: 1000)</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "stt.concurrency")
@Validated
public record SttConcurrencyProperties(

        // Maximum parallel Vosk transcriptions allowed.
        @DefaultValue("4")
        @Positive(message = "Vosk max concurrency must be positive")
        int voskMax,

        // Maximum parallel Whisper transcriptions allowed.
        @DefaultValue("2")
        @Positive(message = "Whisper max concurrency must be positive")
        int whisperMax,

        // Timeout in milliseconds to wait for semaphore acquisition before rejecting request.
        // Default: 1000ms (1 second) provides reasonable buffering for brief spikes.
        // Set to 0 for immediate rejection (fail-fast).
        @DefaultValue("1000")
        @Positive(message = "Acquire timeout must be positive")
        int acquireTimeoutMs,

        // Enable dynamic concurrency scaling based on system resources.
        @DefaultValue("false")
        boolean dynamicScalingEnabled,

        // CPU usage above this triggers permit reduction (0.0-1.0).
        @DefaultValue("0.80")
        @Min(value = 0, message = "CPU threshold must be >= 0")
        @Max(value = 1, message = "CPU threshold must be <= 1")
        double cpuThresholdHigh,

        // Memory usage above this triggers permit reduction (0.0-1.0).
        @DefaultValue("0.85")
        @Min(value = 0, message = "Memory threshold must be >= 0")
        @Max(value = 1, message = "Memory threshold must be <= 1")
        double memoryThresholdHigh,

        // How often to re-evaluate concurrency limits (ms).
        @DefaultValue("5000")
        @Positive(message = "Scaling interval must be positive")
        long scalingIntervalMs
) {

    public boolean isDynamicScalingEnabled() {
        return dynamicScalingEnabled;
    }

    public int getVoskMax() {
        return voskMax;
    }

    public int getWhisperMax() {
        return whisperMax;
    }

    public int getAcquireTimeoutMs() {
        return acquireTimeoutMs;
    }

    public double getCpuThresholdHigh() {
        return cpuThresholdHigh;
    }

    public double getMemoryThresholdHigh() {
        return memoryThresholdHigh;
    }

    public long getScalingIntervalMs() {
        return scalingIntervalMs;
    }
}
