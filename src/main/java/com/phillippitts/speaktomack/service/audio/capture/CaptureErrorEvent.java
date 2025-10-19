package com.phillippitts.speaktomack.service.audio.capture;

import java.time.Instant;

/**
 * Published when microphone capture fails (permissions, device errors, etc.).
 *
 * Payload contains a short reason and timestamp. Avoids any PII.
 */
public record CaptureErrorEvent(String reason, Instant at) { }
