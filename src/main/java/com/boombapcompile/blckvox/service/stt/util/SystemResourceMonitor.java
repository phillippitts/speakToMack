package com.boombapcompile.blckvox.service.stt.util;

import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

/**
 * Lightweight monitor for system CPU and memory usage.
 *
 * <p>Uses {@link com.sun.management.OperatingSystemMXBean} for CPU load and
 * {@link MemoryMXBean} for heap memory usage. Called on demand — no background threads.
 */
@Component
public class SystemResourceMonitor {

    private final com.sun.management.OperatingSystemMXBean osMxBean;
    private final MemoryMXBean memoryMxBean;

    public SystemResourceMonitor() {
        this.osMxBean = (com.sun.management.OperatingSystemMXBean)
                ManagementFactory.getOperatingSystemMXBean();
        this.memoryMxBean = ManagementFactory.getMemoryMXBean();
    }

    /**
     * Returns system CPU load as a value between 0.0 and 1.0.
     * Returns 0.0 if the value is not available (negative from JMX).
     */
    public double getCpuLoad() {
        double load = osMxBean.getCpuLoad();
        return load < 0 ? 0.0 : load;
    }

    /**
     * Returns heap memory usage ratio as a value between 0.0 and 1.0.
     */
    public double getMemoryUsage() {
        long used = memoryMxBean.getHeapMemoryUsage().getUsed();
        long max = memoryMxBean.getHeapMemoryUsage().getMax();
        if (max <= 0) {
            return 0.0;
        }
        return (double) used / max;
    }
}
