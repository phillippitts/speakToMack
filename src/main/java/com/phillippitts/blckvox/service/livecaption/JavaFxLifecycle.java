package com.phillippitts.blckvox.service.livecaption;

import javafx.application.Platform;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the JavaFX toolkit lifecycle within Spring Boot.
 *
 * <p>Starts the JavaFX platform on Spring start and shuts it down on stop.
 * Runs at a phase just before {@code SystemTrayManager} so JavaFX is ready
 * before the tray menu references it.
 *
 * @since 1.3
 */
@Service
@ConditionalOnProperty(name = "live-caption.enabled", havingValue = "true")
public class JavaFxLifecycle implements SmartLifecycle {

    private static final Logger LOG = LogManager.getLogger(JavaFxLifecycle.class);

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public void start() {
        try {
            Platform.startup(() -> Platform.setImplicitExit(false));
            LOG.info("JavaFX platform started");
        } catch (IllegalStateException e) {
            // Platform already initialized (e.g., in tests)
            Platform.setImplicitExit(false);
            LOG.debug("JavaFX platform already initialized");
        }
        running.set(true);
    }

    @Override
    public void stop() {
        running.set(false);
        Platform.exit();
        LOG.info("JavaFX platform stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 1;
    }
}
