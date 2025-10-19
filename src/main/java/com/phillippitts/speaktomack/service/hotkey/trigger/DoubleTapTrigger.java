package com.phillippitts.speaktomack.service.hotkey.trigger;

import com.phillippitts.speaktomack.service.hotkey.HotkeyTrigger;
import com.phillippitts.speaktomack.service.hotkey.NormalizedKeyEvent;

/**
 * Matches a double-tap of the same key within a threshold in milliseconds.
 * Release is emitted on the key-up after a successful double-tap cycle.
 */
public final class DoubleTapTrigger implements HotkeyTrigger {

    private final String key;
    private final int thresholdMs;

    // state
    private long lastTapAt = -1L;
    private boolean armed;

    public DoubleTapTrigger(String key, int thresholdMs) {
        this.key = key.toUpperCase();
        this.thresholdMs = thresholdMs;
    }

    @Override
    public String name() {
        return "double-tap:" + key + "@" + thresholdMs + "ms";
    }

    @Override
    public boolean onKeyPressed(NormalizedKeyEvent e) {
        if (!e.key().equals(key)) {
            return false;
        }
        long now = e.whenMillis();
        if (lastTapAt < 0) {
            lastTapAt = now;
            armed = false;
            return false; // first tap doesn't trigger press yet
        }
        long dt = now - lastTapAt;
        lastTapAt = now;
        if (dt <= thresholdMs) {
            armed = true; // matched double-tap; wait for release to emit
            return true;
        }
        // too slow; treat this as a new first tap
        armed = false;
        return false;
    }

    @Override
    public boolean onKeyReleased(NormalizedKeyEvent e) {
        if (!e.key().equals(key)) {
            return false;
        }
        if (armed) {
            armed = false;
            return true;
        }
        return false;
    }
}
