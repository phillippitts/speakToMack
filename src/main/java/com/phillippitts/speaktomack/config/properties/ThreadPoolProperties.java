package com.phillippitts.speaktomack.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for thread pools.
 *
 * <p>Provides tuneable thread pool sizing for STT executor and event executor.
 * Defaults are conservative but can be adjusted based on hardware and workload.
 */
@Component
@ConfigurationProperties(prefix = "threadpool")
public class ThreadPoolProperties {

    private SttPoolProperties stt = new SttPoolProperties();
    private EventPoolProperties event = new EventPoolProperties();

    public SttPoolProperties getStt() {
        return stt;
    }

    public void setStt(SttPoolProperties stt) {
        this.stt = stt;
    }

    public EventPoolProperties getEvent() {
        return event;
    }

    public void setEvent(EventPoolProperties event) {
        this.event = event;
    }

    /**
     * STT executor pool configuration.
     */
    public static class SttPoolProperties {
        private int corePoolSize = 4;
        private int maxPoolSize = 8;
        private int queueCapacity = 50;
        private int keepAliveSeconds = 60;
        private String threadNamePrefix = "stt-pool-";

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public int getKeepAliveSeconds() {
            return keepAliveSeconds;
        }

        public void setKeepAliveSeconds(int keepAliveSeconds) {
            this.keepAliveSeconds = keepAliveSeconds;
        }

        public String getThreadNamePrefix() {
            return threadNamePrefix;
        }

        public void setThreadNamePrefix(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
        }
    }

    /**
     * Event executor pool configuration.
     */
    public static class EventPoolProperties {
        private int corePoolSize = 2;
        private int maxPoolSize = 4;
        private int queueCapacity = 10;
        private int keepAliveSeconds = 60;
        private String threadNamePrefix = "event-pool-";

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public int getKeepAliveSeconds() {
            return keepAliveSeconds;
        }

        public void setKeepAliveSeconds(int keepAliveSeconds) {
            this.keepAliveSeconds = keepAliveSeconds;
        }

        public String getThreadNamePrefix() {
            return threadNamePrefix;
        }

        public void setThreadNamePrefix(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
        }
    }
}
