package com.phillippitts.speaktomack.service.hotkey;

import java.util.Set;

/**
 * Normalized keyboard event used by the Hotkey subsystem.
 * This avoids direct coupling to any specific native hook library
 * and keeps tests hermetic.
 */
public record NormalizedKeyEvent(Type type, String key, Set<String> modifiers, long whenMillis) {

    public enum Type { PRESSED, RELEASED }

    public NormalizedKeyEvent {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        // Normalize to upper-case canonical names
        key = key.toUpperCase();
        modifiers = modifiers == null ? Set.of() : Set.copyOf(modifiers.stream().map(String::toUpperCase).toList());
    }
}
