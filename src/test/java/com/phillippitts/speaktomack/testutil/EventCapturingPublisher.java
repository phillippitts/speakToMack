package com.phillippitts.speaktomack.testutil;

import com.phillippitts.speaktomack.service.orchestration.event.TranscriptionCompletedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test double for ApplicationEventPublisher that captures events for verification.
 *
 * <p>Thread-safe implementation using CopyOnWriteArrayList for concurrent test scenarios.
 */
public class EventCapturingPublisher implements ApplicationEventPublisher {
    final List<Object> events = new CopyOnWriteArrayList<>();

    @Override
    public void publishEvent(ApplicationEvent event) {
        events.add(event);
    }

    @Override
    public void publishEvent(Object event) {
        events.add(event);
    }

    /**
     * Finds the first TranscriptionCompletedEvent in captured events.
     *
     * @return first transcription event, or null if none found
     */
    public TranscriptionCompletedEvent findTranscriptionEvent() {
        return events.stream()
                .filter(e -> e instanceof TranscriptionCompletedEvent)
                .map(e -> (TranscriptionCompletedEvent) e)
                .findFirst()
                .orElse(null);
    }

    /**
     * Clears all captured events (useful for multi-iteration tests).
     */
    public void clear() {
        events.clear();
    }
}
