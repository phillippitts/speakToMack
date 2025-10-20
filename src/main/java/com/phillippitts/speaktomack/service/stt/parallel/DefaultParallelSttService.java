package com.phillippitts.speaktomack.service.stt.parallel;

import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import com.phillippitts.speaktomack.service.stt.EngineResult;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import com.phillippitts.speaktomack.service.stt.TokenizerUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.phillippitts.speaktomack.service.stt.parallel.ParallelSttService.EnginePair;

/**
 * Default implementation of parallel dual-engine transcription service.
 *
 * <p>This service runs Vosk and Whisper STT engines concurrently using a dedicated executor,
 * enabling Phase 4 reconciliation strategies. Key features:
 * <ul>
 *   <li><b>Parallel Execution:</b> Both engines run simultaneously on separate threads</li>
 *   <li><b>Timeout Protection:</b> Configurable timeout prevents indefinite blocking</li>
 *   <li><b>Graceful Degradation:</b> Returns partial results if one engine fails</li>
 *   <li><b>Token Extraction:</b> Extracts word-level tokens for overlap-based reconciliation</li>
 *   <li><b>JSON Support:</b> Whisper JSON output provides enhanced token accuracy</li>
 * </ul>
 *
 * <p><b>Thread Model:</b> Uses {@code sttExecutor} (bounded thread pool) to prevent resource
 * exhaustion. Each {@link #transcribeBoth(byte[], long)} call spawns two tasks that complete
 * independently. The method blocks until both finish or timeout occurs.
 *
 * <p><b>Error Handling:</b> Individual engine failures are caught and logged. If both engines
 * fail or timeout, a {@link TranscriptionException} is thrown. If only one engine fails, the
 * successful result is still returned for reconciliation (may trigger fallback in reconciler).
 *
 * <p><b>Performance:</b> Parallel execution typically reduces total latency by 40-60% compared
 * to sequential execution. Actual speedup depends on CPU cores and engine implementations.
 *
 * @see ParallelSttService
 * @see com.phillippitts.speaktomack.service.reconcile.TranscriptReconciler
 * @since 1.0 (Phase 4)
 */
@Service
public class DefaultParallelSttService implements ParallelSttService {
    private static final Logger LOG = LogManager.getLogger(DefaultParallelSttService.class);

    private final SttEngine vosk;
    private final SttEngine whisper;
    private final Executor executor;

    // Re-use orchestration timeout for now (could introduce dedicated property later)
    private final long defaultTimeoutMs;

    /**
     * Constructs a DefaultParallelSttService with dependency injection.
     *
     * <p>This constructor is wired by Spring using {@code @Qualifier} annotations to
     * ensure the correct engine and executor beans are injected.
     *
     * @param vosk Vosk STT engine (qualified as "voskSttEngine")
     * @param whisper Whisper STT engine (qualified as "whisperSttEngine")
     * @param executor bounded thread pool for parallel execution (qualified as "sttExecutor")
     * @param timeoutMs default timeout in milliseconds from {@code stt.parallel.timeout-ms} property
     *                  (defaults to 10000ms if not configured or invalid)
     * @throws NullPointerException if vosk, whisper, or executor is null
     * @see com.phillippitts.speaktomack.config.stt.SttConfig
     */
    public DefaultParallelSttService(@Qualifier("voskSttEngine") SttEngine vosk,
                                     @Qualifier("whisperSttEngine") SttEngine whisper,
                                     @Qualifier("sttExecutor") Executor executor,
                                     @Value("${stt.parallel.timeout-ms:10000}")
                                     long timeoutMs) {
        this.vosk = Objects.requireNonNull(vosk);
        this.whisper = Objects.requireNonNull(whisper);
        this.executor = Objects.requireNonNull(executor);
        this.defaultTimeoutMs = timeoutMs <= 0 ? 10_000 : timeoutMs;
    }

