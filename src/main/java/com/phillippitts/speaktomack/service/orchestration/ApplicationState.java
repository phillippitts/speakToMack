package com.phillippitts.speaktomack.service.orchestration;

/**
 * Represents the high-level state of the speech-to-text application.
 *
 * @since 1.2
 */
public enum ApplicationState {
    IDLE,
    RECORDING,
    TRANSCRIBING
}
