package com.boombapcompile.blckvox.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for the live caption overlay window.
 *
 * @since 1.3
 */
@ConfigurationProperties(prefix = "live-caption")
public record LiveCaptionProperties(
        @DefaultValue("false")
        boolean enabled,

        @DefaultValue("600")
        int windowWidth,

        @DefaultValue("250")
        int windowHeight,

        @DefaultValue("0.85")
        double windowOpacity
) {

    public boolean isEnabled() {
        return enabled;
    }

    public int getWindowWidth() {
        return windowWidth;
    }

    public int getWindowHeight() {
        return windowHeight;
    }

    public double getWindowOpacity() {
        return windowOpacity;
    }
}
