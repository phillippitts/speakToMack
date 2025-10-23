package com.phillippitts.speaktomack.config;

import com.phillippitts.speaktomack.config.properties.ThreadPoolProperties;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration for thread pools used in asynchronous processing.
 * Provides optimized executors for parallel STT engine transcription and event offload.
 *
 * <p>Pool sizes are configured via {@link ThreadPoolProperties} and can be tuned
 * in application.properties based on hardware and workload.
 */
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    private final ThreadPoolProperties threadPoolProperties;

    public ThreadPoolConfig(ThreadPoolProperties threadPoolProperties) {
        this.threadPoolProperties = threadPoolProperties;
    }

    /**
     * Creates an optimized thread pool for parallel STT engine processing.
     *
     * <p>Pool sizing strategy configured via {@code threadpool.stt.*} properties:
     * <ul>
     *   <li>Core pool: default 4 - handles typical load</li>
     *   <li>Max pool: default 8 - handles burst traffic</li>
     *   <li>Queue: default 50 tasks - prevents unbounded memory growth</li>
     * </ul>
     *
     * <p>Rejection policy: {@link ThreadPoolExecutor.CallerRunsPolicy}
     * When the pool and queue are full, the caller thread executes the task,
     * providing backpressure instead of failing fast.
     *
     * <p>Thread naming: configured via {@code threadpool.stt.thread-name-prefix}
     * for easy identification in logs and profilers.
     *
     * <p>MDC propagation: Copies Log4j2 ThreadContext (MDC) from the submitting thread to
     * the worker thread to preserve request/user correlation IDs in async logs.
     *
     * @return Configured executor for STT engine processing
     */
    @Bean(name = "sttExecutor")
    public Executor sttExecutor() {
        ThreadPoolProperties.SttPoolProperties sttProps = threadPoolProperties.getStt();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(sttProps.getCorePoolSize());
        executor.setMaxPoolSize(sttProps.getMaxPoolSize());
        executor.setQueueCapacity(sttProps.getQueueCapacity());
        executor.setThreadNamePrefix(sttProps.getThreadNamePrefix());
        executor.setKeepAliveSeconds(sttProps.getKeepAliveSeconds());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        // Propagate MDC to worker threads
        executor.setTaskDecorator(runnable -> {
            Map<String, String> contextMap = ThreadContext.getImmutableContext();
            return () -> {
                Map<String, String> previous = ThreadContext.getImmutableContext();
                try {
                    if (contextMap != null && !contextMap.isEmpty()) {
                        ThreadContext.putAll(contextMap);
                    }
                    runnable.run();
                } finally {
                    ThreadContext.clearAll();
                    if (previous != null && !previous.isEmpty()) {
                        ThreadContext.putAll(previous);
                    }
                }
            };

        });

        executor.initialize();
        return executor;
    }

    /**
     * Creates a bounded thread pool for event listener offload (transcription work).
     *
     * <p>This executor offloads CPU-intensive transcription work from Spring's event bus
     * to prevent blocking other event listeners. Pool sizing is conservative to avoid
     * resource contention with STT engine threads.
     *
     * <p>Pool sizing strategy:
     * <ul>
     *   <li>Core pool: 2 threads - handles typical hotkey press rate (1-2 concurrent events)</li>
     *   <li>Max pool: 4 threads - handles burst scenarios (multiple rapid hotkey presses)</li>
     *   <li>Queue: 10 tasks - bounded to prevent memory exhaustion from runaway events</li>
     * </ul>
     *
     * <p>Rejection policy: {@link ThreadPoolExecutor.CallerRunsPolicy}
     * When pool and queue are full, the event bus thread executes the task, providing
     * backpressure instead of dropping events.
     *
     * <p>Thread naming: {@code event-pool-N} for easy identification in logs.
     *
     * <p>MDC propagation: Copies Log4j2 ThreadContext from event publisher to worker thread.
     *
     * @return Configured executor for event listener offload
     * @since 1.1
     */
    @Bean(name = "eventExecutor")
    public Executor eventExecutor() {
        ThreadPoolProperties.EventPoolProperties eventProps = threadPoolProperties.getEvent();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(eventProps.getCorePoolSize());
        executor.setMaxPoolSize(eventProps.getMaxPoolSize());
        executor.setQueueCapacity(eventProps.getQueueCapacity());
        executor.setThreadNamePrefix(eventProps.getThreadNamePrefix());
        executor.setKeepAliveSeconds(eventProps.getKeepAliveSeconds());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        // Propagate MDC to worker threads (same decorator as sttExecutor)
        executor.setTaskDecorator(runnable -> {
            Map<String, String> contextMap = ThreadContext.getImmutableContext();
            return () -> {
                Map<String, String> previous = ThreadContext.getImmutableContext();
                try {
                    if (contextMap != null && !contextMap.isEmpty()) {
                        ThreadContext.putAll(contextMap);
                    }
                    runnable.run();
                } finally {
                    ThreadContext.clearAll();
                    if (previous != null && !previous.isEmpty()) {
                        ThreadContext.putAll(previous);
                    }
                }
            };
        });

        executor.initialize();
        return executor;
    }
}
