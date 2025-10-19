package com.phillippitts.speaktomack.config.typing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

/**
 * Typed properties controlling paste/typing fallback behavior.
 *
 * Privacy defaults: clipboard restore enabled; INFO logs never include full text.
 */
@Validated
@ConfigurationProperties(prefix = "typing")
public class TypingProperties {

    public enum NewlineMode { LF, CRLF, NONE }

    @Min(100)
    @Max(2000)
    private final int chunkSize;

    @Min(0)
    @Max(500)
    private final int interChunkDelayMs;

    @Min(0)
    @Max(1000)
    private final int focusDelayMs;

    /** Whether to restore prior clipboard contents after paste. */
    private final boolean restoreClipboard;

    /** If true, do not send paste shortcut, only place text on clipboard. */
    private final boolean clipboardOnlyFallback;

    /** Normalize newlines before paste. */
    @NotNull
    private final NewlineMode normalizeNewlines;

    /** Trim trailing newline at end of text. */
    private final boolean trimTrailingNewline;

    /** Enable Robot-based typing (Tier 1). If false, skip to clipboard tier. */
    private final boolean enableRobot;

    /** Optional override for paste shortcut: os-default | META+V | CONTROL+V. */
    private final String pasteShortcut;

    @ConstructorBinding
    public TypingProperties(Integer chunkSize,
                            Integer interChunkDelayMs,
                            Integer focusDelayMs,
                            Boolean restoreClipboard,
                            Boolean clipboardOnlyFallback,
                            NewlineMode normalizeNewlines,
                            Boolean trimTrailingNewline,
                            Boolean enableRobot,
                            String pasteShortcut) {
        this.chunkSize = chunkSize == null ? 800 : chunkSize;
        this.interChunkDelayMs = interChunkDelayMs == null ? 30 : interChunkDelayMs;
        this.focusDelayMs = focusDelayMs == null ? 100 : focusDelayMs;
        this.restoreClipboard = restoreClipboard == null ? true : restoreClipboard;
        this.clipboardOnlyFallback = clipboardOnlyFallback == null ? false : clipboardOnlyFallback;
        this.normalizeNewlines = normalizeNewlines == null ? NewlineMode.LF : normalizeNewlines;
        this.trimTrailingNewline = trimTrailingNewline == null ? true : trimTrailingNewline;
        this.enableRobot = enableRobot == null ? true : enableRobot;
        this.pasteShortcut = (pasteShortcut == null || pasteShortcut.isBlank()) ? "os-default" : pasteShortcut;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getInterChunkDelayMs() {
        return interChunkDelayMs;
    }

    public int getFocusDelayMs() {
        return focusDelayMs;
    }

    public boolean isRestoreClipboard() {
        return restoreClipboard;
    }

    public boolean isClipboardOnlyFallback() {
        return clipboardOnlyFallback;
    }

    public NewlineMode getNormalizeNewlines() {
        return normalizeNewlines;
    }

    public boolean isTrimTrailingNewline() {
        return trimTrailingNewline;
    }

    public boolean isEnableRobot() {
        return enableRobot;
    }

    public String getPasteShortcut() {
        return pasteShortcut;
    }
}
