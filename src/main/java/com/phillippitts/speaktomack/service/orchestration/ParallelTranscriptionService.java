package com.phillippitts.speaktomack.service.orchestration;

import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import com.phillippitts.speaktomack.service.validation.AudioValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Service for parallel transcription with multiple STT engines.
 *
 * <p>Validates PCM once, then dispatches the same buffer to both engines concurrently
 * using the provided Executor. Returns results from both engines for comparison.
 *
 * <p>Thread-safe: Uses immutable state and CompletableFuture for parallel execution.
 *
 * <p>Error handling: Translates timeout and completion exceptions to TranscriptionException
 * with meaningful messages for debugging.
 *
 * <p>Note: Not currently managed by Spring. Instantiate manually with required dependencies.
 * Spring integration can be added when needed via {@code @Bean} configuration.
 */
public class ParallelTranscriptionService {

    private static final Logger LOG = LogManager.getLogger(ParallelTranscriptionService.class);

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
     * @throws TranscriptionException if timeout occurs or either engine fails
     */
    public List<TranscriptionResult> transcribeBoth(byte[] pcm, Duration timeout) {
        validator.validate(pcm);

        long startMs = System.currentTimeMillis();
        LOG.info("Starting parallel transcription: timeout={}ms, audioSize={} bytes",
                timeout.toMillis(), pcm.length);

        CompletableFuture<TranscriptionResult> fVosk =
                CompletableFuture.supplyAsync(() -> {
                    LOG.debug("Vosk transcription started");
                    TranscriptionResult result = vosk.transcribe(pcm);
                    LOG.debug("Vosk transcription completed: text={} chars", result.text().length());
                    return result;
                }, sttExecutor);

        CompletableFuture<TranscriptionResult> fWhisper =
                CompletableFuture.supplyAsync(() -> {
                    LOG.debug("Whisper transcription started");
                    TranscriptionResult result = whisper.transcribe(pcm);
                    LOG.debug("Whisper transcription completed: text={} chars", result.text().length());
                    return result;
                }, sttExecutor);

        try {
            CompletableFuture.allOf(fVosk, fWhisper)
                    .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .join();

            long durationMs = System.currentTimeMillis() - startMs;
            LOG.info("Parallel transcription completed: duration={}ms", durationMs);

            return List.of(fVosk.join(), fWhisper.join());

        } catch (CompletionException e) {
            long durationMs = System.currentTimeMillis() - startMs;

            if (e.getCause() instanceof TimeoutException) {
                LOG.warn("Parallel transcription timed out after {}ms (limit: {}ms)",
                        durationMs, timeout.toMillis());
                throw new TranscriptionException(
                        "Parallel transcription timed out after " + durationMs + "ms",
                        "parallel", e);
            }

            LOG.error("Parallel transcription failed after {}ms: {}", durationMs, e.getMessage());
            throw new TranscriptionException(
                    "Parallel transcription failed: " + e.getMessage(),
                    "parallel", e);
        }
    }
}
