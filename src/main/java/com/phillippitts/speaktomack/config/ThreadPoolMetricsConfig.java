package com.phillippitts.speaktomack.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration for thread pool metrics exposure via Micrometer.
 *
 * <p>Exposes detailed metrics for the STT executor thread pool including:
 * <ul>
 *   <li>stt.pool.size - Current number of threads in the pool</li>
 *   <li>stt.pool.active - Number of actively executing tasks</li>
 *   <li>stt.pool.queued - Number of tasks waiting in the queue</li>
 *   <li>stt.pool.completed - Cumulative count of completed tasks</li>
 *   <li>stt.pool.core.size - Configured core pool size</li>
 *   <li>stt.pool.max.size - Configured maximum pool size</li>
 * </ul>
 *
 * <p>These metrics are available via:
 * <ul>
 *   <li>HTTP: {@code GET /actuator/metrics/stt.pool.active}</li>
 *   <li>Prometheus: {@code stt_pool_active}</li>
 * </ul>
 *
 * <p>Additionally logs a health summary every 5 minutes for operational visibility.
 */
@Configuration
public class ThreadPoolMetricsConfig {

    private static final Logger LOG = LogManager.getLogger(ThreadPoolMetricsConfig.class);

    private final ObjectProvider<ThreadPoolTaskExecutor> sttExecutorProvider;

    public ThreadPoolMetricsConfig(
            @Qualifier("sttExecutor") ObjectProvider<ThreadPoolTaskExecutor> sttExecutorProvider) {
        this.sttExecutorProvider = sttExecutorProvider;
    }

    /**
     * Binds STT executor thread pool metrics to Micrometer registry.
     *
     * @return MeterBinder that registers custom metrics
     */
    @Bean
    public MeterBinder sttExecutorMetrics() {
        return registry -> {
            ThreadPoolTaskExecutor sttExecutor = this.sttExecutorProvider.getObject();
            ThreadPoolExecutor executor = sttExecutor.getThreadPoolExecutor();

            Gauge.builder("stt.pool.size", executor, ThreadPoolExecutor::getPoolSize)
                    .description("Current number of threads in the STT pool")
                    .register(registry);

            Gauge.builder("stt.pool.active", executor, ThreadPoolExecutor::getActiveCount)
                    .description("Number of threads actively executing STT tasks")
                    .register(registry);

            Gauge.builder("stt.pool.queued", executor, e -> e.getQueue().size())
                    .description("Number of STT tasks waiting in the queue")
                    .register(registry);

            Gauge.builder("stt.pool.completed", executor, ThreadPoolExecutor::getCompletedTaskCount)
                    .description("Cumulative count of completed STT tasks")
                    .register(registry);

            Gauge.builder("stt.pool.core.size", executor, ThreadPoolExecutor::getCorePoolSize)
                    .description("Configured core pool size for STT executor")
                    .register(registry);

            Gauge.builder("stt.pool.max.size", executor, ThreadPoolExecutor::getMaximumPoolSize)
                    .description("Configured maximum pool size for STT executor")
                    .register(registry);

            LOG.info("STT thread pool metrics registered: stt.pool.* available via /actuator/metrics");
        };
    }

    /**
     * Logs thread pool health summary every 5 minutes for operational monitoring.
     */
    @Scheduled(fixedRate = 300_000) // 5 minutes
    public void logThreadPoolHealth() {
        ThreadPoolTaskExecutor sttExecutor = this.sttExecutorProvider.getObject();
        ThreadPoolExecutor executor = sttExecutor.getThreadPoolExecutor();

        LOG.info("STT Thread Pool Health: size={}/{}, active={}, queued={}, completed={}",
                executor.getPoolSize(),
                executor.getMaximumPoolSize(),
                executor.getActiveCount(),
                executor.getQueue().size(),
                executor.getCompletedTaskCount()
        );
    }
}
