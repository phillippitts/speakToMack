package com.phillippitts.speaktomack.service.orchestration.event;

import com.phillippitts.speaktomack.service.orchestration.ApplicationState;

import java.time.Instant;

/**
 * Published when the application state changes (IDLE, RECORDING, TRANSCRIBING).
 *
 * @param previous the previous state
 * @param current the new state
 * @param timestamp when the transition occurred
 * @since 1.2
 */
public record ApplicationStateChangedEvent(
        ApplicationState previous,
        ApplicationState current,
        Instant timestamp
) {}
