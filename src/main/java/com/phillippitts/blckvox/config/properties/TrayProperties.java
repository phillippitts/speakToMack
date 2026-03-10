package com.phillippitts.blckvox.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for the macOS system tray icon.
 *
 * @since 1.2
 */
@ConfigurationProperties(prefix = "tray")
public record TrayProperties(
        @DefaultValue("true")
        boolean enabled
) {

    public boolean isEnabled() {
        return enabled;
    }
}
