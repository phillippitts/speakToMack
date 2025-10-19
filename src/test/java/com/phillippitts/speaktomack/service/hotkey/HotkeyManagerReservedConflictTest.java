package com.phillippitts.speaktomack.service.hotkey;

import com.phillippitts.speaktomack.config.hotkey.HotkeyProperties;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyConflictEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class HotkeyManagerReservedConflictTest {

    @Test
    void publishesConflictEventWhenReservedMatches() {
        // Configure hotkey META+TAB and reserved contains META+TAB
        HotkeyProperties props = new HotkeyProperties(com.phillippitts.speaktomack.config.hotkey.TriggerType.MODIFIER_COMBO, "TAB", 300, List.of("META"), List.of("META+TAB"));
        List<Object> events = new ArrayList<>();
        ApplicationEventPublisher publisher = events::add;
        FakeHook hook = new FakeHook();

        HotkeyManager mgr = new HotkeyManager(hook, new HotkeyTriggerFactory(), props, publisher);
        mgr.start();
        mgr.stop();

        boolean hasConflict = events.stream().anyMatch(e -> e instanceof HotkeyConflictEvent);
        assertThat(hasConflict).isTrue();
    }

    static class FakeHook implements GlobalKeyHook {
        private final AtomicBoolean reg = new AtomicBoolean();
        private volatile Consumer<NormalizedKeyEvent> listener;
        @Override public void register() { reg.set(true); }
        @Override public void unregister() { reg.set(false); }
        @Override public void addListener(Consumer<NormalizedKeyEvent> listener) { this.listener = listener; }
    }
}
