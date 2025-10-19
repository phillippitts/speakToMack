package com.phillippitts.speaktomack.config.hotkey;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Typed properties for global hotkey configuration.
 *
 * Supports three trigger styles:
 * - single-key
 * - double-tap (same key pressed twice within threshold)
 * - modifier-combination (e.g., META+SHIFT+D)
 *
 * Values are validated on startup for fail-fast behavior.
 */
@Validated
@ConfigurationProperties(prefix = "hotkey")
public class HotkeyProperties {

    /** Trigger type: single-key | double-tap | modifier-combination. */
    @NotBlank
    private final String type;

    /** Primary key code name (e.g., RIGHT_META, F13, D). */
    @NotBlank
    private final String key;

    /** Double-tap threshold (ms). Only used when type=double-tap. */
    @Min(50)
    @Max(1000)
    private final int thresholdMs;

    /** Optional modifiers for single-key or combination types (e.g., META, SHIFT, CONTROL, ALT). */
    private final List<String> modifiers;

    @ConstructorBinding
    public HotkeyProperties(String type,
                            String key,
                            Integer thresholdMs,
                            List<String> modifiers) {
        this.type = type;
        this.key = key;
        this.thresholdMs = thresholdMs == null ? 300 : thresholdMs;
        this.modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
    }

    public String getType() { return type; }
    public String getKey() { return key; }
    public int getThresholdMs() { return thresholdMs; }
    public List<String> getModifiers() { return modifiers; }
}
