package com.phillippitts.speaktomack.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * Configuration properties for the STT engine watchdog (Task 2.7).
 */
@ConfigurationProperties(prefix = "stt.watchdog")
@Validated
public class SttWatchdogProperties {

    /** Enable/disable watchdog globally. */
    private boolean enabled = true;

    /** Sliding window size for restart budget, in minutes. */
    @Positive(message = "Window minutes must be positive")
    private int windowMinutes = 60;

    /** Maximum restarts permitted per engine within the window. */
    @Positive(message = "Max restarts per window must be positive")
    private int maxRestartsPerWindow = 3;

    /** Cooldown minutes after disabling an engine before attempting re-enable. */
    @Positive(message = "Cooldown minutes must be positive")
    private int cooldownMinutes = 10;

    /** Optional lightweight probe enabled (not used by default). */
    private boolean probeEnabled = false;

    /** Health summary log interval in milliseconds. */
    @Positive(message = "Health summary interval must be positive")
    private long healthSummaryIntervalMillis = 60_000;

    /** Average confidence below this threshold triggers blacklisting (0.0-1.0). */
    @Min(value = 0, message = "Confidence blacklist threshold must be >= 0")
    @Max(value = 1, message = "Confidence blacklist threshold must be <= 1")
    private double confidenceBlacklistThreshold = 0.3;

    /** Number of recent confidence scores to average for blacklisting. */
    @Positive(message = "Confidence window size must be positive")
    private int confidenceWindowSize = 10;

    /** Minimum samples required before evaluating confidence trend. */
    @Positive(message = "Confidence min samples must be positive")
    private int confidenceMinSamples = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getWindowMinutes() {
        return windowMinutes;
    }

    public void setWindowMinutes(int windowMinutes) {
        this.windowMinutes = windowMinutes;
    }

    public int getMaxRestartsPerWindow() {
        return maxRestartsPerWindow;
    }

    public void setMaxRestartsPerWindow(int maxRestartsPerWindow) {
        this.maxRestartsPerWindow = maxRestartsPerWindow;
    }

    public int getCooldownMinutes() {
        return cooldownMinutes;
    }

    public void setCooldownMinutes(int cooldownMinutes) {
        this.cooldownMinutes = cooldownMinutes;
    }

    public boolean isProbeEnabled() {
        return probeEnabled;
    }

    public void setProbeEnabled(boolean probeEnabled) {
        this.probeEnabled = probeEnabled;
    }

    public long getHealthSummaryIntervalMillis() {
        return healthSummaryIntervalMillis;
    }

    public void setHealthSummaryIntervalMillis(long healthSummaryIntervalMillis) {
        this.healthSummaryIntervalMillis = healthSummaryIntervalMillis;
    }

    public double getConfidenceBlacklistThreshold() {
        return confidenceBlacklistThreshold;
    }

    public void setConfidenceBlacklistThreshold(double confidenceBlacklistThreshold) {
        this.confidenceBlacklistThreshold = confidenceBlacklistThreshold;
    }

    public int getConfidenceWindowSize() {
        return confidenceWindowSize;
    }

    public void setConfidenceWindowSize(int confidenceWindowSize) {
        this.confidenceWindowSize = confidenceWindowSize;
    }

    public int getConfidenceMinSamples() {
        return confidenceMinSamples;
    }

    public void setConfidenceMinSamples(int confidenceMinSamples) {
        this.confidenceMinSamples = confidenceMinSamples;
    }
}
