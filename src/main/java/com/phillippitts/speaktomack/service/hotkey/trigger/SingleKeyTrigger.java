package com.phillippitts.speaktomack.service.hotkey.trigger;

import com.phillippitts.speaktomack.service.hotkey.HotkeyTrigger;
import com.phillippitts.speaktomack.service.hotkey.NormalizedKeyEvent;

import java.util.Set;

/**
 * Matches a single key, optionally with required modifiers, ignoring key-repeat.
 */
public final class SingleKeyTrigger implements HotkeyTrigger {

    private final String key;
    private final Set<String> modifiers;
    private boolean held;

    public SingleKeyTrigger(String key, java.util.List<String> modifiers) {
        this.key = key.toUpperCase();
        this.modifiers = Set.copyOf(modifiers.stream().map(String::toUpperCase).toList());
    }

    @Override
    public String name() { return "single-key:" + key + (modifiers.isEmpty()?"":"+"+String.join("+", modifiers)); }

    @Override
    public boolean onKeyPressed(NormalizedKeyEvent e) {
        if (held) return false; // ignore repeats while held
        if (!e.key().equals(key)) return false;
        if (!e.modifiers().containsAll(modifiers)) return false;
        held = true;
        return true;
    }

    @Override
    public boolean onKeyReleased(NormalizedKeyEvent e) {
        if (held && e.key().equals(key)) {
            held = false;
            return true;
        }
        return false;
    }
}
