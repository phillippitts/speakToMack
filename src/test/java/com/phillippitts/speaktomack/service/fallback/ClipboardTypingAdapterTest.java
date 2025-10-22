package com.phillippitts.speaktomack.service.fallback;

import com.phillippitts.speaktomack.config.properties.TypingProperties;
import org.junit.jupiter.api.Test;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ClipboardTypingAdapterTest {

    static class FakeClipboard extends Clipboard {
        String contents;

        FakeClipboard() {
            super("fake");
        }
        @Override public synchronized void setContents(Transferable contents, ClipboardOwner owner) {
            try {
                this.contents = (String) contents.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
            } catch (UnsupportedFlavorException | IOException e) {
                this.contents = null;
            }
        }
        @Override public synchronized Transferable getContents(Object requestor) {
            String s = contents;
            return new java.awt.datatransfer.StringSelection(s == null ? "" : s);
        }
    }

    static class FakeFacade implements ClipboardTypingAdapter.ClipboardFacade {
        final FakeClipboard clipboard = new FakeClipboard();

        @Override
        public Clipboard getSystemClipboard() {
            return clipboard;
        }
    }

    @Test
    void savesAndRestoresClipboardAndNormalizesLf() {
        TypingProperties props = new TypingProperties(
                800, 0, 0, true, true,
                TypingProperties.NewlineMode.LF, true, false, "os-default"
        );
        FakeFacade facade = new FakeFacade();
        // Seed prior content
        facade.clipboard.setContents(new java.awt.datatransfer.StringSelection("orig"), null);

        ClipboardTypingAdapter adapter = new ClipboardTypingAdapter(props, facade);
        boolean ok = adapter.type("hello\r\nworld\r\n");
        assertThat(ok).isTrue();
        // Clipboard-only fallback leaves normalized AND TRIMMED text (trimTrailingNewline=true)
        assertThat(facade.clipboard.contents).isEqualTo("hello\nworld");

        // With clipboardOnlyFallback=false, content should be restored to prior after paste
        TypingProperties props2 = new TypingProperties(
                800, 0, 0, true, false,
                TypingProperties.NewlineMode.LF, true, false, "os-default"
        );
        facade.clipboard.setContents(new java.awt.datatransfer.StringSelection("orig"), null);
        ClipboardTypingAdapter adapter2 = new ClipboardTypingAdapter(props2, facade);
        boolean ok2 = adapter2.type("abc\r\n");
        assertThat(ok2).isTrue();
        assertThat(facade.clipboard.contents).isEqualTo("orig");
    }
}
