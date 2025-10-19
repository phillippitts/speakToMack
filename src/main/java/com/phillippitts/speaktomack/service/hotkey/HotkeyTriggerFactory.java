package com.phillippitts.speaktomack.service.hotkey;

import com.phillippitts.speaktomack.config.hotkey.HotkeyProperties;
import com.phillippitts.speaktomack.service.hotkey.trigger.DoubleTapTrigger;
import com.phillippitts.speaktomack.service.hotkey.trigger.ModifierCombinationTrigger;
import com.phillippitts.speaktomack.service.hotkey.trigger.SingleKeyTrigger;
import org.springframework.stereotype.Component;

/**
 * Builds HotkeyTrigger implementations from typed properties.
 */
@Component
public class HotkeyTriggerFactory {

    public HotkeyTrigger from(HotkeyProperties p) {
        String type = p.getType().toLowerCase();
        return switch (type) {
            case "single-key" -> new SingleKeyTrigger(p.getKey(), p.getModifiers());
            case "double-tap" -> new DoubleTapTrigger(p.getKey(), p.getThresholdMs());
            case "modifier-combination" -> new ModifierCombinationTrigger(p.getModifiers(), p.getKey());
            default -> throw new IllegalArgumentException("Unknown hotkey.type: " + p.getType());
        };
    }
}
