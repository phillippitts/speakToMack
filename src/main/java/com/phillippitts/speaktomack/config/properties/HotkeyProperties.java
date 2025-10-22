package com.phillippitts.speaktomack.config.properties;

import com.phillippitts.speaktomack.config.hotkey.TriggerType;
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

    /** Trigger type. */
    @jakarta.validation.constraints.NotNull
    private final TriggerType type;

    /** Primary key code name (e.g., RIGHT_META, F13, D). */
    @NotBlank
    private final String key;

    /** Double-tap threshold (ms). Only used when type=double-tap. */
    @Min(100)
    @Max(1000)
    private final int thresholdMs;

    /** Optional modifiers for single-key or combination types (e.g., META, SHIFT, CONTROL, ALT). */
    private final List<String> modifiers;

    /** Reserved OS shortcuts to flag as conflicts (e.g., META+TAB, META+L). */
    private final List<String> reserved;

    /**
     * Toggle mode: if true, first hotkey press starts recording, second press stops and transcribes.
     * If false (default), uses push-to-talk: press starts recording, release stops and transcribes.
     */
    private final boolean toggleMode;

    @ConstructorBinding
    public HotkeyProperties(TriggerType type,
                            String key,
                            Integer thresholdMs,
                            List<String> modifiers,
                            List<String> reserved,
                            Boolean toggleMode) {
        this.type = type;
        this.key = key;
        this.thresholdMs = thresholdMs == null ? 300 : thresholdMs;
        this.modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
        // Provide sensible defaults if not supplied
        this.reserved = (reserved == null || reserved.isEmpty())
                ? List.of("META+TAB", "META+L")
                : List.copyOf(reserved);
        this.toggleMode = toggleMode != null && toggleMode;
    }

    public TriggerType getType() {
        return type;
    }

    public String getKey() {
        return key;
    }

    public int getThresholdMs() {
        return thresholdMs;
    }

    public List<String> getModifiers() {
        return modifiers;
    }

    public List<String> getReserved() {
        return reserved;
    }

    public boolean isToggleMode() {
        return toggleMode;
    }
}
