package com.phillippitts.speaktomack.service.hotkey;

import java.util.function.Consumer;

/**
 * Abstraction over a global keyboard hook (e.g., JNativeHook).
 *
 * Provides a test seam so unit tests can inject a fake implementation
 * and remain hermetic (no OS-level hooks required in CI).
 */
public interface GlobalKeyHook {

    /** Register the global hook. Idempotent. */
    void register();

    /** Unregister the global hook. Idempotent. */
    void unregister();

    /**
     * Subscribe to normalized key events.
     * Implementations adapt native events into NormalizedKeyEvent to keep
     * the rest of the system decoupled from specific libraries.
     */
    void addListener(Consumer<NormalizedKeyEvent> listener);
}
