package com.phillippitts.speaktomack.config;

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
 */
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    private static final int QUEUE_CAPACITY = 50;
    private static final int KEEP_ALIVE_SECONDS = 60;
    private static final String THREAD_NAME_PREFIX = "stt-pool-";

    /**
     * Creates an optimized thread pool for parallel STT engine processing.
     *
     * <p>Pool sizing strategy:
     * <ul>
     *   <li>Core pool: {@code availableProcessors()} - handles typical load</li>
     *   <li>Max pool: {@code availableProcessors() * 2} - handles burst traffic</li>
     *   <li>Queue: 50 tasks - prevents unbounded memory growth</li>
     * </ul>
     *
     * <p>Rejection policy: {@link ThreadPoolExecutor.CallerRunsPolicy}
     * When the pool and queue are full, the caller thread executes the task,
     * providing backpressure instead of failing fast.
     *
     * <p>Thread naming: {@code stt-pool-N} for easy identification in logs and profilers.
     *
     * <p>MDC propagation: Copies Log4j2 ThreadContext (MDC) from the submitting thread to
     * the worker thread to preserve request/user correlation IDs in async logs.
     *
     * @return Configured executor for STT engine processing
     */
    @Bean(name = "sttExecutor")
    public Executor sttExecutor() {
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        int maxPoolSize = corePoolSize * 2;

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
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
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("event-pool-");
        executor.setKeepAliveSeconds(KEEP_ALIVE_SECONDS);
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
