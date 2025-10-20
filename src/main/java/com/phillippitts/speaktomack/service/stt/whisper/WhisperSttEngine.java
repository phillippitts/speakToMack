package com.phillippitts.speaktomack.service.stt.whisper;

import com.phillippitts.speaktomack.config.stt.WhisperConfig;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import com.phillippitts.speaktomack.service.audio.WavWriter;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import com.phillippitts.speaktomack.service.stt.watchdog.EngineFailureEvent;
import java.util.HashMap;
import java.util.Map;

import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Whisper-based implementation of {@link SttEngine} using the external whisper.cpp binary.
 *
 * <p>MVP model: whole-buffer processing. The engine expects validated PCM16LE mono 16kHz input
 * and converts it to a temporary WAV file, then executes whisper.cpp via {@link WhisperProcessManager}.
 *
 * <p>Privacy: Never logs full transcription at INFO level. Only duration and character count are logged.
 */
@Component
public final class WhisperSttEngine implements SttEngine {

    private final java.util.concurrent.Semaphore concurrencySemaphore;
    private final int acquireTimeoutMs;

    private static final Logger LOG = LogManager.getLogger(WhisperSttEngine.class);
    private static final String ENGINE = "whisper";

    private final WhisperConfig cfg;
    private final WhisperProcessManager manager;
    private ApplicationEventPublisher publisher;
    private final boolean jsonMode;
    // Stores last JSON/stdout and parsed tokens when jsonMode is enabled; consumed by parallel service
    private volatile String lastRawJson;
    private volatile java.util.List<String> lastTokens;

    private final Object lock = new Object();
    private boolean initialized;
    private boolean closed;

