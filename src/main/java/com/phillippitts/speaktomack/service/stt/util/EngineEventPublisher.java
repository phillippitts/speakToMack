package com.phillippitts.speaktomack.service.stt.util;

import com.phillippitts.speaktomack.service.stt.watchdog.EngineFailureEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Map;

/**
 * Utility class for publishing STT engine failure events.
 *
 * <p>Centralizes the common pattern of null-checking the publisher and creating
 * EngineFailureEvent instances. This reduces code duplication across STT engines
 * and related components.
 *
 * @since 1.0
 */
public final class EngineEventPublisher {

    private EngineEventPublisher() {
        // Utility class - prevent instantiation
    }

    /**
     * Publishes an engine failure event if a publisher is available.
     *
     * <p>If the publisher is null, this method does nothing (fails silently).
     * This allows engines to work without event publishing in test scenarios.
     *
     * @param publisher the Spring event publisher (may be null)
     * @param engineName the name of the engine experiencing the failure
     * @param message a human-readable description of the failure
     * @param cause the exception that caused the failure (may be null)
     * @param context additional context as key-value pairs (may be null or empty)
     */
    public static void publishFailure(ApplicationEventPublisher publisher,
                                       String engineName,
                                       String message,
                                       Throwable cause,
                                       Map<String, String> context) {
        if (publisher != null) {
            publisher.publishEvent(new EngineFailureEvent(
                engineName,
                Instant.now(),
                message,
                cause,
                context
            ));
        }
    }

    /**
     * Publishes an engine failure event with no additional context.
     *
     * @param publisher the Spring event publisher (may be null)
     * @param engineName the name of the engine experiencing the failure
     * @param message a human-readable description of the failure
     * @param cause the exception that caused the failure (may be null)
     */
    public static void publishFailure(ApplicationEventPublisher publisher,
                                       String engineName,
                                       String message,
                                       Throwable cause) {
        publishFailure(publisher, engineName, message, cause, null);
    }
}
