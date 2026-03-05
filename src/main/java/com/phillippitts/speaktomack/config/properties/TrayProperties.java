package com.phillippitts.speaktomack.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the macOS system tray icon.
 *
 * @since 1.2
 */
@ConfigurationProperties(prefix = "tray")
public class TrayProperties {

    private final boolean enabled;

    public TrayProperties(Boolean enabled) {
        this.enabled = enabled == null || enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
