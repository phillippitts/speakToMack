package com.phillippitts.speaktomack.service.hotkey;

import com.phillippitts.speaktomack.service.hotkey.trigger.DoubleTapTrigger;
import com.phillippitts.speaktomack.service.hotkey.trigger.ModifierCombinationTrigger;
import com.phillippitts.speaktomack.service.hotkey.trigger.SingleKeyTrigger;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HotkeyTriggerTests {

    @Test
    void singleKeyIgnoresRepeatsAndMatchesRelease() {
        HotkeyTrigger t = new SingleKeyTrigger("RIGHT_META", List.of());
        long now = System.currentTimeMillis();
        var press = new NormalizedKeyEvent(NormalizedKeyEvent.Type.PRESSED, "RIGHT_META", Set.of(), now);
        var repeat = new NormalizedKeyEvent(NormalizedKeyEvent.Type.PRESSED, "RIGHT_META", Set.of(), now+10);
        var release = new NormalizedKeyEvent(NormalizedKeyEvent.Type.RELEASED, "RIGHT_META", Set.of(), now+20);
        assertThat(t.onKeyPressed(press)).isTrue();
        assertThat(t.onKeyPressed(repeat)).isFalse();
        assertThat(t.onKeyReleased(release)).isTrue();
    }

    @Test
    void modifierCombinationRequiresAllModifiers() {
        HotkeyTrigger t = new ModifierCombinationTrigger(List.of("META", "SHIFT"), "D");
        long now = System.currentTimeMillis();
        var pressWrong = new NormalizedKeyEvent(NormalizedKeyEvent.Type.PRESSED, "D", Set.of("META"), now);
        var pressOk = new NormalizedKeyEvent(NormalizedKeyEvent.Type.PRESSED, "D", Set.of("META","SHIFT"), now);
        var releaseOk = new NormalizedKeyEvent(NormalizedKeyEvent.Type.RELEASED, "D", Set.of("META","SHIFT"), now+5);
        assertThat(t.onKeyPressed(pressWrong)).isFalse();
        assertThat(t.onKeyPressed(pressOk)).isTrue();
        assertThat(t.onKeyReleased(releaseOk)).isTrue();
    }

    @Test
    void doubleTapMatchesWithinThresholdOnly() {
        HotkeyTrigger t = new DoubleTapTrigger("F13", 300);
        long t0 = System.currentTimeMillis();
        var tap1 = new NormalizedKeyEvent(NormalizedKeyEvent.Type.PRESSED, "F13", Set.of(), t0);
        var tap2Late = new NormalizedKeyEvent(NormalizedKeyEvent.Type.PRESSED, "F13", Set.of(), t0 + 400);
        var tap3 = new NormalizedKeyEvent(NormalizedKeyEvent.Type.PRESSED, "F13", Set.of(), t0 + 450);
        var release = new NormalizedKeyEvent(NormalizedKeyEvent.Type.RELEASED, "F13", Set.of(), t0 + 451);

        assertThat(t.onKeyPressed(tap1)).isFalse();
        assertThat(t.onKeyPressed(tap2Late)).isFalse(); // too slow
        assertThat(t.onKeyPressed(tap3)).isTrue(); // matched double-tap
        assertThat(t.onKeyReleased(release)).isTrue();
    }
}
