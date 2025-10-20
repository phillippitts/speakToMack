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
 * Default implementation running Vosk and Whisper in parallel using the sttExecutor.
 */
@Service
public class DefaultParallelSttService implements ParallelSttService {
    private static final Logger LOG = LogManager.getLogger(DefaultParallelSttService.class);

    private final SttEngine vosk;
    private final SttEngine whisper;
    private final Executor executor;

    // Re-use orchestration timeout for now (could introduce dedicated property later)
    private final long defaultTimeoutMs;

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
                if (jt != null && !jt.isEmpty()) {
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
