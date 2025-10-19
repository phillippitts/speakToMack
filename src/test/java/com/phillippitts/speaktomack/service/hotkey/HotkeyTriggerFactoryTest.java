package com.phillippitts.speaktomack.service.hotkey;

import com.phillippitts.speaktomack.config.hotkey.HotkeyProperties;
import com.phillippitts.speaktomack.service.hotkey.trigger.DoubleTapTrigger;
import com.phillippitts.speaktomack.service.hotkey.trigger.ModifierCombinationTrigger;
import com.phillippitts.speaktomack.service.hotkey.trigger.SingleKeyTrigger;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HotkeyTriggerFactoryTest {

    @Test
    void buildsSingleKeyTrigger() {
        HotkeyTriggerFactory f = new HotkeyTriggerFactory();
        HotkeyProperties p = new HotkeyProperties(com.phillippitts.speaktomack.config.hotkey.TriggerType.SINGLE_KEY, "RIGHT_META", 300, List.of(), List.of());
        HotkeyTrigger t = f.from(p);
        assertThat(t).isInstanceOf(SingleKeyTrigger.class);
    }

    @Test
    void buildsDoubleTapTrigger() {
        HotkeyTriggerFactory f = new HotkeyTriggerFactory();
        HotkeyProperties p = new HotkeyProperties(com.phillippitts.speaktomack.config.hotkey.TriggerType.DOUBLE_TAP, "F13", 250, List.of(), List.of());
        HotkeyTrigger t = f.from(p);
        assertThat(t).isInstanceOf(DoubleTapTrigger.class);
    }

    @Test
    void buildsModifierCombinationTrigger() {
        HotkeyTriggerFactory f = new HotkeyTriggerFactory();
        HotkeyProperties p = new HotkeyProperties(com.phillippitts.speaktomack.config.hotkey.TriggerType.MODIFIER_COMBO, "D", 300, List.of("META","SHIFT"), List.of());
        HotkeyTrigger t = f.from(p);
        assertThat(t).isInstanceOf(ModifierCombinationTrigger.class);
    }

}
