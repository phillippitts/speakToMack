package com.phillippitts.speaktomack.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the live caption overlay window.
 *
 * @since 1.3
 */
@ConfigurationProperties(prefix = "live-caption")
public class LiveCaptionProperties {

    private static final int DEFAULT_WIDTH = 600;
    private static final int DEFAULT_HEIGHT = 250;
    private static final double DEFAULT_OPACITY = 0.85;

    private final boolean enabled;
    private final int windowWidth;
    private final int windowHeight;
    private final double windowOpacity;

    public LiveCaptionProperties(Boolean enabled,
                                 Integer windowWidth,
                                 Integer windowHeight,
                                 Double windowOpacity) {
        this.enabled = enabled != null && enabled;
        this.windowWidth = windowWidth != null ? windowWidth : DEFAULT_WIDTH;
        this.windowHeight = windowHeight != null ? windowHeight : DEFAULT_HEIGHT;
        this.windowOpacity = windowOpacity != null ? windowOpacity : DEFAULT_OPACITY;
    }

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
