package com.phillippitts.speaktomack.service.stt.whisper;

import com.phillippitts.speaktomack.config.stt.WhisperConfig;
import com.phillippitts.speaktomack.config.properties.SttConcurrencyProperties;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import com.phillippitts.speaktomack.service.audio.WavWriter;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import com.phillippitts.speaktomack.service.stt.util.ConcurrencyGuard;
import com.phillippitts.speaktomack.service.stt.util.EngineEventPublisher;
import com.phillippitts.speaktomack.util.TimeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import java.util.HashMap;
import java.util.Map;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/**
 * Whisper-based implementation of {@link SttEngine} using the external whisper.cpp binary.
 *
 * <p>This engine provides high-accuracy offline speech-to-text transcription by invoking
 * the whisper.cpp command-line binary as a subprocess. It supports both text and JSON output
 * modes, with JSON mode enabling advanced features like word-level tokens for reconciliation.
 *
 * <p><b>Architecture:</b>
 * <ul>
 *   <li>Converts PCM audio to temporary WAV file using {@link WavWriter}</li>
 *   <li>Invokes whisper.cpp via {@link WhisperProcessManager} with configured timeout</li>
 *   <li>Parses output (plain text or JSON) and cleans up temporary files</li>
 *   <li>Supports concurrency limiting via semaphore to prevent CPU saturation</li>
 * </ul>
 *
 * <p><b>Output Modes:</b>
 * <ul>
 *   <li><b>Text Mode (default):</b> Simple text output from whisper.cpp (stdout)</li>
 *   <li><b>JSON Mode:</b> Structured JSON output with word-level tokens and timestamps.
 *       Enabled via {@code stt.whisper.output=json}. Provides enhanced accuracy for
 *       {@link com.phillippitts.speaktomack.service.reconcile.impl.WordOverlapReconciler}.</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> This engine is thread-safe. Concurrent transcriptions are
 * protected by a semaphore (configurable max). Each transcription runs in isolation with
 * its own temporary WAV file and subprocess.
 *
 * <p><b>Privacy:</b> Never logs full transcription text at INFO level. Only duration
 * and character count are logged to protect user privacy.
 *
 * <p><b>Audio Contract:</b> Expects raw PCM audio in the format defined by
 * {@link com.phillippitts.speaktomack.service.audio.AudioFormat} (16kHz, 16-bit, mono, little-endian).
 * Callers must validate/convert inputs before invoking {@link #transcribe(byte[])}.
 *
 * @see WhisperProcessManager
 * @see WhisperConfig
 * @see WhisperJsonParser
 * @since 1.0
 */
@Component
public final class WhisperSttEngine extends com.phillippitts.speaktomack.service.stt.AbstractSttEngine {

    private final ConcurrencyGuard concurrencyGuard;

    private static final Logger LOG = LogManager.getLogger(WhisperSttEngine.class);
    private static final String ENGINE = "whisper";

    private final WhisperConfig cfg;
    private final WhisperProcessManager manager;
    private ApplicationEventPublisher publisher;
    private final boolean jsonMode;
    // Stores last JSON/stdout and parsed tokens when jsonMode is enabled; consumed by parallel service
    private volatile String lastRawJson;
    private volatile java.util.List<String> lastTokens;

