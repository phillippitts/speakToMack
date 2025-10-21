package com.phillippitts.speaktomack.service.fallback;

import com.phillippitts.speaktomack.config.typing.TypingProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class RobotTypingAdapterTest {

    static class FakeRobot implements RobotTypingAdapter.RobotFacade {
        final List<String> events = new ArrayList<>();
        int delayMs = 0;

        @Override
        public void keyPress(int keyCode) {
            events.add("press:" + keyCode);
        }

        @Override
        public void keyRelease(int keyCode) {
            events.add("release:" + keyCode);
        }

        @Override
        public void delay(int ms) {
            delayMs += ms;
            events.add("delay:" + ms);
        }

        @Override
        public void setClipboard(String text) {
            events.add("clipboard:" + (text == null ? "" : text));
        }
    }

    @Test
    void typesShortTextWithoutChunking() {
        // chunkSize, interChunkDelayMs, focusDelayMs, restoreClipboard, clipboardOnlyFallback, ...
        TypingProperties props = new TypingProperties(
                5000, 0, 0, true, false,
                TypingProperties.NewlineMode.LF, true, true, "os-default"
        );
        FakeRobot robot = new FakeRobot();
        RobotTypingAdapter adapter = new RobotTypingAdapter(props, robot);

        boolean ok = adapter.type("hello");

        assertThat(ok).isTrue();
        // Should have paste shortcut keys (META or CONTROL + V depending on OS)
        assertThat(robot.events).hasSizeGreaterThanOrEqualTo(4); // press mod, press V, release V, release mod
        assertThat(robot.events).contains("press:86"); // KeyEvent.VK_V
        assertThat(robot.events).contains("release:86");
    }

    @Test
    void chunksLongTextIntoMultipleSegments() {
        // chunkSize, interChunkDelayMs, focusDelayMs, restoreClipboard, clipboardOnlyFallback, ...
        TypingProperties props = new TypingProperties(
                10, 50, 0, true, false,
                TypingProperties.NewlineMode.LF, true, true, "os-default"
        );
        FakeRobot robot = new FakeRobot();
        RobotTypingAdapter adapter = new RobotTypingAdapter(props, robot);

        // 25 chars - should chunk into 10 + 10 + 5
        boolean ok = adapter.type("1234567890abcdefghijklmno");

        assertThat(ok).isTrue();
        // Should have 3 chunks (each with press/release events), 2 delays between them
        long delayCount = robot.events.stream().filter(e -> e.startsWith("delay:50")).count();
        assertThat(delayCount).isEqualTo(2); // Inter-chunk delays between chunks 1-2 and 2-3

        // Should have 3 paste operations (3 chunks * 4 key events per paste = 12 key events)
        long pasteCount = robot.events.stream().filter(e -> e.equals("press:86")).count(); // VK_V
        assertThat(pasteCount).isEqualTo(3);
    }

    @Test
    void appliesFocusDelayBeforeFirstPaste() {
        // chunkSize, interChunkDelayMs, focusDelayMs, restoreClipboard, clipboardOnlyFallback, ...
        TypingProperties props = new TypingProperties(
                5000, 0, 200, true, false,
                TypingProperties.NewlineMode.LF, true, true, "os-default"
        );
        FakeRobot robot = new FakeRobot();
        RobotTypingAdapter adapter = new RobotTypingAdapter(props, robot);

        adapter.type("test");

        // Focus delay should be first event
        assertThat(robot.events).isNotEmpty();
        assertThat(robot.events.get(0)).isEqualTo("delay:200");
        assertThat(robot.delayMs).isGreaterThanOrEqualTo(200);
    }

    @Test
    void canTypeReturnsFalseWhenRobotDisabled() {
        // chunkSize, interChunkDelayMs, focusDelayMs, restoreClipboard, clipboardOnlyFallback, ...
        TypingProperties props = new TypingProperties(
                5000, 0, 0, false, false,
                TypingProperties.NewlineMode.LF, true, false, "os-default"
        );
        FakeRobot robot = new FakeRobot();
        RobotTypingAdapter adapter = new RobotTypingAdapter(props, robot);

        assertThat(adapter.canType()).isFalse();
        assertThat(adapter.type("test")).isFalse();
        assertThat(robot.events).isEmpty();
    }

    @Test
    void canTypeReturnsFalseWhenRobotNull() {
        // chunkSize, interChunkDelayMs, focusDelayMs, restoreClipboard, clipboardOnlyFallback, ...
        TypingProperties props = new TypingProperties(
                5000, 0, 0, true, false,
                TypingProperties.NewlineMode.LF, true, false, "os-default"
        );
        RobotTypingAdapter adapter = new RobotTypingAdapter(props, null);

        assertThat(adapter.canType()).isFalse();
    }

    @Test
    void handlesNullTextGracefully() {
        // chunkSize, interChunkDelayMs, focusDelayMs, restoreClipboard, clipboardOnlyFallback, ...
        TypingProperties props = new TypingProperties(
                5000, 0, 0, true, false,
                TypingProperties.NewlineMode.LF, true, true, "os-default"
        );
        FakeRobot robot = new FakeRobot();
        RobotTypingAdapter adapter = new RobotTypingAdapter(props, robot);

        boolean ok = adapter.type(null);

        assertThat(ok).isTrue();
        // Should still execute paste shortcut even for empty string
        assertThat(robot.events).isNotEmpty();
    }

    @Test
    void returnsCorrectName() {
        // chunkSize, interChunkDelayMs, focusDelayMs, restoreClipboard, clipboardOnlyFallback, ...
        TypingProperties props = new TypingProperties(
                5000, 0, 0, true, false,
                TypingProperties.NewlineMode.LF, true, false, "os-default"
        );
        FakeRobot robot = new FakeRobot();
        RobotTypingAdapter adapter = new RobotTypingAdapter(props, robot);

        assertThat(adapter.name()).isEqualTo("robot");
    }

    @Test
    void pasteShortcutUsesMeta() {
        FakeRobot robot = new FakeRobot();
        RobotTypingAdapter.pasteShortcut(robot, "META+V");

        assertThat(robot.events).contains("press:157"); // KeyEvent.VK_META
        assertThat(robot.events).contains("release:157");
        assertThat(robot.events).contains("press:86"); // KeyEvent.VK_V
        assertThat(robot.events).contains("release:86");
    }

    @Test
    void pasteShortcutUsesControl() {
        FakeRobot robot = new FakeRobot();
        RobotTypingAdapter.pasteShortcut(robot, "CONTROL+V");

        assertThat(robot.events).contains("press:17"); // KeyEvent.VK_CONTROL
        assertThat(robot.events).contains("release:17");
        assertThat(robot.events).contains("press:86"); // KeyEvent.VK_V
        assertThat(robot.events).contains("release:86");
    }
}
