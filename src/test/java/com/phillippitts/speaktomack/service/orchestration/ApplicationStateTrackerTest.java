package com.phillippitts.speaktomack.service.orchestration;

import com.phillippitts.speaktomack.service.orchestration.event.ApplicationStateChangedEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationStateTrackerTest {

    @Test
    void startsInIdleState() {
        ApplicationStateTracker tracker = new ApplicationStateTracker(e -> { });
        assertThat(tracker.getState()).isEqualTo(ApplicationState.IDLE);
    }

    @Test
    void transitionsAndPublishesEvent() {
        List<Object> events = new ArrayList<>();
        ApplicationStateTracker tracker = new ApplicationStateTracker(events::add);

        tracker.transitionTo(ApplicationState.RECORDING);

        assertThat(tracker.getState()).isEqualTo(ApplicationState.RECORDING);
        assertThat(events).hasSize(1);
        ApplicationStateChangedEvent evt = (ApplicationStateChangedEvent) events.getFirst();
        assertThat(evt.previous()).isEqualTo(ApplicationState.IDLE);
        assertThat(evt.current()).isEqualTo(ApplicationState.RECORDING);
        assertThat(evt.timestamp()).isNotNull();
    }

    @Test
    void noOpWhenTransitioningToSameState() {
        List<Object> events = new ArrayList<>();
        ApplicationStateTracker tracker = new ApplicationStateTracker(events::add);

        tracker.transitionTo(ApplicationState.IDLE);

        assertThat(events).isEmpty();
    }

    @Test
    void fullLifecycle() {
        List<ApplicationStateChangedEvent> events = new ArrayList<>();
        ApplicationStateTracker tracker = new ApplicationStateTracker(
                e -> events.add((ApplicationStateChangedEvent) e));

        tracker.transitionTo(ApplicationState.RECORDING);
        tracker.transitionTo(ApplicationState.TRANSCRIBING);
        tracker.transitionTo(ApplicationState.IDLE);

        assertThat(events).hasSize(3);
        assertThat(events.get(0).current()).isEqualTo(ApplicationState.RECORDING);
        assertThat(events.get(1).current()).isEqualTo(ApplicationState.TRANSCRIBING);
        assertThat(events.get(2).current()).isEqualTo(ApplicationState.IDLE);
    }
}
