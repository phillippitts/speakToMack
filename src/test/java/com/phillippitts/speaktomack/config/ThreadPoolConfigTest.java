package com.phillippitts.speaktomack.config;

import com.phillippitts.speaktomack.config.properties.ThreadPoolProperties;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadPoolConfigTest {

    @Test
    void shouldCreateExecutorWithCorrectConfiguration() {
        ThreadPoolProperties properties = new ThreadPoolProperties();
        ThreadPoolConfig config = new ThreadPoolConfig(properties);
        Executor executor = config.sttExecutor();

        assertThat(executor).isNotNull();
        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);

        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;

        // Check against default property values
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(4);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(8);
        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("stt-pool-");
    }

    @Test
    void shouldHandleConcurrentTasks() throws InterruptedException {
        ThreadPoolProperties properties = new ThreadPoolProperties();
        ThreadPoolConfig config = new ThreadPoolConfig(properties);
        Executor executor = config.sttExecutor();

        int taskCount = 10;
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger completedTasks = new AtomicInteger(0);

        for (int i = 0; i < taskCount; i++) {
            executor.execute(() -> {
                try {
                    Thread.sleep(10); // Simulate work
                    completedTasks.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(5, TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(completedTasks.get()).isEqualTo(taskCount);
    }

    @Test
    void shouldNotExhaustThreadsUnderLoad() throws InterruptedException {
        ThreadPoolProperties properties = new ThreadPoolProperties();
        ThreadPoolConfig config = new ThreadPoolConfig(properties);
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.sttExecutor();

        int taskCount = 20;
        CountDownLatch latch = new CountDownLatch(taskCount);

        for (int i = 0; i < taskCount; i++) {
            executor.execute(() -> {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(10, TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(executor.getActiveCount()).isLessThanOrEqualTo(executor.getMaxPoolSize());
    }

    @Test
    void shouldUseCorrectThreadNamePrefix() throws InterruptedException {
        ThreadPoolProperties properties = new ThreadPoolProperties();
        ThreadPoolConfig config = new ThreadPoolConfig(properties);
        Executor executor = config.sttExecutor();

        CountDownLatch latch = new CountDownLatch(1);
        String[] threadName = new String[1];

        executor.execute(() -> {
            threadName[0] = Thread.currentThread().getName();
            latch.countDown();
        });

        latch.await(1, TimeUnit.SECONDS);

        assertThat(threadName[0]).startsWith("stt-pool-");
    }

    @Test
    void shouldShutdownGracefully() {
        ThreadPoolProperties properties = new ThreadPoolProperties();
        ThreadPoolConfig config = new ThreadPoolConfig(properties);
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.sttExecutor();

        executor.execute(() -> {
            // Simple task
        });

        executor.shutdown();
        assertThat(executor.getThreadPoolExecutor().isShutdown()).isTrue();
    }
}