    public WhisperSttEngine(WhisperConfig cfg, WhisperProcessManager manager) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        this.manager = Objects.requireNonNull(manager, "manager");
        this.concurrencySemaphore = new java.util.concurrent.Semaphore(2);
        this.acquireTimeoutMs = 1000; // Default 1 second
        this.jsonMode = false;
    }

    @Autowired
    public WhisperSttEngine(WhisperConfig cfg,
                             com.phillippitts.speaktomack.config.stt.SttConcurrencyProperties concurrencyProperties,
                             WhisperProcessManager manager,
                             ApplicationEventPublisher publisher,
                             @org.springframework.beans.factory.annotation.Value("${stt.whisper.output:text}")
                             String outputMode) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        this.manager = Objects.requireNonNull(manager, "manager");
        int max = Math.max(1, concurrencyProperties.getWhisperMax());
        this.concurrencySemaphore = new java.util.concurrent.Semaphore(max);
        this.acquireTimeoutMs = Math.max(0, concurrencyProperties.getAcquireTimeoutMs());
        this.publisher = publisher;
        this.jsonMode = "json".equalsIgnoreCase(outputMode);
    }

    @Override
    public void initialize() {
        synchronized (lock) {
            if (closed) {
                IllegalStateException ex = new IllegalStateException("Engine already closed");
                if (publisher != null) {
                    Map<String, String> ctx = new HashMap<>();
                    ctx.put("binaryPath", cfg.binaryPath());
                    ctx.put("modelPath", cfg.modelPath());
                    publisher.publishEvent(new EngineFailureEvent(ENGINE, java.time.Instant.now(),
                            "initialize failure (already closed)", ex, ctx));
                }
                throw ex;
            }
            if (initialized) {
                return;
            }
            try {
                initialized = true; // Fail-fast validation already ran at startup
                LOG.info("Whisper engine initialized: bin={}, model={}, timeout={}s, lang={}, threads={}",
                        cfg.binaryPath(), cfg.modelPath(), cfg.timeoutSeconds(), cfg.language(), cfg.threads());
            } catch (Throwable t) {
                if (publisher != null) {
                    Map<String, String> ctx = new HashMap<>();
                    ctx.put("binaryPath", cfg.binaryPath());
                    ctx.put("modelPath", cfg.modelPath());
                    publisher.publishEvent(new EngineFailureEvent(ENGINE, java.time.Instant.now(),
                            "initialize failure", t, ctx));
                }
                throw new TranscriptionException("Whisper initialization failed", ENGINE, t);
            }
        }
    }

    @Override
    public TranscriptionResult transcribe(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            throw new IllegalArgumentException("audioData must not be null or empty");
        }
        boolean acquired = acquireConcurrencyPermit();
        try {
            checkEngineInitialized();
            Path wav = null;
            long t0 = System.nanoTime();
            try {
                wav = createTempWavFile(audioData);
                String stdout = manager.transcribe(wav, cfg);
                String text = extractTranscriptionText(stdout);
                double confidence = 1.0;

                long ms = (System.nanoTime() - t0) / 1_000_000L;
                LOG.info("Whisper transcribed clip in {} ms (chars={})", ms, text.length());
                return TranscriptionResult.of(text, confidence, ENGINE);
            } catch (Exception e) {
                publishTranscribeFailureEvent(e);
                if (e instanceof TranscriptionException te) {
                    throw te;
                }
                throw new TranscriptionException("Whisper transcription failed: " + e.getMessage(), ENGINE, e);
            } finally {
                cleanupTempFile(wav);
            }
        } finally {
            if (acquired) {
                concurrencySemaphore.release();
            }
        }
    }

    /**
     * Acquires concurrency permit with timeout.
     *
     * @return true if permit was acquired
     * @throws TranscriptionException if permit cannot be acquired or interrupted
     */
    private boolean acquireConcurrencyPermit() {
        try {
            boolean acquired = concurrencySemaphore.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
            if (!acquired) {
                if (publisher != null) {
                    Map<String, String> ctx = new HashMap<>();
                    ctx.put("reason", "concurrency-limit");
                    ctx.put("timeoutMs", String.valueOf(acquireTimeoutMs));
                    publisher.publishEvent(new EngineFailureEvent(ENGINE, java.time.Instant.now(),
                            "concurrency limit reached after " + acquireTimeoutMs + "ms wait", null, ctx));
                }
                throw new TranscriptionException("Whisper concurrency limit reached after "
                        + acquireTimeoutMs + "ms wait", ENGINE);
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TranscriptionException("Whisper transcription interrupted while waiting for semaphore",
                    ENGINE, e);
        }
    }

    /**
     * Checks if engine is initialized and not closed.
     *
     * @throws TranscriptionException if engine is not ready
     */
    private void checkEngineInitialized() {
        synchronized (lock) {
            if (!initialized || closed) {
                throw new TranscriptionException("Whisper engine not initialized", ENGINE);
            }
        }
    }

    /**
     * Creates a temporary WAV file from PCM audio data.
     *
     * @param audioData PCM16LE mono 16kHz audio data
     * @return path to temporary WAV file
     * @throws Exception if file creation or write fails
     */
    private Path createTempWavFile(byte[] audioData) throws Exception {
        Path wav = Files.createTempFile("whisper-", ".wav");
        WavWriter.writePcm16LeMono16kHz(audioData, wav);
        return wav;
    }

    /**
     * Extracts transcription text from whisper.cpp output (JSON or plain text mode).
     *
     * @param stdout raw output from whisper.cpp process
     * @return extracted transcription text
     */
    private String extractTranscriptionText(String stdout) {
        if (jsonMode) {
            // When JSON mode is enabled, parse text from JSON safely and cache JSON + tokens
            String text = WhisperJsonParser.extractText(stdout);
            this.lastRawJson = stdout;
            this.lastTokens = WhisperJsonParser.extractTokens(stdout);
            return text;
        } else {
            this.lastRawJson = null;
            this.lastTokens = null;
            return stdout == null ? "" : stdout.trim();
        }
    }

    /**
     * Publishes transcription failure event with context.
     *
     * @param cause the exception that caused the failure
     */
    private void publishTranscribeFailureEvent(Exception cause) {
        if (publisher != null) {
            Map<String, String> ctx = new HashMap<>();
            ctx.put("binaryPath", cfg.binaryPath());
            ctx.put("modelPath", cfg.modelPath());
            publisher.publishEvent(new EngineFailureEvent(ENGINE, java.time.Instant.now(),
                    "transcribe failure", cause, ctx));
        }
    }

    /**
     * Cleans up temporary WAV file, ignoring any errors.
     *
     * @param wav path to temporary WAV file (may be null)
     */
    private void cleanupTempFile(Path wav) {
        if (wav != null) {
            try {
                Files.deleteIfExists(wav);
            } catch (Exception ignore) {
                // Cleanup failure is non-critical
            }
        }
    }

    /**
     * Consume and clear the last raw JSON captured in JSON mode.
     * Returns null if JSON mode is disabled or no JSON available.
     */
    public String consumeLastRawJson() {
        String v = this.lastRawJson;
        this.lastRawJson = null;
        return v;
    }

    /**
     * Consume and clear the last parsed tokens captured in JSON mode.
     * Returns an empty list if JSON mode is disabled or no tokens available.
     */
    public java.util.List<String> consumeLastTokens() {
        java.util.List<String> v = this.lastTokens;
        this.lastTokens = null;
        return v == null ? java.util.List.of() : v;
    }

    @Override
    public String getEngineName() {
        return ENGINE;
    }

    @Override
    public boolean isHealthy() {
        synchronized (lock) {
            return initialized && !closed;
        }
    }

    /**
     * Closes the Whisper engine and releases process resources.
     *
     * <p>This operation is idempotent - multiple calls are safe.
     *
     * <p>Thread-safe: synchronized to prevent concurrent closure.
     *
     * <p>Automatically invoked by Spring container on shutdown via {@link PreDestroy}.
     */
    @Override
    @PreDestroy
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            initialized = false;
        }
        try {
            manager.close();
        } catch (Exception e) {
            LOG.debug("manager.close()", e);
        }
        LOG.info("Whisper engine closed");
    }
}
