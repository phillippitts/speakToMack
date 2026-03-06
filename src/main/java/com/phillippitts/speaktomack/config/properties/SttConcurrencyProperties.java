package com.phillippitts.speaktomack.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
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
public class SttConcurrencyProperties {

    /** Maximum parallel Vosk transcriptions allowed. */
    @Positive(message = "Vosk max concurrency must be positive")
    private int voskMax = 4;

    /** Maximum parallel Whisper transcriptions allowed. */
    @Positive(message = "Whisper max concurrency must be positive")
    private int whisperMax = 2;

    /**
     * Timeout in milliseconds to wait for semaphore acquisition before rejecting request.
     * Default: 1000ms (1 second) provides reasonable buffering for brief spikes.
     * Set to 0 for immediate rejection (fail-fast).
     */
    @Positive(message = "Acquire timeout must be positive")
    private int acquireTimeoutMs = 1000;

    /** Enable dynamic concurrency scaling based on system resources. */
    private boolean dynamicScalingEnabled = false;

    /** CPU usage above this triggers permit reduction (0.0-1.0). */
    @Min(value = 0, message = "CPU threshold must be >= 0")
    @Max(value = 1, message = "CPU threshold must be <= 1")
    private double cpuThresholdHigh = 0.80;

    /** Memory usage above this triggers permit reduction (0.0-1.0). */
    @Min(value = 0, message = "Memory threshold must be >= 0")
    @Max(value = 1, message = "Memory threshold must be <= 1")
    private double memoryThresholdHigh = 0.85;

    /** How often to re-evaluate concurrency limits (ms). */
    @Positive(message = "Scaling interval must be positive")
    private long scalingIntervalMs = 5000;

    public int getVoskMax() {
        return voskMax;
    }

    public void setVoskMax(int voskMax) {
        this.voskMax = voskMax;
    }

    public int getWhisperMax() {
        return whisperMax;
    }

    public void setWhisperMax(int whisperMax) {
        this.whisperMax = whisperMax;
    }

    public int getAcquireTimeoutMs() {
        return acquireTimeoutMs;
    }

    public void setAcquireTimeoutMs(int acquireTimeoutMs) {
        this.acquireTimeoutMs = acquireTimeoutMs;
    }

    public boolean isDynamicScalingEnabled() {
        return dynamicScalingEnabled;
    }

    public void setDynamicScalingEnabled(boolean dynamicScalingEnabled) {
        this.dynamicScalingEnabled = dynamicScalingEnabled;
    }

    public double getCpuThresholdHigh() {
        return cpuThresholdHigh;
    }

    public void setCpuThresholdHigh(double cpuThresholdHigh) {
        this.cpuThresholdHigh = cpuThresholdHigh;
    }

    public double getMemoryThresholdHigh() {
        return memoryThresholdHigh;
    }

    public void setMemoryThresholdHigh(double memoryThresholdHigh) {
        this.memoryThresholdHigh = memoryThresholdHigh;
    }

    public long getScalingIntervalMs() {
        return scalingIntervalMs;
    }

    public void setScalingIntervalMs(long scalingIntervalMs) {
        this.scalingIntervalMs = scalingIntervalMs;
    }
}
