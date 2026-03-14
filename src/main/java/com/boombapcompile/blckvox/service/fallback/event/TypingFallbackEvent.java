package com.boombapcompile.blckvox.service.fallback.event;

import java.time.Instant;

/** Published when a typing adapter fails and a fallback tier is attempted. */
public record TypingFallbackEvent(String tier, String reason, Instant at) { }
