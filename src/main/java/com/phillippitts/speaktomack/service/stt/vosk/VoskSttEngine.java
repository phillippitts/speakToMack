package com.phillippitts.speaktomack.service.stt.vosk;

import com.phillippitts.speaktomack.config.stt.VoskConfig;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import jakarta.annotation.PreDestroy;
import java.util.Objects;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.phillippitts.speaktomack.service.stt.watchdog.EngineFailureEvent;

/**
 * Vosk-based implementation of the SttEngine interface.
 *
 * <p>This engine provides offline speech-to-text transcription using the Vosk library.
 * It supports per-call recognizer creation for thread-safe concurrent transcription.
 *
 * <p>Thread-safe: All state-modifying operations are synchronized.
 * Transcription operations use per-call recognizers for safe concurrent access.
 *
 * <p>Audio contract: This engine expects raw PCM audio in the format defined by
 * com.phillippitts.speaktomack.service.audio.AudioFormat (16kHz, 16-bit, mono, little-endian).
 * Callers must validate/convert inputs (e.g., strip WAV headers) before invoking {@link #transcribe(byte[])}.
 */
@Component
public class VoskSttEngine implements SttEngine {

    private static final Logger LOG = LogManager.getLogger(VoskSttEngine.class);
    private static final String ENGINE_NAME = "vosk";

    /**
     * Maximum allowed JSON response size from Vosk recognizer (1MB).
     * Protects against malicious/custom Vosk builds returning unbounded output.
     */
    private static final int MAX_JSON_SIZE = 1_048_576; // 1MB

    private final VoskConfig config;
    private final Object lock = new Object();

    // Lightweight concurrency guard (configurable)
    private final java.util.concurrent.Semaphore concurrencySemaphore;
    private final int acquireTimeoutMs;

    // Optional event publisher for watchdog events
    private ApplicationEventPublisher publisher;

    // Native resources (created in initialize, closed in close)
    // @GuardedBy("lock")
    private org.vosk.Model model;           // JNI resource

    // Recognizer is created per call for thread-safety; only the Model is held.

    // @GuardedBy("lock")
    private boolean initialized = false;

    // @GuardedBy("lock")
    private boolean closed = false;

    public VoskSttEngine(VoskConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.concurrencySemaphore = new java.util.concurrent.Semaphore(4);
        this.acquireTimeoutMs = 1000; // Default 1 second
    }

    /**
     * Spring-friendly constructor to inject concurrency limits.
     */
    @Autowired
    public VoskSttEngine(VoskConfig config,
            com.phillippitts.speaktomack.config.stt.SttConcurrencyProperties concurrencyProperties,
            ApplicationEventPublisher publisher) {
        this.config = Objects.requireNonNull(config, "config");
        int max = Math.max(1, concurrencyProperties.getVoskMax());
        this.concurrencySemaphore = new java.util.concurrent.Semaphore(max);
        this.acquireTimeoutMs = Math.max(0, concurrencyProperties.getAcquireTimeoutMs());
        this.publisher = publisher;
    }

    /**
     * Initializes the Vosk engine by loading the model (recognizer is created per transcription call).
     *
     * <p>This operation is idempotent - multiple calls will not reload the model.
     *
     * <p>Thread-safe: synchronized to prevent concurrent initialization.
     *
     * @throws TranscriptionException if model loading or recognizer creation fails
     */
    @Override
    public void initialize() {
        synchronized (lock) {
            if (initialized) {
                LOG.debug("VoskSttEngine.initialize(): already initialized");
                return;
            }
            LOG.info("Initializing Vosk engine: modelPath={}, sampleRate={}, maxAlternatives={}",
                    config.modelPath(), config.sampleRate(), config.maxAlternatives());
            try {
                // Load model only; recognizer is created per transcription call for thread-safety
                this.model = new org.vosk.Model(config.modelPath());

                initialized = true;
                closed = false;
                LOG.info("Vosk engine initialized");
            } catch (Throwable t) {
                // Ensure partial resources are closed on failure
                safeCloseUnlocked();
                if (publisher != null) {
                    Map<String, String> ctx = new HashMap<>();
                    ctx.put("modelPath", String.valueOf(config.modelPath()));
                    ctx.put("sampleRate", String.valueOf(config.sampleRate()));
                    publisher.publishEvent(new EngineFailureEvent(ENGINE_NAME, java.time.Instant.now(),
                            "initialize failure", t, ctx));
                }
                throw new TranscriptionException(
                    "Failed to initialize Vosk (model=" + config.modelPath()
                        + ", sampleRate=" + config.sampleRate() + ")", ENGINE_NAME, t);
            }
        }
    }

