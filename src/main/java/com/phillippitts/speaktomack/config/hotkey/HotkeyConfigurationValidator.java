package com.phillippitts.speaktomack.config.hotkey;

import com.phillippitts.speaktomack.service.hotkey.KeyNameMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Validates HotkeyProperties against allow-lists at startup to fail fast with
 * actionable messages.
 */
@Component
class HotkeyConfigurationValidator {


    private final HotkeyProperties props;

    HotkeyConfigurationValidator(HotkeyProperties props) {
        this.props = props;
    }

    @PostConstruct
    void validate() {
        if (props.getType() == null) {
            throw new IllegalArgumentException("Invalid hotkey.type: null");
        }
        // Key validation
        if (!KeyNameMapper.isValidKey(props.getKey())) {
            throw new IllegalArgumentException("Invalid hotkey.key: '" + props.getKey() + "'. Must be A-Z, 0-9, F1..F24, or a known special (ESCAPE, ENTER, TAB, SPACE, BACKSPACE, LEFT/RIGHT_*). ");
        }
        // Modifiers validation
        for (String m : props.getModifiers()) {
            if (!KeyNameMapper.isValidModifier(m)) {
                throw new IllegalArgumentException("Invalid hotkey.modifiers entry: '" + m + "'. Allowed: META, SHIFT, CONTROL, ALT and LEFT/RIGHT variants.");
            }
        }
        if (props.getType() == TriggerType.MODIFIER_COMBO && props.getModifiers().isEmpty()) {
            throw new IllegalArgumentException("hotkey.type=modifier-combination requires at least one modifier in hotkey.modifiers");
        }
        // Reserved list can be empty; no strict validation beyond basic format
    }
}
