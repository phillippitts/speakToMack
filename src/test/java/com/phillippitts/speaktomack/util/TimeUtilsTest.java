package com.phillippitts.speaktomack.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeUtilsTest {

    @Test
    void shouldConvertNanosToMillis() {
        assertThat(TimeUtils.nanosToMillis(1_000_000L)).isEqualTo(1L);
        assertThat(TimeUtils.nanosToMillis(5_000_000L)).isEqualTo(5L);
        assertThat(TimeUtils.nanosToMillis(100_000_000L)).isEqualTo(100L);
    }

    @Test
    void shouldTruncateNanosToMillis() {
        // 1.5 milliseconds truncates to 1 millisecond
        assertThat(TimeUtils.nanosToMillis(1_500_000L)).isEqualTo(1L);
        // 2.999 milliseconds truncates to 2 milliseconds
        assertThat(TimeUtils.nanosToMillis(2_999_999L)).isEqualTo(2L);
    }

    @Test
    void shouldHandleZeroNanos() {
        assertThat(TimeUtils.nanosToMillis(0L)).isEqualTo(0L);
    }

    @Test
    void shouldHandleNegativeNanos() {
        assertThat(TimeUtils.nanosToMillis(-1_000_000L)).isEqualTo(-1L);
    }

    @Test
    void shouldCalculateElapsedMillis() {
        long startNanos = System.nanoTime();

        // Sleep for a short period
        try {
            Thread.sleep(10); // 10 milliseconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long elapsedMs = TimeUtils.elapsedMillis(startNanos);

        // Elapsed time should be at least 10ms (with some tolerance for timing precision)
        assertThat(elapsedMs).isGreaterThanOrEqualTo(5L);
        // Should be less than 100ms (generous upper bound to avoid flaky tests)
        assertThat(elapsedMs).isLessThan(100L);
    }

    @Test
    void shouldCalculateElapsedMillisFromPastTimestamp() {
        // Simulate a timestamp from 1 second ago
        long startNanos = System.nanoTime() - (1_000L * TimeUtils.NANOS_PER_MILLI);

        long elapsedMs = TimeUtils.elapsedMillis(startNanos);

        // Should be approximately 1000ms (with tolerance)
        assertThat(elapsedMs).isGreaterThanOrEqualTo(950L);
        assertThat(elapsedMs).isLessThan(1050L);
    }

    @Test
    void shouldCalculateZeroElapsedForCurrentTime() {
        long startNanos = System.nanoTime();
        long elapsedMs = TimeUtils.elapsedMillis(startNanos);

        // Elapsed time should be very close to 0 (within a few milliseconds)
        assertThat(elapsedMs).isLessThan(5L);
    }

    @Test
    void shouldUseCorrectNanosPerMilliConstant() {
        assertThat(TimeUtils.NANOS_PER_MILLI).isEqualTo(1_000_000L);
    }
}
