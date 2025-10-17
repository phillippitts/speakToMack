package com.phillippitts.speaktomack.service.stt.watchdog;

import java.time.Instant;

/**
 * Published when an STT engine has been successfully restarted after failures.
 */
public record EngineRecoveredEvent(
        String engine,
        Instant at
) {
    public EngineRecoveredEvent {
        if (at == null) at = Instant.now();
    }
}
