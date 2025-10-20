package com.phillippitts.speaktomack.util;

/**
 * Utility methods for time conversions and elapsed time calculations.
 *
 * <p>Provides convenient methods for converting between nanoseconds and milliseconds,
 * commonly used for performance timing with {@link System#nanoTime()}.
 *
 * @since 1.0
 */
public final class TimeUtils {

    /**
     * Number of nanoseconds in one millisecond.
     */
    public static final long NANOS_PER_MILLI = 1_000_000L;

    private TimeUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Converts nanoseconds to milliseconds.
     *
     * @param nanos time in nanoseconds
     * @return time in milliseconds (truncated)
     */
    public static long nanosToMillis(long nanos) {
        return nanos / NANOS_PER_MILLI;
    }

    /**
     * Calculates elapsed milliseconds since a nanosecond timestamp.
     *
     * <p>Typical usage:
     * <pre>
     * long startTime = System.nanoTime();
     * // ... do work ...
     * long elapsedMs = TimeUtils.elapsedMillis(startTime);
     * </pre>
     *
     * @param startNanos start time from {@link System#nanoTime()}
     * @return elapsed milliseconds since startNanos
     */
    public static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / NANOS_PER_MILLI;
    }
}
