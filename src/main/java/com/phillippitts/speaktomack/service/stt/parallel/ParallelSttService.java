package com.phillippitts.speaktomack.service.stt.parallel;

import com.phillippitts.speaktomack.exception.TranscriptionException;
import com.phillippitts.speaktomack.service.stt.EngineResult;

/**
 * Runs Vosk and Whisper in parallel and returns both results for reconciliation.
 * Implementations must be hermetic-test friendly.
 */
public interface ParallelSttService {

    /**
     * Holds both engine results (either may be null if a failure occurred).
     */
    record EnginePair(EngineResult vosk, EngineResult whisper) {}

    /**
     * Transcribes with both engines, respecting parallel timeout.
     * @throws TranscriptionException when both engines fail or timeout occurs without any result
     */
    EnginePair transcribeBoth(byte[] pcm, long timeoutMs);
}
