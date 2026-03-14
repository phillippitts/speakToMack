package com.boombapcompile.blckvox.service.orchestration;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CaptureStateMachine}.
 */
class CaptureStateMachineTest {

    @Test
    void startCaptureSucceedsWhenIdle() {
        CaptureStateMachine sm = new CaptureStateMachine();
        UUID id = UUID.randomUUID();

        boolean result = sm.startCapture(id);

        assertThat(result).isTrue();
        assertThat(sm.isActive()).isTrue();
    }

    @Test
    void startCaptureFailsWhenAlreadyActive() {
        CaptureStateMachine sm = new CaptureStateMachine();
        sm.startCapture(UUID.randomUUID());

        boolean result = sm.startCapture(UUID.randomUUID());

        assertThat(result).isFalse();
    }

    @Test
    void stopCaptureSucceedsWithCorrectId() {
        CaptureStateMachine sm = new CaptureStateMachine();
        UUID id = UUID.randomUUID();
        sm.startCapture(id);

        boolean result = sm.stopCapture(id);

        assertThat(result).isTrue();
        assertThat(sm.isActive()).isFalse();
    }

    @Test
    void stopCaptureFailsWithWrongId() {
        CaptureStateMachine sm = new CaptureStateMachine();
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        sm.startCapture(id1);

        boolean result = sm.stopCapture(id2);

        assertThat(result).isFalse();
        assertThat(sm.isActive()).isTrue();
    }

    @Test
    void stopCaptureFailsWhenNoActiveSession() {
        CaptureStateMachine sm = new CaptureStateMachine();

        boolean result = sm.stopCapture(UUID.randomUUID());

        assertThat(result).isFalse();
    }

    @Test
    void cancelCaptureReturnsSessionId() {
        CaptureStateMachine sm = new CaptureStateMachine();
        UUID id = UUID.randomUUID();
        sm.startCapture(id);

        UUID cancelled = sm.cancelCapture();

        assertThat(cancelled).isEqualTo(id);
        assertThat(sm.isActive()).isFalse();
    }

    @Test
    void cancelCaptureReturnsNullWhenIdle() {
        CaptureStateMachine sm = new CaptureStateMachine();

        UUID cancelled = sm.cancelCapture();

        assertThat(cancelled).isNull();
    }

    @Test
    void getActiveSessionReturnsNullWhenIdle() {
        CaptureStateMachine sm = new CaptureStateMachine();

        assertThat(sm.getActiveSession()).isNull();
    }

    @Test
    void getActiveSessionReturnsIdWhenActive() {
        CaptureStateMachine sm = new CaptureStateMachine();
        UUID id = UUID.randomUUID();
        sm.startCapture(id);

        assertThat(sm.getActiveSession()).isEqualTo(id);
    }

    @Test
    void startCaptureRejectsNullSessionId() {
        CaptureStateMachine sm = new CaptureStateMachine();

        assertThatThrownBy(() -> sm.startCapture(null))
                .isInstanceOf(NullPointerException.class);
    }
}
