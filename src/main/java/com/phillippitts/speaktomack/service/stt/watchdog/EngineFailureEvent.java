package com.phillippitts.speaktomack.service.stt.watchdog;

import java.time.Instant;
import java.util.Map;

/**
 * Published when an STT engine experiences a failure (e.g., timeout, non-zero exit, JNI error).
 *
 * <p>PII note: Do not include transcript text in context. Restrict to technical diagnostics.
 */
public record EngineFailureEvent(
        String engine,
        Instant at,
        String message,
        Throwable cause,
        Map<String, String> context
) {
    public EngineFailureEvent {
        if (at == null) {
            at = Instant.now();
        }
    }
}
