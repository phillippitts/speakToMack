package com.phillippitts.blckvox.service.stt.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicConcurrencyGuardTest {

    @Test
    void shouldStartWithConfiguredMaxPermits() {
        DynamicConcurrencyGuard guard = new DynamicConcurrencyGuard(4, 1000, "test", null);
        assertThat(guard.getCurrentPermits()).isEqualTo(4);
        assertThat(guard.getConfiguredMax()).isEqualTo(4);
    }

    @Test
    void shouldIncreasePermits() {
        DynamicConcurrencyGuard guard = new DynamicConcurrencyGuard(4, 1000, "test", null);

        // Decrease first to have room to increase
        guard.adjustPermits(2);
        assertThat(guard.getCurrentPermits()).isEqualTo(2);

        // Increase back
        guard.adjustPermits(4);
        assertThat(guard.getCurrentPermits()).isEqualTo(4);
    }

    @Test
    void shouldDecreasePermitsBestEffort() {
        DynamicConcurrencyGuard guard = new DynamicConcurrencyGuard(4, 1000, "test", null);

        guard.adjustPermits(2);
        assertThat(guard.getCurrentPermits()).isEqualTo(2);
    }

    @Test
    void shouldClampToMinimumOfOne() {
        DynamicConcurrencyGuard guard = new DynamicConcurrencyGuard(4, 1000, "test", null);

        guard.adjustPermits(0);
        assertThat(guard.getCurrentPermits()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldClampToConfiguredMax() {
        DynamicConcurrencyGuard guard = new DynamicConcurrencyGuard(4, 1000, "test", null);

        guard.adjustPermits(10);
        assertThat(guard.getCurrentPermits()).isEqualTo(4);
    }

    @Test
    void shouldHandleNoOpAdjustment() {
        DynamicConcurrencyGuard guard = new DynamicConcurrencyGuard(4, 1000, "test", null);

        guard.adjustPermits(4);
        assertThat(guard.getCurrentPermits()).isEqualTo(4);
    }

    @Test
    void shouldAcquireAndRelease() {
        DynamicConcurrencyGuard guard = new DynamicConcurrencyGuard(2, 1000, "test", null);

        guard.acquire();
        guard.acquire();
        guard.release();
        guard.release();
        // Should not throw — permits were returned
    }

    @Test
    void shouldEnforceMinimumOnePermit() {
        DynamicConcurrencyGuard guard = new DynamicConcurrencyGuard(1, 1000, "test", null);
        assertThat(guard.getConfiguredMax()).isEqualTo(1);

        guard.adjustPermits(-5);
        assertThat(guard.getCurrentPermits()).isGreaterThanOrEqualTo(1);
    }
}
