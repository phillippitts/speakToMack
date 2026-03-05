package com.phillippitts.speaktomack.service.orchestration;

import com.phillippitts.speaktomack.service.orchestration.event.ApplicationStateChangedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Thread-safe tracker for the application's high-level state.
 * Publishes {@link ApplicationStateChangedEvent} on every transition.
 *
 * @since 1.2
 */
@Service
public class ApplicationStateTracker {

    private static final Logger LOG = LogManager.getLogger(ApplicationStateTracker.class);

    private final ApplicationEventPublisher publisher;
    private volatile ApplicationState state = ApplicationState.IDLE;

    public ApplicationStateTracker(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Transitions to a new state and publishes an event.
     * If the new state equals the current state, this is a no-op.
     *
     * @param newState the state to transition to
     */
    public synchronized void transitionTo(ApplicationState newState) {
        ApplicationState previous = this.state;
        if (previous == newState) {
            return;
        }
        this.state = newState;
        LOG.info("State transition: {} → {}", previous, newState);
        publisher.publishEvent(new ApplicationStateChangedEvent(previous, newState, Instant.now()));
    }

    public ApplicationState getState() {
        return state;
    }
}
