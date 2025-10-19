package com.phillippitts.speaktomack.service.hotkey;

/**
 * Strategy for matching a configured hotkey against normalized key events.
 * Implementations must be side-effect free except for internal timing state
 * (e.g., double-tap detection).
 */
public interface HotkeyTrigger {
    /** Human-readable name for logs. */
    String name();

    /** @return true if the hotkey press condition matched on this event. */
    boolean onKeyPressed(NormalizedKeyEvent e);

    /** @return true if the hotkey release condition matched on this event. */
    boolean onKeyReleased(NormalizedKeyEvent e);
}
