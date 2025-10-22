package com.phillippitts.speaktomack.service.orchestration.event;

import com.phillippitts.speaktomack.domain.TranscriptionResult;

import java.time.Instant;

/**
 * Emitted when a transcription completes and is ready for downstream consumers
 * (e.g., FallbackManager / TypingService).
 *
 * @param result the transcription result with text and confidence
 * @param timestamp when the transcription completed
 * @param engineUsed name of the STT engine that produced this result (vosk/whisper/reconciled)
 */
public record TranscriptionCompletedEvent(
        TranscriptionResult result,
        Instant timestamp,
        String engineUsed
) {}
