package com.phillippitts.blckvox.service.hotkey;

import com.phillippitts.blckvox.config.properties.HotkeyProperties;
import com.phillippitts.blckvox.service.hotkey.event.HotkeyPressedEvent;
import com.phillippitts.blckvox.service.hotkey.event.HotkeyReleasedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class HotkeyManagerTest {

    @Test
    void publishesPressAndReleaseForSingleKey() {
        // Arrange defaults: single-key RIGHT_META
        HotkeyProperties props =
                new HotkeyProperties(
                        com.phillippitts.blckvox.config.hotkey.TriggerType.SINGLE_KEY,
                        "RIGHT_META", 300, List.of(), List.of(), false);
        List<Object> events = new ArrayList<>();
        ApplicationEventPublisher publisher = events::add;

        FakeHook hook = new FakeHook();
        HotkeyManager mgr = new HotkeyManager(hook, new HotkeyTriggerFactory(), props, publisher);

        // Act
        mgr.start();
        long now = System.currentTimeMillis();
        hook.emit(new NormalizedKeyEvent(NormalizedKeyEvent.Type.PRESSED, "RIGHT_META",
                Set.of(), now));
        hook.emit(new NormalizedKeyEvent(NormalizedKeyEvent.Type.RELEASED, "RIGHT_META",
                Set.of(), now + 5));
        mgr.stop();

        // Assert
        assertThat(events.stream().filter(e -> e instanceof HotkeyPressedEvent).count())
                .isEqualTo(1);
        assertThat(events.stream().filter(e -> e instanceof HotkeyReleasedEvent).count())
                .isEqualTo(1);
    }

    // Simple fake hook for tests
    static class FakeHook implements GlobalKeyHook {
        private volatile Consumer<NormalizedKeyEvent> listener;
        @Override
        public void register() { }
        @Override
        public void unregister() { }
        @Override
        public void addListener(Consumer<NormalizedKeyEvent> listener) {
            this.listener = listener;
        }
        void emit(NormalizedKeyEvent e) {
            Consumer<NormalizedKeyEvent> l = listener;
            if (l != null) {
                l.accept(e);
            }
        }
    }
}
