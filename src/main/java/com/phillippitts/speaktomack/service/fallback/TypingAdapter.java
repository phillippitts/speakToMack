package com.phillippitts.speaktomack.service.fallback;

/** Strategy for delivering text to the active application. */
interface TypingAdapter {
    /** @return true if adapter is available in current environment */
    boolean canType();

    /** Perform paste/typing; return true on success. */
    boolean type(String text);

    /** Name for logs/metrics. */
    String name();
}
