package com.phillippitts.blckvox.service.hotkey;

import com.phillippitts.blckvox.config.properties.HotkeyProperties;
import com.phillippitts.blckvox.service.hotkey.trigger.DoubleTapTrigger;
import com.phillippitts.blckvox.service.hotkey.trigger.ModifierCombinationTrigger;
import com.phillippitts.blckvox.service.hotkey.trigger.SingleKeyTrigger;
import org.springframework.stereotype.Component;

/**
 * Builds HotkeyTrigger implementations from typed properties.
 */
@Component
public class HotkeyTriggerFactory {

    public HotkeyTrigger from(HotkeyProperties p) {
        return switch (p.getType()) {
            case SINGLE_KEY -> new SingleKeyTrigger(p.getKey(), p.getModifiers());
            case DOUBLE_TAP -> new DoubleTapTrigger(p.getKey(), p.getThresholdMs());
            case MODIFIER_COMBO -> new ModifierCombinationTrigger(p.getModifiers(), p.getKey());
        };
    }
}
