package com.phillippitts.speaktomack.config.stt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the STT engine watchdog (Task 2.7).
 */
@ConfigurationProperties(prefix = "stt.watchdog")
public class SttWatchdogProperties {

    /** Enable/disable watchdog globally. */
    private boolean enabled = true;

    /** Sliding window size for restart budget, in minutes. */
    private int windowMinutes = 60;

    /** Maximum restarts permitted per engine within the window. */
    private int maxRestartsPerWindow = 3;

    /** Cooldown minutes after disabling an engine before attempting re-enable. */
    private int cooldownMinutes = 10;

    /** Optional lightweight probe enabled (not used by default). */
    private boolean probeEnabled = false;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getWindowMinutes() { return windowMinutes; }
    public void setWindowMinutes(int windowMinutes) { this.windowMinutes = windowMinutes; }

    public int getMaxRestartsPerWindow() { return maxRestartsPerWindow; }
    public void setMaxRestartsPerWindow(int maxRestartsPerWindow) { this.maxRestartsPerWindow = maxRestartsPerWindow; }

    public int getCooldownMinutes() { return cooldownMinutes; }
    public void setCooldownMinutes(int cooldownMinutes) { this.cooldownMinutes = cooldownMinutes; }

    public boolean isProbeEnabled() { return probeEnabled; }
    public void setProbeEnabled(boolean probeEnabled) { this.probeEnabled = probeEnabled; }
}