    /**
     * Transcribes audio using both Vosk and Whisper engines in parallel.
     *
     * <p>This method spawns two asynchronous tasks (one per engine) and waits for both to
     * complete or for the timeout to expire. Results are packaged into an {@link EnginePair}
     * for downstream reconciliation.
     *
     * <p><b>Timeout Behavior:</b>
     * <ul>
     *   <li>If {@code timeoutMs > 0}, uses the specified timeout</li>
     *   <li>If {@code timeoutMs <= 0}, uses the default timeout from configuration</li>
     *   <li>If timeout expires, cancels remaining tasks and returns partial results (if any)</li>
     * </ul>
     *
     * <p><b>Partial Results:</b> If one engine succeeds and the other fails or times out,
     * the pair will contain one non-null result. Reconcilers must handle this gracefully
     * (typically by using the available result or throwing an exception).
     *
     * <p><b>Token Extraction:</b> For Whisper, this method attempts to extract JSON-derived
     * tokens via {@link com.phillippitts.speaktomack.service.stt.whisper.WhisperSttEngine#consumeLastTokens()}.
     * This provides more accurate word boundaries for overlap-based reconciliation.
     *
     * @param pcm PCM audio data (16-bit, 16kHz, mono)
     * @param timeoutMs timeout in milliseconds (use 0 for default)
     * @return pair of engine results (either may be null if that engine failed)
     * @throws NullPointerException if pcm is null
     * @throws TranscriptionException if both engines fail or timeout
     * @see EnginePair
     * @see com.phillippitts.speaktomack.service.reconcile.TranscriptReconciler
     */
    @Override
    public EnginePair transcribeBoth(byte[] pcm, long timeoutMs) {
        Objects.requireNonNull(pcm, "pcm");
        final long toMs = timeoutMs > 0 ? timeoutMs : defaultTimeoutMs;

        List<CompletableFuture<EngineResult>> futures = new ArrayList<>(2);
        futures.add(CompletableFuture.supplyAsync(() -> runEngine(vosk, pcm), executor));
        futures.add(CompletableFuture.supplyAsync(() -> runEngine(whisper, pcm), executor));

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(toMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            LOG.warn("Parallel transcription timed out after {} ms", toMs);
            // Cancel remaining tasks best-effort
            futures.forEach(f -> f.cancel(true));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException ee) {
            // Already handled per-engine; continue to collect results
        }

        EngineResult rVosk = getResultSilently(futures.get(0));
        EngineResult rWhisper = getResultSilently(futures.get(1));

        if (rVosk == null && rWhisper == null) {
            throw new TranscriptionException("Both engines failed or timed out");
        }
        return new EnginePair(rVosk, rWhisper);
    }

    private EngineResult getResultSilently(CompletableFuture<EngineResult> f) {
        try {
            return f.isDone() && !f.isCompletedExceptionally() && !f.isCancelled() ? f.get() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private EngineResult runEngine(SttEngine engine, byte[] pcm) {
        long t0 = System.nanoTime();
        try {
            TranscriptionResult tr = engine.transcribe(pcm);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            List<String> tokens = TokenizerUtil.tokenize(tr.text());
            String rawJson = null;
            // If this is the Whisper engine in JSON mode, prefer JSON-derived tokens/raw
            if (engine instanceof com.phillippitts.speaktomack.service.stt.whisper.WhisperSttEngine w) {
                List<String> jt = w.consumeLastTokens();
                if (!jt.isEmpty()) {
                    tokens = jt;
                }
                rawJson = w.consumeLastRawJson();
            }
            return new EngineResult(tr.text(), tr.confidence(), tokens, ms, engine.getEngineName(), rawJson);
        } catch (TranscriptionException te) {
            LOG.warn("{} failed: {}", engine.getEngineName(), te.getMessage());
            return null;
        } catch (RuntimeException re) {
            LOG.error("{} unexpected error", engine.getEngineName(), re);
            return null;
        }
    }
}
