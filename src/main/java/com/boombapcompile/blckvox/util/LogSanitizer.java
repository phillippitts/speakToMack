package com.boombapcompile.blckvox.util;

import java.util.regex.Pattern;

/** Utility for privacy-safe logging of text previews. */
public final class LogSanitizer {
    private LogSanitizer() {}

    /** Pattern to match ANSI escape sequences. */
    private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[;\\d]*[A-Za-z]");

    /**
     * Sanitize and truncate the input string for safe logging.
     * Strips control characters, ANSI escapes, and truncates to max length.
     * Returns "" for null.
     */
    public static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (max <= 0) {
            return "";
        }
        String sanitized = sanitize(s);
        return sanitized.length() <= max ? sanitized : sanitized.substring(0, max);
    }

    /**
     * Sanitize a string for safe inclusion in log output.
     * Replaces newlines, tabs, and other control characters with safe representations.
     * Strips ANSI escape sequences.
     */
    public static String sanitize(String s) {
        if (s == null) {
            return "";
        }
        // Strip ANSI escape sequences
        String result = ANSI_PATTERN.matcher(s).replaceAll("");
        // Replace control characters with safe representations
        StringBuilder sb = new StringBuilder(result.length());
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\t') {
                sb.append("\\t");
            } else if (Character.isISOControl(c)) {
                sb.append("\\x").append(String.format("%02x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
