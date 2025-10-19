package com.phillippitts.speaktomack.service.stt;

import java.util.List;
import java.util.Objects;

/**
 * Result of a single STT engine run, designed for reconciliation.
 */
public record EngineResult(
        String text,
        double confidence,
        List<String> tokens,
        long durationMs,
        String engine,
        String rawJson
) {
    public EngineResult {
        Objects.requireNonNull(text, "text");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        tokens = tokens == null ? List.of() : List.copyOf(tokens);
        Objects.requireNonNull(engine, "engine");
    }
}