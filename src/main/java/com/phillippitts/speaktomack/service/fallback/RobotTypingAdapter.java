package com.phillippitts.speaktomack.service.fallback;

import com.phillippitts.speaktomack.config.typing.TypingProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.util.Objects;

/**
 * Tier 1 typing via java.awt.Robot. Requires Accessibility permission on macOS.
 * Uses paste shortcut (Command/Control+V) and types text via the clipboard to
 * remain keyboard-layout agnostic.
 *
 * For hermetic tests, RobotFaçade can be replaced.
 */
@Component
public class RobotTypingAdapter implements TypingAdapter {
    private static final Logger LOG = LogManager.getLogger(RobotTypingAdapter.class);

    interface RobotFacade {
        void keyPress(int keyCode);
        void keyRelease(int keyCode);
        void delay(int ms);
    }

    static final class AwtRobotFacade implements RobotFacade {
        private final Robot robot;

        AwtRobotFacade() throws AWTException {
            this.robot = new Robot();
        }

        @Override
        public void keyPress(int keyCode) {
            robot.keyPress(keyCode);
        }

        @Override
        public void keyRelease(int keyCode) {
            robot.keyRelease(keyCode);
        }

        @Override
        public void delay(int ms) {
            robot.delay(ms);
        }
    }

    private final TypingProperties props;
    private final RobotFacade robot;

    @org.springframework.beans.factory.annotation.Autowired
        public RobotTypingAdapter(TypingProperties props) {
        this(props, createRobotFacade());
    }

    // Package-private for tests
    RobotTypingAdapter(TypingProperties props, RobotFacade facade) {
        this.props = Objects.requireNonNull(props);
        this.robot = facade; // may be a fake in tests
    }

    private static RobotFacade createRobotFacade() {
        try {
            return new AwtRobotFacade();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public boolean canType() {
        if (!props.isEnableRobot()) {
            return false;
        }
        return robot != null;
    }

    @Override
    public boolean type(String text) {
        if (!canType()) {
            return false;
        }
        if (text == null) {
            text = "";
        }
        try {
            // Focus delay before pasting
            if (props.getFocusDelayMs() > 0) {
                robot.delay(props.getFocusDelayMs());
            }

            // For very long text, chunk via multiple pastes to avoid buffer overflow issues
            // We choose clipboard-paste per chunk to avoid keystroke replay issues across layouts
            int chunkSize = props.getChunkSize();
            if (text.length() <= chunkSize) {
                // Short text: single paste
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(text), null);
                pasteShortcut(robot, props.getPasteShortcut());
            } else {
                // Long text: paste in chunks
                int i = 0;
                while (i < text.length()) {
                    if (i > 0 && props.getInterChunkDelayMs() > 0) {
                        robot.delay(props.getInterChunkDelayMs());
                    }
                    String segment = text.substring(i, Math.min(text.length(), i + chunkSize));
                    Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(new StringSelection(segment), null);
                    pasteShortcut(robot, props.getPasteShortcut());
                    i += chunkSize;
                }
            }
            return true;
        } catch (Exception e) {
            LOG.warn("Robot typing failed: {}", e.toString());
            return false;
        }
    }

    @Override
    public String name() {
        return "robot";
    }

    static void pasteShortcut(RobotFacade robot, String mode) {
        boolean mac = System.getProperty("os.name").toLowerCase().contains("mac");
        int modKey = mac ? KeyEvent.VK_META : KeyEvent.VK_CONTROL;
        if ("META+V".equalsIgnoreCase(mode)) {
            modKey = KeyEvent.VK_META;
        } else if ("CONTROL+V".equalsIgnoreCase(mode)) {
            modKey = KeyEvent.VK_CONTROL;
        }
        robot.keyPress(modKey);
        robot.keyPress(KeyEvent.VK_V);
        robot.keyRelease(KeyEvent.VK_V);
        robot.keyRelease(modKey);
    }
}
