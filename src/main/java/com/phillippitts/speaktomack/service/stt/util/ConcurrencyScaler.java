package com.phillippitts.speaktomack.service.stt.util;

import com.phillippitts.speaktomack.config.properties.SttConcurrencyProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodically adjusts concurrency permits for STT engines based on system resource usage.
 *
 * <p>Reads CPU and memory pressure from {@link SystemResourceMonitor} and scales each
 * registered {@link DynamicConcurrencyGuard} proportionally. Only active when
 * {@code stt.concurrency.dynamic-scaling-enabled=true}.
 */
@Component
@ConditionalOnProperty(prefix = "stt.concurrency", name = "dynamic-scaling-enabled", havingValue = "true")
public class ConcurrencyScaler {

    private static final Logger LOG = LogManager.getLogger(ConcurrencyScaler.class);

    private final SttConcurrencyProperties props;
    private final SystemResourceMonitor monitor;
    private final Map<String, DynamicConcurrencyGuard> guards = new ConcurrentHashMap<>();

    public ConcurrencyScaler(SttConcurrencyProperties props, SystemResourceMonitor monitor) {
        this.props = props;
        this.monitor = monitor;
        LOG.info("ConcurrencyScaler initialized: cpuThreshold={}, memoryThreshold={}, interval={}ms",
                props.getCpuThresholdHigh(), props.getMemoryThresholdHigh(), props.getScalingIntervalMs());
    }

    /**
     * Registers a guard for dynamic scaling.
     *
     * @param engineName engine identifier
     * @param guard the dynamic concurrency guard
     */
    public void registerGuard(String engineName, DynamicConcurrencyGuard guard) {
        guards.put(engineName, guard);
        LOG.info("Registered dynamic guard for engine={} (max={})", engineName, guard.getConfiguredMax());
    }

    @Scheduled(fixedRateString = "#{${stt.concurrency.scaling-interval-ms:5000}}")
    void scale() {
        if (guards.isEmpty()) {
            return;
        }

        double cpu = monitor.getCpuLoad();
        double memory = monitor.getMemoryUsage();

        double cpuPressure = Math.max(0.0, (cpu - props.getCpuThresholdHigh()) / (1.0 - props.getCpuThresholdHigh()));
        double memPressure = Math.max(0.0, (memory - props.getMemoryThresholdHigh()) / (1.0 - props.getMemoryThresholdHigh()));
        double pressure = Math.max(cpuPressure, memPressure);

        guards.forEach((name, guard) -> {
            int target = (int) Math.floor(guard.getConfiguredMax() * (1.0 - pressure));
            target = Math.max(1, Math.min(target, guard.getConfiguredMax()));
            guard.adjustPermits(target);
        });

        LOG.debug("ConcurrencyScaler: cpu={}, memory={}, pressure={}",
                String.format("%.2f", cpu), String.format("%.2f", memory), String.format("%.2f", pressure));
    }
}