    /**
     * Transcribes audio data to text.
     *
     * <p>Creates a new recognizer per call for thread-safe operation.
     * Empty text results are valid (e.g., silence or unclear audio).
     *
     * @param audioData PCM audio data (16kHz, 16-bit, mono)
     * @return transcription result with text and confidence score
     * @throws IllegalArgumentException if audioData is null or empty
     * @throws TranscriptionException if engine not initialized or transcription fails
     */
    @Override
    public TranscriptionResult transcribe(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            throw new IllegalArgumentException("audioData must not be null or empty");
        }
        boolean acquired = false;
        try {
            // Try to acquire with bounded wait to handle brief spikes gracefully
            acquired = concurrencySemaphore.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
            if (!acquired) {
                if (publisher != null) {
                    Map<String, String> ctx = new HashMap<>();
                    ctx.put("reason", "concurrency-limit");
                    ctx.put("timeoutMs", String.valueOf(acquireTimeoutMs));
                    publisher.publishEvent(new EngineFailureEvent(ENGINE_NAME, java.time.Instant.now(),
                            "concurrency limit reached after " + acquireTimeoutMs + "ms wait", null, ctx));
                }
                throw new TranscriptionException("Vosk concurrency limit reached after " + acquireTimeoutMs + "ms wait", ENGINE_NAME);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore interrupt status
            throw new TranscriptionException("Vosk transcription interrupted while waiting for semaphore", ENGINE_NAME, e);
        }
        try {
            org.vosk.Model localModel;
            synchronized (lock) {
                if (!initialized || closed) {
                    throw new TranscriptionException("Vosk engine not initialized", ENGINE_NAME);
                }
                localModel = this.model;
            }
            // Recognizer per call for thread-safety
            try (org.vosk.Recognizer localRecognizer = new org.vosk.Recognizer(localModel, config.sampleRate())) {
                // Some Vosk versions expose this method; guard against NoSuchMethodError
                try {
                    localRecognizer.setMaxAlternatives(config.maxAlternatives());
                } catch (NoSuchMethodError ignored) {
                    // ignore
                }
                // Feed full buffer (whole-clip MVP) and finalize
                localRecognizer.acceptWaveForm(audioData, audioData.length);
                String json = localRecognizer.getFinalResult();
                VoskTranscription transcription = parseVoskJson(json);
                // Empty text is valid (e.g., silence or unclear audio)
                return TranscriptionResult.of(transcription.text(), transcription.confidence(), getEngineName());
            } catch (Throwable t) {
                throw new TranscriptionException("Vosk transcription failed", ENGINE_NAME, t);
            }
        } finally {
            if (acquired) {
                concurrencySemaphore.release();
            }
        }
    }

    /**
     * Returns the engine name identifier.
     *
     * @return "vosk"
     */
    @Override
    public String getEngineName() {
        return ENGINE_NAME;
    }

    /**
     * Checks if the engine is healthy and ready to transcribe.
     *
     * <p>Returns true if initialized, not closed, and model is loaded.
     *
     * @return true if healthy, false otherwise
     */
    @Override
    public boolean isHealthy() {
        synchronized (lock) {
            return initialized && !closed && model != null;
        }
    }

    /**
     * Closes the Vosk engine and releases JNI resources.
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
                LOG.debug("VoskSttEngine.close(): already closed");
                return;
            }
            safeCloseUnlocked();
            closed = true;
            initialized = false;
        }
        LOG.info("Vosk engine closed");
    }

    /**
     * Closes JNI resources without acquiring lock.
     *
     * <p>Caller must hold lock. Used internally during cleanup.
     *
     * <p>GuardedBy: lock (caller must hold lock)
     */
    private void safeCloseUnlocked() {
        if (model != null) {
            try {
                model.close();
            } catch (Throwable t) {
                LOG.warn("Error closing model", t);
            }
            model = null;
        }
    }

    /**
     * Parses Vosk JSON response and extracts both text and confidence.
     *
     * <p>This method parses the JSON once and extracts both values for efficiency.
     *
     * <p>Protects against unbounded output from malicious/custom Vosk builds by
     * capping JSON response size at {@link #MAX_JSON_SIZE}.
     *
     * @param json JSON string from Vosk recognizer
     * @return VoskTranscription containing text and confidence
     */
    private static VoskTranscription parseVoskJson(String json) {
        if (json == null || json.isBlank()) {
            return new VoskTranscription("", 1.0);
        }
        // Protect against malicious Vosk builds returning huge JSON
        if (json.length() > MAX_JSON_SIZE) {
            LOG.warn("Vosk JSON response exceeds {}B cap (actual: {}B); truncating to prevent OOM",
                    MAX_JSON_SIZE, json.length());
            json = json.substring(0, MAX_JSON_SIZE);
        }
        try {
            JSONObject obj = new JSONObject(json);
            String text = obj.optString("text", "").trim();
            double confidence = extractConfidenceFromJson(obj);
            return new VoskTranscription(text, confidence);
        } catch (Exception e) {
            LOG.warn("Failed to parse Vosk JSON response: {}", json, e);
            return new VoskTranscription("", 1.0);
        }
    }

    /**
     * Extracts confidence from already-parsed JSONObject.
     *
     * @param obj parsed JSON object
     * @return average confidence (0.0-1.0), or 1.0 if no confidence data
     */
    private static double extractConfidenceFromJson(JSONObject obj) {
        if (!obj.has("result")) {
            return 1.0; // No result array, assume perfect confidence
        }
        org.json.JSONArray results = obj.getJSONArray("result");
        if (results.length() == 0) {
            return 1.0; // Empty result, no words recognized
        }

        double sum = 0.0;
        int count = 0;
        for (int i = 0; i < results.length(); i++) {
            JSONObject wordObj = results.getJSONObject(i);
            if (wordObj.has("conf")) {
                sum += wordObj.getDouble("conf");
                count++;
            }
        }

        double rawConfidence = count > 0 ? sum / count : 1.0;
        // Clamp to [0.0, 1.0] to satisfy TranscriptionResult contract
        return Math.min(1.0, Math.max(0.0, rawConfidence));
    }

