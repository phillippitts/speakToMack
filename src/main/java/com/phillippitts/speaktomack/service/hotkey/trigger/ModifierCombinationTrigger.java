package com.phillippitts.speaktomack.service.hotkey.trigger;

import com.phillippitts.speaktomack.service.hotkey.HotkeyTrigger;
import com.phillippitts.speaktomack.service.hotkey.NormalizedKeyEvent;

import java.util.Set;

/**
 * Matches a combination like META+SHIFT+D. Requires all configured modifiers
 * to be present along with the primary key.
 */
public final class ModifierCombinationTrigger implements HotkeyTrigger {

    private final String primaryKey;
    private final Set<String> requiredModifiers;
    private boolean held;

    public ModifierCombinationTrigger(java.util.List<String> modifiers, String primaryKey) {
        this.primaryKey = primaryKey.toUpperCase();
        this.requiredModifiers = Set.copyOf(modifiers.stream().map(String::toUpperCase).toList());
    }

    @Override
    public String name() {
        return "combo:" + String.join("+", requiredModifiers) + "+" + primaryKey;
    }

    @Override
    public boolean onKeyPressed(NormalizedKeyEvent e) {
        if (held) {
            return false;
        }
        if (!e.key().equals(primaryKey)) {
            return false;
        }
        if (!e.modifiers().containsAll(requiredModifiers)) {
            return false;
        }
        held = true;
        return true;
    }

    @Override
    public boolean onKeyReleased(NormalizedKeyEvent e) {
        if (held && e.key().equals(primaryKey)) {
            held = false;
            return true;
        }
        return false;
    }
}
