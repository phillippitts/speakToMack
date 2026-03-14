package com.boombapcompile.blckvox.service.stt.util;

import com.boombapcompile.blckvox.config.properties.SttConcurrencyProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConcurrencyScalerTest {

    @Test
    void scaleReturnsEarlyWhenNoGuards() {
        SttConcurrencyProperties props = new SttConcurrencyProperties(
                4, 2, 1000, true, 0.80, 0.85, 5000);
        SystemResourceMonitor monitor = mock(SystemResourceMonitor.class);

        ConcurrencyScaler scaler = new ConcurrencyScaler(props, monitor);
        // Should not throw - just returns early
        scaler.scale();
    }

    @Test
    void registerGuardAddsGuard() {
        SttConcurrencyProperties props = new SttConcurrencyProperties(
                4, 2, 1000, true, 0.80, 0.85, 5000);
        SystemResourceMonitor monitor = mock(SystemResourceMonitor.class);
        when(monitor.getCpuLoad()).thenReturn(0.5);
        when(monitor.getMemoryUsage()).thenReturn(0.5);

        ConcurrencyScaler scaler = new ConcurrencyScaler(props, monitor);
        DynamicConcurrencyGuard guard = new DynamicConcurrencyGuard(4, 1000, "vosk", null);
        scaler.registerGuard("vosk", guard);

        // scale should now process the guard without error
        scaler.scale();
        assertThat(guard.getCurrentPermits()).isEqualTo(4);
    }

    @Test
    void scaleReducesPermitsUnderHighCpuPressure() {
        SttConcurrencyProperties props = new SttConcurrencyProperties(
                4, 2, 1000, true, 0.80, 0.85, 5000);
        SystemResourceMonitor monitor = mock(SystemResourceMonitor.class);
        when(monitor.getCpuLoad()).thenReturn(0.95); // High CPU
        when(monitor.getMemoryUsage()).thenReturn(0.5);

        ConcurrencyScaler scaler = new ConcurrencyScaler(props, monitor);
        DynamicConcurrencyGuard guard = new DynamicConcurrencyGuard(4, 1000, "vosk", null);
        scaler.registerGuard("vosk", guard);

        scaler.scale();
        // With 0.95 CPU and 0.80 threshold: pressure = (0.95 - 0.80) / (1.0 - 0.80) = 0.75
        // target = floor(4 * (1.0 - 0.75)) = floor(4 * 0.25) = 1
        assertThat(guard.getCurrentPermits()).isEqualTo(1);
    }

    @Test
    void scaleReducesPermitsUnderHighMemoryPressure() {
        SttConcurrencyProperties props = new SttConcurrencyProperties(
                4, 2, 1000, true, 0.80, 0.85, 5000);
        SystemResourceMonitor monitor = mock(SystemResourceMonitor.class);
        when(monitor.getCpuLoad()).thenReturn(0.3);
        when(monitor.getMemoryUsage()).thenReturn(0.95); // High memory

        ConcurrencyScaler scaler = new ConcurrencyScaler(props, monitor);
        DynamicConcurrencyGuard guard = new DynamicConcurrencyGuard(4, 1000, "vosk", null);
        scaler.registerGuard("vosk", guard);

        scaler.scale();
        // memPressure = (0.95 - 0.85) / (1.0 - 0.85) = 0.667
        // target = floor(4 * 0.333) = 1
        assertThat(guard.getCurrentPermits()).isLessThan(4);
    }

    @Test
    void scaleKeepsPermitsAtMaxUnderLowPressure() {
        SttConcurrencyProperties props = new SttConcurrencyProperties(
                4, 2, 1000, true, 0.80, 0.85, 5000);
        SystemResourceMonitor monitor = mock(SystemResourceMonitor.class);
        when(monitor.getCpuLoad()).thenReturn(0.3);
        when(monitor.getMemoryUsage()).thenReturn(0.3);

        ConcurrencyScaler scaler = new ConcurrencyScaler(props, monitor);
        DynamicConcurrencyGuard guard = new DynamicConcurrencyGuard(4, 1000, "vosk", null);
        scaler.registerGuard("vosk", guard);

        scaler.scale();
        assertThat(guard.getCurrentPermits()).isEqualTo(4);
    }
}