    /**
     * Internal record to hold parsed Vosk transcription data.
     *
     * @param text transcribed text (may be empty)
     * @param confidence confidence score (0.0-1.0)
     */
    private record VoskTranscription(String text, double confidence) {
    }

    /**
     * Extracts the transcribed text from Vosk's JSON response.
     *
     * @deprecated Use {@link #parseVoskJson(String)} instead for better performance.
     *             This method is kept for backward compatibility with existing tests.
     *             Will be removed in version 2.0.
     *
     * <p><strong>Migration Guide:</strong>
     * <pre>{@code
     * // Old approach (parses JSON twice):
     * String text = extractTextFromVoskJson(json);
     * double confidence = extractConfidenceFromVoskJson(json);
     *
     * // New approach (parses JSON once):
     * VoskTranscription result = parseVoskJson(json);
     * String text = result.text();
     * double confidence = result.confidence();
     * }</pre>
     *
     * <p>Example Vosk JSON: {@code {"text": "hello world"}}
     *
     * @param json JSON string from Vosk recognizer
     * @return extracted text, or empty string if parsing fails
     */
    @Deprecated(since = "1.0", forRemoval = true)
    static String extractTextFromVoskJson(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        // Protect against malicious Vosk builds returning huge JSON
        if (json.length() > MAX_JSON_SIZE) {
            LOG.warn("Vosk JSON response exceeds {}B cap (actual: {}B); truncating to prevent OOM",
                    MAX_JSON_SIZE, json.length());
            json = json.substring(0, MAX_JSON_SIZE);
        }
        try {
            JSONObject obj = new JSONObject(json);
            return obj.optString("text", "").trim();
        } catch (Exception e) {
            LOG.warn("Failed to parse Vosk JSON response: {}", json, e);
            return "";
        }
    }

    /**
     * Extracts average confidence from Vosk's JSON response.
     *
     * @deprecated Use {@link #parseVoskJson(String)} instead for better performance.
     *             This method is kept for backward compatibility with existing tests.
     *             Will be removed in version 2.0.
     *
     * <p><strong>Problem:</strong> Calling {@code extractTextFromVoskJson()} and
     * {@code extractConfidenceFromVoskJson()} separately parses the JSON twice,
     * which is inefficient for production code.
     *
     * <p><strong>Migration Guide:</strong>
     * <pre>{@code
     * // Old approach (inefficient - parses JSON twice):
     * String text = extractTextFromVoskJson(json);
     * double confidence = extractConfidenceFromVoskJson(json);
     *
     * // New approach (efficient - parses JSON once):
     * VoskTranscription result = parseVoskJson(json);
     * String text = result.text();
     * double confidence = result.confidence();
     * }</pre>
     *
     * <p>Vosk provides per-word confidence in the "result" array.
     * Example: {@code {"result": [{"conf": 0.95, "word": "hello"}, {"conf": 0.87, "word": "world"}]}}
     *
     * <p>If no confidence data is available, returns 1.0 (assume perfect confidence).
     *
     * @param json JSON string from Vosk recognizer
     * @return average confidence (0.0-1.0), or 1.0 if parsing fails
     */
    @Deprecated(since = "1.0", forRemoval = true)
    static double extractConfidenceFromVoskJson(String json) {
        if (json == null || json.isBlank()) {
            return 1.0;
        }
        // Protect against malicious Vosk builds returning huge JSON
        if (json.length() > MAX_JSON_SIZE) {
            LOG.warn("Vosk JSON response exceeds {}B cap (actual: {}B); truncating to prevent OOM",
                    MAX_JSON_SIZE, json.length());
            json = json.substring(0, MAX_JSON_SIZE);
        }
        try {
            JSONObject obj = new JSONObject(json);
            if (!obj.has("result")) {
                return 1.0; // No result array, assume perfect confidence
            }
            org.json.JSONArray results = obj.getJSONArray("result");
            if (results.length() == 0) {
                return 1.0; // Empty result, no words recognized
            }

            double sum = 0.0;
            int count = 0;
            for (int i = 0; i < results.length(); i++) {
                JSONObject wordObj = results.getJSONObject(i);
                if (wordObj.has("conf")) {
                    sum += wordObj.getDouble("conf");
                    count++;
                }
            }

            double rawConfidence = count > 0 ? sum / count : 1.0;
            // Clamp to [0.0, 1.0] to satisfy TranscriptionResult contract
            return Math.min(1.0, Math.max(0.0, rawConfidence));
        } catch (Exception e) {
            LOG.warn("Failed to parse confidence from Vosk JSON: {}", json, e);
            return 1.0; // Default to perfect confidence on parse error
        }
    }
}
