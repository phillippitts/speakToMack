package com.phillippitts.speaktomack.service.fallback;

import com.phillippitts.speaktomack.config.typing.TypingProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.awt.AWTException;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Objects;

/** Tier 2 typing via system clipboard. */
@Component
class ClipboardTypingAdapter implements TypingAdapter {
    private static final Logger LOG = LogManager.getLogger(ClipboardTypingAdapter.class);

    interface ClipboardFacade {
        Clipboard getSystemClipboard();
    }

    static final class AwtClipboardFacade implements ClipboardFacade {
        @Override
        public Clipboard getSystemClipboard() {
            return Toolkit.getDefaultToolkit().getSystemClipboard();
        }
    }

    private final TypingProperties props;
    private final ClipboardFacade clipboard;

    @org.springframework.beans.factory.annotation.Autowired
    public ClipboardTypingAdapter(TypingProperties props) {
        this(props, new AwtClipboardFacade());
    }

    // package-private for tests
    ClipboardTypingAdapter(TypingProperties props, ClipboardFacade facade) {
        this.props = Objects.requireNonNull(props);
        this.clipboard = facade;
    }

    @Override
    public boolean canType() {
        return true;
    }

    @Override
    public boolean type(String text) {
        if (text == null) {
            text = "";
        }
        // Normalize newlines
        text = normalize(text);
        // Optionally trim
        if (props.isTrimTrailingNewline()) {
            while (text.endsWith("\n") || text.endsWith("\r")) {
                text = text.substring(0, text.length() - 1);
            }
        }
        Clipboard cb = clipboard.getSystemClipboard();
        Object prior = null;
        // Only save prior clipboard if we plan to restore it (and not clipboard-only mode)
        if (props.isRestoreClipboard() && !props.isClipboardOnlyFallback()) {
            try {
                if (cb.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    prior = cb.getData(DataFlavor.stringFlavor);
                }
            } catch (UnsupportedFlavorException | IOException ignored) {
                // Ignore failures reading prior clipboard
            }
        }
        try {
            cb.setContents(new StringSelection(text), null);
            if (!props.isClipboardOnlyFallback()) {
                // Issue paste shortcut appropriate for OS
                try {
                    RobotTypingAdapter.RobotFacade rf = new RobotTypingAdapter.AwtRobotFacade();
                    RobotTypingAdapter.pasteShortcut(rf, props.getPasteShortcut());
                } catch (AWTException e) {
                    LOG.debug("Robot unavailable for clipboard paste shortcut: {}", e.toString());
                    // Fall through - text is on clipboard even if paste shortcut failed
                }
            }
            return true;
        } catch (Exception e) {
            LOG.warn("Clipboard typing failed: {}", e.toString());
            return false;
        } finally {
            // Restore clipboard only if we saved prior content
            if (prior != null && props.isRestoreClipboard() && !props.isClipboardOnlyFallback()) {
                try {
                    cb.setContents(new StringSelection(String.valueOf(prior)), null);
                } catch (Exception ignored) {
                    // Ignore failures restoring clipboard
                }
            }
        }
    }

    @Override
    public String name() {
        return "clipboard";
    }

    private String normalize(String s) {
        return switch (props.getNormalizeNewlines()) {
            case LF -> s.replace("\r\n", "\n").replace('\r', '\n');
            case CRLF -> s.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\r\n");
            case NONE -> s;
        };
    }
}
