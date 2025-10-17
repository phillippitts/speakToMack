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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

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

    private static final Logger LOG = LogManager.getLogger(WhisperSttEngine.class);
    private static final String ENGINE = "whisper";

    private final WhisperConfig cfg;
    private final WhisperProcessManager manager;
    private ApplicationEventPublisher publisher;

    private final Object lock = new Object();
    private boolean initialized;
    private boolean closed;

    public WhisperSttEngine(WhisperConfig cfg, WhisperProcessManager manager) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        this.manager = Objects.requireNonNull(manager, "manager");
        this.concurrencySemaphore = new java.util.concurrent.Semaphore(2);
    }

    @Autowired
    public WhisperSttEngine(WhisperConfig cfg,
                             com.phillippitts.speaktomack.config.stt.SttConcurrencyProperties concurrencyProperties,
                             WhisperProcessManager manager,
                             ApplicationEventPublisher publisher) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        this.manager = Objects.requireNonNull(manager, "manager");
        int max = Math.max(1, concurrencyProperties.getWhisperMax());
        this.concurrencySemaphore = new java.util.concurrent.Semaphore(max);
        this.publisher = publisher;
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
        boolean acquired = false;
        try {
            acquired = concurrencySemaphore.tryAcquire();
            if (!acquired) {
                if (publisher != null) {
                    Map<String, String> ctx = new HashMap<>();
                    ctx.put("reason", "concurrency-limit");
                    publisher.publishEvent(new EngineFailureEvent(ENGINE, java.time.Instant.now(),
                            "concurrency limit reached", null, ctx));
                }
                throw new TranscriptionException("Whisper concurrency limit reached", ENGINE);
            }
            synchronized (lock) {
                if (!initialized || closed) {
                    throw new TranscriptionException("Whisper engine not initialized", ENGINE);
                }
            }
            Path wav = null;
            long t0 = System.nanoTime();
            try {
                wav = Files.createTempFile("whisper-", ".wav");
                WavWriter.writePcm16LeMono16kHz(audioData, wav);

                String stdout = manager.transcribe(wav, cfg); // may throw TranscriptionException
                String text = stdout == null ? "" : stdout.trim();
                double confidence = 1.0; // Unknown in text mode

                long ms = (System.nanoTime() - t0) / 1_000_000L;
                LOG.info("Whisper transcribed clip in {} ms (chars={})", ms, text.length());
                return TranscriptionResult.of(text, confidence, ENGINE);
            } catch (Exception e) {
                if (e instanceof TranscriptionException te) {
                    if (publisher != null) {
                        Map<String, String> ctx = new HashMap<>();
                        ctx.put("binaryPath", cfg.binaryPath());
                        ctx.put("modelPath", cfg.modelPath());
                        publisher.publishEvent(new EngineFailureEvent(ENGINE, java.time.Instant.now(),
                                "transcribe failure", e, ctx));
                    }
                    throw te; // already enriched by manager
                }
                if (publisher != null) {
                    Map<String, String> ctx = new HashMap<>();
                    ctx.put("binaryPath", cfg.binaryPath());
                    ctx.put("modelPath", cfg.modelPath());
                    publisher.publishEvent(new EngineFailureEvent(ENGINE, java.time.Instant.now(),
                            "transcribe failure", e, ctx));
                }
                throw new TranscriptionException("Whisper transcription failed: " + e.getMessage(), ENGINE, e);
            } finally {
                if (wav != null) {
                    try {
                        Files.deleteIfExists(wav);
                    } catch (Exception ignore) {
                        // Cleanup failure is non-critical
                    }
                }
            }
        } finally {
            if (acquired) {
                concurrencySemaphore.release();
            }
        }
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

    @Override
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
