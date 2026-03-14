package com.boombapcompile.blckvox.service.stt.vosk;

import com.boombapcompile.blckvox.config.stt.VoskConfig;
import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.vosk.Model;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Shared provider for the Vosk JNI {@link Model} instance.
 *
 * <p>Lazily loads a single model on first access and shares it across
 * {@link VoskSttEngine} and {@link VoskStreamingService}, avoiding
 * duplicate model loading (which can consume 50MB–1.8GB each).
 *
 * <p>Thread-safe: uses a lock to guard lazy initialization and close.
 */
@Component
public class VoskModelProvider {

    private static final Logger LOG = LogManager.getLogger(VoskModelProvider.class);

    private final VoskConfig config;
    private final ReentrantLock lock = new ReentrantLock();
    private Model model;
    private boolean closed;

    public VoskModelProvider(VoskConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Returns the shared Vosk model, loading it on first access.
     *
     * @return the shared Model instance
     * @throws RuntimeException if the model cannot be loaded
     */
    public Model getModel() {
        lock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("VoskModelProvider has been closed");
            }
            if (model == null) {
                LOG.info("Loading shared Vosk model: {}", config.modelPath());
                try {
                    model = new Model(config.modelPath());
                } catch (IOException e) {
                    throw new RuntimeException("Failed to load Vosk model: " + config.modelPath(), e);
                }
                LOG.info("Shared Vosk model loaded");
            }
            return model;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Closes the shared model and releases JNI resources.
     */
    @PreDestroy
    public void close() {
        lock.lock();
        try {
            closed = true;
            if (model != null) {
                try {
                    model.close();
                } catch (Exception e) {
                    LOG.warn("Error closing shared Vosk model", e);
                }
                model = null;
                LOG.info("Shared Vosk model closed");
            }
        } finally {
            lock.unlock();
        }
    }
}