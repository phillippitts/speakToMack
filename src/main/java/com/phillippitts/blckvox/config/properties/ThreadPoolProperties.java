package com.phillippitts.blckvox.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for thread pools.
 *
 * <p>Provides tuneable thread pool sizing for STT executor and event executor.
 * Defaults are conservative but can be adjusted based on hardware and workload.
 */
@ConfigurationProperties(prefix = "threadpool")
public record ThreadPoolProperties(
        @DefaultValue
        SttPoolProperties stt,

        @DefaultValue
        EventPoolProperties event
) {

    public SttPoolProperties getStt() {
        return stt;
    }

    public EventPoolProperties getEvent() {
        return event;
    }

    /**
     * STT executor pool configuration.
     */
    public record SttPoolProperties(
            @DefaultValue("4")
            int corePoolSize,

            @DefaultValue("8")
            int maxPoolSize,

            @DefaultValue("50")
            int queueCapacity,

            @DefaultValue("60")
            int keepAliveSeconds,

            @DefaultValue("stt-pool-")
            String threadNamePrefix
    ) {

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public int getKeepAliveSeconds() {
            return keepAliveSeconds;
        }

        public String getThreadNamePrefix() {
            return threadNamePrefix;
        }
    }

    /**
     * Event executor pool configuration.
     */
    public record EventPoolProperties(
            @DefaultValue("2")
            int corePoolSize,

            @DefaultValue("4")
            int maxPoolSize,

            @DefaultValue("10")
            int queueCapacity,

            @DefaultValue("60")
            int keepAliveSeconds,

            @DefaultValue("event-pool-")
            String threadNamePrefix
    ) {

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public int getKeepAliveSeconds() {
            return keepAliveSeconds;
        }

        public String getThreadNamePrefix() {
            return threadNamePrefix;
        }
    }
}
