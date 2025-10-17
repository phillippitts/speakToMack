package com.phillippitts.speaktomack.service.orchestration;

import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import com.phillippitts.speaktomack.service.validation.AudioValidator;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Minimal parallel transcription facade used by tests (Task 2.6 prep).
 *
 * Validates PCM once, then dispatches the same buffer to both engines concurrently
 * using the provided Executor. Reconciliation is deliberately out of scope for
 * Phase 2; callers can choose which result to prefer for assertions.
 */
public class ParallelTranscriptionService {

    private final AudioValidator validator;
    private final SttEngine vosk;
    private final SttEngine whisper;
    private final Executor sttExecutor;

    /**
     * Configuration for parallel transcription (parameter object pattern).
     */
    public record EngineConfig(SttEngine vosk, SttEngine whisper, Executor executor) {
        public EngineConfig {
            Objects.requireNonNull(vosk, "vosk");
            Objects.requireNonNull(whisper, "whisper");
            Objects.requireNonNull(executor, "executor");
        }
    }

    public ParallelTranscriptionService(AudioValidator validator, EngineConfig config) {
        this.validator = Objects.requireNonNull(validator, "validator");
        this.vosk = config.vosk();
        this.whisper = config.whisper();
        this.sttExecutor = config.executor();
    }

    /**
     * Transcribes with both engines in parallel and returns both results.
     *
     * @param pcm raw PCM 16kHz, 16-bit, mono (validated here)
     * @param timeout overall timeout for both completions
     * @return list of two results in the order [vosk, whisper]
     */
    public List<TranscriptionResult> transcribeBoth(byte[] pcm, Duration timeout) {
        validator.validate(pcm);
        CompletableFuture<TranscriptionResult> fVosk =
                CompletableFuture.supplyAsync(() -> vosk.transcribe(pcm), sttExecutor);
        CompletableFuture<TranscriptionResult> fWhisper =
                CompletableFuture.supplyAsync(() -> whisper.transcribe(pcm), sttExecutor);

        CompletableFuture.allOf(fVosk, fWhisper)
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .join();

        return List.of(fVosk.join(), fWhisper.join());
    }
}
