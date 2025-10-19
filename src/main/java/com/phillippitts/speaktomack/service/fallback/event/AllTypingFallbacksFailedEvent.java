package com.phillippitts.speaktomack.service.fallback.event;

import java.time.Instant;

/** Published when no typing adapters succeed. */
public record AllTypingFallbacksFailedEvent(String reason, Instant at) { }
