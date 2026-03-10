package com.phillippitts.blckvox.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Typed properties controlling paste/typing fallback behavior.
 * Privacy defaults: clipboard restore enabled; INFO logs never include full text.
 */
@Validated
@ConfigurationProperties(prefix = "typing")
public record TypingProperties(
        @DefaultValue("800")
        @Min(100)
        @Max(2000)
        int chunkSize,

        @DefaultValue("30")
        @Min(0)
        @Max(500)
        int interChunkDelayMs,

        @DefaultValue("100")
        @Min(0)
        @Max(1000)
        int focusDelayMs,

        // Whether to restore prior clipboard contents after paste.
        @DefaultValue("true")
        boolean restoreClipboard,

        // If true, do not send paste shortcut, only place text on clipboard.
        @DefaultValue("false")
        boolean clipboardOnlyFallback,

        // Normalize newlines before paste.
        @DefaultValue("LF")
        @NotNull
        NewlineMode normalizeNewlines,

        // Trim trailing newline at end of text.
        @DefaultValue("true")
        boolean trimTrailingNewline,

        // Enable Robot-based typing (Tier 1). If false, skip to clipboard tier.
        @DefaultValue("true")
        boolean enableRobot,

        // Optional override for paste shortcut: os-default | META+V | CONTROL+V.
        @DefaultValue("os-default")
        String pasteShortcut,

        // Delay in ms before restoring clipboard after paste (gives target app time to process).
        @DefaultValue("200")
        @Min(50)
        @Max(2000)
        int clipboardRestoreDelayMs
) {

    public enum NewlineMode { LF, CRLF, NONE }

    public boolean isRestoreClipboard() {
        return restoreClipboard;
    }

    public boolean isClipboardOnlyFallback() {
        return clipboardOnlyFallback;
    }

    public boolean isTrimTrailingNewline() {
        return trimTrailingNewline;
    }

    public boolean isEnableRobot() {
        return enableRobot;
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

    public NewlineMode getNormalizeNewlines() {
        return normalizeNewlines;
    }

    public String getPasteShortcut() {
        return pasteShortcut;
    }

    public int getClipboardRestoreDelayMs() {
        return clipboardRestoreDelayMs;
    }
}
