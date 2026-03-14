package com.boombapcompile.blckvox.service.stt.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemResourceMonitorTest {

    private final SystemResourceMonitor monitor = new SystemResourceMonitor();

    @Test
    void cpuLoadReturnsNonNegativeValue() {
        double cpu = monitor.getCpuLoad();
        assertThat(cpu).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void memoryUsageReturnsBoundedValue() {
        double memory = monitor.getMemoryUsage();
        assertThat(memory).isBetween(0.0, 1.0);
    }
}
