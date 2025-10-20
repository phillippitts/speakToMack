package com.phillippitts.speaktomack.config.hotkey;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HotkeyPropertiesValidationTest {

    @Test
    void failsOnUnknownType() {
        HotkeyProperties p = new HotkeyProperties(null, "RIGHT_META", 300, List.of(), List.of());
        HotkeyConfigurationValidator v = new HotkeyConfigurationValidator(p);
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid hotkey.type");
    }

    @Test
    void failsOnBadKey() {
        HotkeyProperties p = new HotkeyProperties(
                com.phillippitts.speaktomack.config.hotkey.TriggerType.SINGLE_KEY,
                "NOT_A_KEY", 300, List.of(), List.of());
        HotkeyConfigurationValidator v = new HotkeyConfigurationValidator(p);
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid hotkey.key");
    }

    @Test
    void failsOnBadModifier() {
        HotkeyProperties p = new HotkeyProperties(
                com.phillippitts.speaktomack.config.hotkey.TriggerType.MODIFIER_COMBO,
                "D", 300, List.of("WEIRD"), List.of());
        HotkeyConfigurationValidator v = new HotkeyConfigurationValidator(p);
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid hotkey.modifiers entry");
    }

    @Test
    void requiresModifierForCombination() {
        HotkeyProperties p = new HotkeyProperties(
                com.phillippitts.speaktomack.config.hotkey.TriggerType.MODIFIER_COMBO,
                "D", 300, List.of(), List.of());
        HotkeyConfigurationValidator v = new HotkeyConfigurationValidator(p);
        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires at least one modifier");
    }
}
