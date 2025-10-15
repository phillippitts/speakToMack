package com.phillippitts.speaktomack.config;

import org.apache.logging.log4j.ThreadContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration for thread pools used in asynchronous processing.
 * Provides optimized executor for parallel STT engine transcription.
 */
@Configuration
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
        int processors = Runtime.getRuntime().availableProcessors();
        int corePoolSize = processors;
        int maxPoolSize = processors * 2;

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
                };
            };
        });

        executor.initialize();
        return executor;
    }
}
