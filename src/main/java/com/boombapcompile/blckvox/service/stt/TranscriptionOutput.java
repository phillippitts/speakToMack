package com.boombapcompile.blckvox.service.stt;

import com.boombapcompile.blckvox.domain.TranscriptionResult;

import java.util.List;
import java.util.Objects;

/**
 * Immutable output from a detailed transcription, wrapping the result
 * with optional word-level tokens and raw JSON output.
 *
 * <p>Returned directly from {@code transcribeDetailed()} as a single immutable value.
 *
 * @param result  the transcription result (never null)
 * @param tokens  word-level tokens from the engine, or empty list if unavailable
 * @param rawJson raw JSON output from the engine, or null if unavailable
 * @since 1.4
 */
public record TranscriptionOutput(
        TranscriptionResult result,
        List<String> tokens,
        String rawJson
) {
    public TranscriptionOutput {
        Objects.requireNonNull(result, "result must not be null");
        tokens = tokens != null ? List.copyOf(tokens) : List.of();
    }

    /** Creates output with only a result (no tokens or JSON). */
    public static TranscriptionOutput of(TranscriptionResult result) {
        return new TranscriptionOutput(result, List.of(), null);
    }

    /** Creates output with result, tokens, and optional raw JSON. */
    public static TranscriptionOutput of(TranscriptionResult result, List<String> tokens, String rawJson) {
        return new TranscriptionOutput(result, tokens, rawJson);
    }
}