    public WhisperSttEngine(WhisperConfig cfg, WhisperProcessManager manager) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        this.manager = Objects.requireNonNull(manager, "manager");
        this.concurrencyGuard = new ConcurrencyGuard(
                new Semaphore(2),
                1000, // Default 1 second timeout
                ENGINE,
                null // No publisher in basic constructor
        );
        this.jsonMode = false;
    }

    @Autowired
    public WhisperSttEngine(WhisperConfig cfg,
                             SttConcurrencyProperties concurrencyProperties,
                             WhisperProcessManager manager,
                             ApplicationEventPublisher publisher,
                             @org.springframework.beans.factory.annotation.Value("${stt.whisper.output:text}")
                             String outputMode) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        this.manager = Objects.requireNonNull(manager, "manager");
        int max = Math.max(1, concurrencyProperties.getWhisperMax());
        long timeoutMs = Math.max(0, concurrencyProperties.getAcquireTimeoutMs());
        this.concurrencyGuard = new ConcurrencyGuard(
                new Semaphore(max),
                timeoutMs,
                ENGINE,
                publisher
        );
        this.publisher = publisher;
        this.jsonMode = "json".equalsIgnoreCase(outputMode);
    }

    @Override
    protected void doInitialize() {
        try {
            // Allow reinitialization after close() for watchdog restart support
            closed = false;
            // Fail-fast validation already ran at startup
            LOG.info("Whisper engine initialized: bin={}, model={}, timeout={}s, lang={}, threads={}",
                    cfg.binaryPath(), cfg.modelPath(), cfg.timeoutSeconds(), cfg.language(), cfg.threads());
        } catch (Throwable t) {
            Map<String, String> ctx = new HashMap<>();
            ctx.put("binaryPath", cfg.binaryPath());
            ctx.put("modelPath", cfg.modelPath());
            EngineEventPublisher.publishFailure(publisher, ENGINE, "initialize failure", t, ctx);
            throw new TranscriptionException("Whisper initialization failed", ENGINE, t);
        }
    }

    @Override
    public TranscriptionResult transcribe(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            throw new IllegalArgumentException("audioData must not be null or empty");
        }
        try {
            concurrencyGuard.acquire();
            ensureInitialized();
            Path wav = null;
            long startTime = System.nanoTime();
            try {
                wav = createTempWavFile(audioData);
                String stdout = manager.transcribe(wav, cfg);
                String text = extractTranscriptionText(stdout);
                double confidence = 1.0;

                long elapsedMs = TimeUtils.elapsedMillis(startTime);
                LOG.debug("Whisper transcribed clip in {} ms (chars={})", elapsedMs, text.length());
                return TranscriptionResult.of(text, confidence, ENGINE);
            } catch (Exception e) {
                Map<String, String> ctx = new HashMap<>();
                ctx.put("binaryPath", cfg.binaryPath());
                ctx.put("modelPath", cfg.modelPath());
                EngineEventPublisher.publishFailure(publisher, ENGINE, "transcribe failure", e, ctx);
                if (e instanceof TranscriptionException te) {
                    throw te;
                }
                throw new TranscriptionException("Whisper transcription failed: " + e.getMessage(), ENGINE, e);
            } finally {
                cleanupTempFile(wav);
            }
        } finally {
            concurrencyGuard.release();
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
     * Consumes and clears the last raw JSON output captured from whisper.cpp (JSON mode only).
     *
     * <p>This method is used by {@link com.phillippitts.speaktomack.service.stt.parallel.DefaultParallelSttService}
     * to access the raw JSON output for advanced reconciliation strategies.
     *
     * <p><b>Side Effect:</b> Clears the cached JSON after returning it (one-time consumption).
     * Subsequent calls will return {@code null} until the next transcription.
     *
     * <p><b>JSON Mode Only:</b> Returns {@code null} if JSON mode is disabled
     * ({@code stt.whisper.output=text}) or if no transcription has occurred yet.
     *
     * @return raw JSON output from whisper.cpp, or null if unavailable
     * @see #consumeLastTokens()
     * @see WhisperJsonParser
     */
    public String consumeLastRawJson() {
        String v = this.lastRawJson;
        this.lastRawJson = null;
        return v;
    }

    /**
     * Consumes and clears the last parsed word tokens from whisper.cpp (JSON mode only).
     *
     * <p>This method is used by {@link com.phillippitts.speaktomack.service.stt.parallel.DefaultParallelSttService}
     * to access word-level tokens for overlap-based reconciliation strategies like
     * {@link com.phillippitts.speaktomack.service.reconcile.impl.WordOverlapReconciler}.
     *
     * <p>Word tokens provide more accurate overlap calculation than simple space-splitting,
     * especially for multi-word phrases and punctuation handling.
     *
     * <p><b>Side Effect:</b> Clears the cached tokens after returning them (one-time consumption).
     * Subsequent calls will return an empty list until the next transcription.
     *
     * <p><b>JSON Mode Only:</b> Returns an empty list if JSON mode is disabled
     * ({@code stt.whisper.output=text}) or if no transcription has occurred yet.
     *
     * @return list of word tokens from last transcription, or empty list if unavailable
     * @see #consumeLastRawJson()
     * @see WhisperJsonParser#extractTokens(String)
     */
    public java.util.List<String> consumeLastTokens() {
        java.util.List<String> v = this.lastTokens;
        this.lastTokens = null;
        return v == null ? java.util.List.of() : v;
    }

    /**
     * Implementation of SttEngine interface method for consuming tokens.
     * Wraps {@link #consumeLastTokens()} to return Optional for polymorphic usage.
     */
    @Override
    public java.util.Optional<java.util.List<String>> consumeTokens() {
        java.util.List<String> tokens = consumeLastTokens();
        return tokens.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(tokens);
    }

    /**
     * Implementation of SttEngine interface method for consuming raw JSON.
     * Wraps {@link #consumeLastRawJson()} to return Optional for polymorphic usage.
     */
    @Override
    public java.util.Optional<String> consumeRawJson() {
        String json = consumeLastRawJson();
        return json == null ? java.util.Optional.empty() : java.util.Optional.of(json);
    }

    @Override
    public String getEngineName() {
        return ENGINE;
    }

    /**
     * Closes the Whisper engine and releases process resources.
     *
     * <p>Called by {@link #close()} within synchronized context.
     */
    @Override
    protected void doClose() {
        try {
            manager.close();
        } catch (Exception e) {
            LOG.debug("manager.close()", e);
        }
        LOG.info("Whisper engine closed");
    }
}
