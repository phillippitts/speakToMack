package com.phillippitts.speaktomack.util;

/** Utility for privacy-safe logging of text previews. */
public final class LogSanitizer {
    private LogSanitizer() {}

    /**
     * Truncate the input string to at most max characters; returns "" for null.
     */
    public static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (max <= 0) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
