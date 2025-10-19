package com.phillippitts.speaktomack.service.hotkey;

import com.phillippitts.speaktomack.config.hotkey.HotkeyProperties;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyPressedEvent;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyReleasedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class HotkeyManagerTest {

    @Test
    void publishesPressAndReleaseForSingleKey() {
        // Arrange defaults: single-key RIGHT_META
        HotkeyProperties props = new HotkeyProperties("single-key", "RIGHT_META", 300, List.of());
        List<Object> events = new ArrayList<>();
        ApplicationEventPublisher publisher = events::add;

        FakeHook hook = new FakeHook();
        HotkeyManager mgr = new HotkeyManager(hook, new HotkeyTriggerFactory(), props, publisher);

        // Act
        mgr.start();
        long now = System.currentTimeMillis();
        hook.emit(new NormalizedKeyEvent(NormalizedKeyEvent.Type.PRESSED, "RIGHT_META", Set.of(), now));
        hook.emit(new NormalizedKeyEvent(NormalizedKeyEvent.Type.RELEASED, "RIGHT_META", Set.of(), now + 5));
        mgr.stop();

        // Assert
        assertThat(events.stream().filter(e -> e instanceof HotkeyPressedEvent).count()).isEqualTo(1);
        assertThat(events.stream().filter(e -> e instanceof HotkeyReleasedEvent).count()).isEqualTo(1);
    }

    // Simple fake hook for tests
    static class FakeHook implements GlobalKeyHook {
        private final AtomicBoolean reg = new AtomicBoolean();
        private volatile Consumer<NormalizedKeyEvent> listener;
        @Override public void register() { reg.set(true); }
        @Override public void unregister() { reg.set(false); }
        @Override public void addListener(Consumer<NormalizedKeyEvent> listener) { this.listener = listener; }
        void emit(NormalizedKeyEvent e) { Consumer<NormalizedKeyEvent> l = listener; if (l != null) l.accept(e); }
    }
}
