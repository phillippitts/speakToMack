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
        return switch (p.getType()) {
            case SINGLE_KEY -> new SingleKeyTrigger(p.getKey(), p.getModifiers());
            case DOUBLE_TAP -> new DoubleTapTrigger(p.getKey(), p.getThresholdMs());
            case MODIFIER_COMBO -> new ModifierCombinationTrigger(p.getModifiers(), p.getKey());
        };
    }
}
