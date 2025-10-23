package com.phillippitts.speaktomack.service.stt.vosk;

import com.phillippitts.speaktomack.config.stt.VoskConfig;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import com.phillippitts.speaktomack.service.audio.AudioSilenceDetector;
import com.phillippitts.speaktomack.service.stt.SttEngineNames;
import com.phillippitts.speaktomack.service.stt.util.ConcurrencyGuard;
import com.phillippitts.speaktomack.service.stt.util.EngineEventPublisher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.concurrent.Semaphore;

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
public class VoskSttEngine extends com.phillippitts.speaktomack.service.stt.AbstractSttEngine {

    private static final Logger LOG = LogManager.getLogger(VoskSttEngine.class);

    /**
     * Maximum allowed JSON response size from Vosk recognizer (1MB).
     * Protects against malicious/custom Vosk builds returning unbounded output.
     */
    private static final int MAX_JSON_SIZE = 1_048_576; // 1MB

    /**
     * Default concurrency limit for Vosk transcription when no configuration provided.
     */
    private static final int DEFAULT_CONCURRENCY_LIMIT = 4;

    /**
     * Default semaphore acquire timeout in milliseconds.
     */
    private static final int DEFAULT_ACQUIRE_TIMEOUT_MS = 1000;

    private final VoskConfig config;

    // Lightweight concurrency guard (configurable)
    private final ConcurrencyGuard concurrencyGuard;

    // Optional event publisher for watchdog events
    private ApplicationEventPublisher publisher;

    // Silence gap threshold for pause-based paragraph detection (milliseconds)
    private final int silenceGapMs;

    // Native resources (created in initialize, closed in close)
    // @GuardedBy("lock")
    private org.vosk.Model model;           // JNI resource

    // Recognizer is created per call for thread-safety; only the Model is held.

    public VoskSttEngine(VoskConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.concurrencyGuard = new ConcurrencyGuard(
                new Semaphore(DEFAULT_CONCURRENCY_LIMIT),
                DEFAULT_ACQUIRE_TIMEOUT_MS,
                SttEngineNames.VOSK,
                null // No publisher in basic constructor
        );
        this.silenceGapMs = 0; // Disabled in basic constructor
    }

    /**
     * Spring-friendly constructor to inject concurrency limits and pause detection config.
     */
    public VoskSttEngine(VoskConfig config,
            com.phillippitts.speaktomack.config.properties.SttConcurrencyProperties concurrencyProperties,
            ApplicationEventPublisher publisher,
            com.phillippitts.speaktomack.config.properties.OrchestrationProperties orchestrationProperties) {
        this.config = Objects.requireNonNull(config, "config");
        int max = Math.max(1, concurrencyProperties.getVoskMax());
        long timeoutMs = Math.max(0, concurrencyProperties.getAcquireTimeoutMs());
        this.concurrencyGuard = new ConcurrencyGuard(
                new Semaphore(max),
                timeoutMs,
                SttEngineNames.VOSK,
                publisher
        );
        this.publisher = publisher;
        this.silenceGapMs = orchestrationProperties != null ?
                orchestrationProperties.getSilenceGapMs() : 0;
    }

    /**
     * Initializes the Vosk engine by loading the model (recognizer is created per transcription call).
     *
     * <p>Called by {@link #initialize()} within synchronized context. Supports reinitialization
     * after close by resetting the {@code closed} flag.
     *
     * @throws TranscriptionException if model loading fails
     */
    @Override
    protected void doInitialize() {
        LOG.info("Initializing Vosk engine: modelPath={}, sampleRate={}, maxAlternatives={}",
                config.modelPath(), config.sampleRate(), config.maxAlternatives());
        try {
            // Load model only; recognizer is created per transcription call for thread-safety
            this.model = new org.vosk.Model(config.modelPath());
            closed = false; // Support reinitialization after close
            LOG.info("Vosk engine initialized");
        } catch (Throwable t) {
            // Ensure partial resources are closed on failure
            safeCloseUnlocked();
            EngineEventPublisher.publishFailure(
                publisher,
                SttEngineNames.VOSK,
                "initialize failure",
                t,
                Map.of("modelPath", String.valueOf(config.modelPath()),
                       "sampleRate", String.valueOf(config.sampleRate()))
            );
            throw com.phillippitts.speaktomack.exception.TranscriptionExceptionBuilder
                    .create("Failed to initialize Vosk")
                    .engine(SttEngineNames.VOSK)
                    .cause(t)
                    .metadata("modelPath", config.modelPath())
                    .metadata("sampleRate", config.sampleRate())
                    .build();
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
        LOG.debug("VoskSttEngine.transcribe: received {} bytes of audio data", audioData.length);

        try {
            concurrencyGuard.acquire();
            org.vosk.Model localModel = getModelForTranscription();
            return transcribeWithModel(localModel, audioData);
        } finally {
            concurrencyGuard.release();
        }
    }

    /**
     * Retrieves the model for transcription, ensuring the engine is initialized.
     *
     * <p>Thread-safe: synchronized to ensure consistent state.
     *
     * @return the Vosk model instance
     * @throws TranscriptionException if engine not initialized or closed
     */
    private org.vosk.Model getModelForTranscription() {
        ensureInitialized();
        synchronized (lock) {
            return this.model;
        }
    }

    /**
     * Transcribes audio data using the provided Vosk model.
     *
     * <p>If pause detection is enabled ({@code silenceGapMs > 0}), splits audio at
     * detected silence boundaries and joins transcriptions with newlines.
     *
     * @param localModel the Vosk model to use for transcription
     * @param audioData PCM audio data to transcribe
     * @return transcription result with text, confidence, and metadata
     */
    private TranscriptionResult transcribeWithModel(org.vosk.Model localModel, byte[] audioData) {
        if (silenceGapMs > 0) {
            return transcribeWithPauseDetection(localModel, audioData);
        } else {
            return transcribeSingleSegment(localModel, audioData);
        }
    }

    /**
     * Transcribes a single audio segment without pause detection.
     *
     * @param localModel the Vosk model to use
     * @param audioData PCM audio data
     * @return transcription result
     */
    private TranscriptionResult transcribeSingleSegment(org.vosk.Model localModel, byte[] audioData) {
        try (org.vosk.Recognizer localRecognizer = new org.vosk.Recognizer(localModel, config.sampleRate())) {
            configureRecognizer(localRecognizer);
            String json = processAudioAndGetResult(localRecognizer, audioData);
            return parseJsonAndCreateResult(json);
        } catch (Exception e) {
            throw handleTranscriptionError(e, publisher, null);
        }
    }

    /**
     * Transcribes audio with pause detection, inserting newlines at silence boundaries.
     *
     * <p>Detects silence regions in the PCM audio buffer, splits at those boundaries,
     * transcribes each segment separately, and joins with newlines.
     *
     * @param localModel the Vosk model to use
     * @param audioData PCM audio data
     * @return transcription result with newlines at pause boundaries
     */
    private TranscriptionResult transcribeWithPauseDetection(org.vosk.Model localModel, byte[] audioData) {
        try {
            // Detect silence boundaries in the audio
            List<Integer> boundaries = AudioSilenceDetector.detectSilenceBoundaries(
                    audioData, silenceGapMs, config.sampleRate());

            if (boundaries.isEmpty()) {
                // No pauses detected, transcribe as single segment
                return transcribeSingleSegment(localModel, audioData);
            }

            // Split audio at silence boundaries and transcribe each segment
            List<String> segments = new ArrayList<>();
            double totalConfidence = 0.0;
            int segmentCount = 0;

            int start = 0;
            for (int boundary : boundaries) {
                if (boundary > start) {
                    byte[] segment = Arrays.copyOfRange(audioData, start, boundary);
                    TranscriptionResult segmentResult = transcribeSingleSegment(localModel, segment);
                    String text = segmentResult.text();
                    if (!text.isBlank()) {
                        segments.add(text);
                        totalConfidence += segmentResult.confidence();
                        segmentCount++;
                    }
                    start = boundary;
                }
            }

            // Handle remaining audio after last boundary
            if (start < audioData.length) {
                byte[] segment = Arrays.copyOfRange(audioData, start, audioData.length);
                TranscriptionResult segmentResult = transcribeSingleSegment(localModel, segment);
                String text = segmentResult.text();
                if (!text.isBlank()) {
                    segments.add(text);
                    totalConfidence += segmentResult.confidence();
                    segmentCount++;
                }
            }

            // Join segments with newlines
            String fullText = String.join("\n", segments);
            double avgConfidence = segmentCount > 0 ? totalConfidence / segmentCount : 1.0;

            LOG.debug("Vosk pause detection: split into {} segments", segmentCount);
            return TranscriptionResult.of(fullText, avgConfidence, getEngineName());

        } catch (Exception e) {
            throw handleTranscriptionError(e, publisher, null);
        }
    }

    /**
     * Configures the recognizer with application settings.
     *
     * <p>Some Vosk versions may not expose certain configuration methods;
     * this method guards against NoSuchMethodError for forward compatibility.
     *
     * @param recognizer the recognizer to configure
     */
    private void configureRecognizer(org.vosk.Recognizer recognizer) {
        try {
            recognizer.setMaxAlternatives(config.maxAlternatives());
        } catch (NoSuchMethodError ignored) {
            // Older Vosk versions may not have this method - silently ignore
        }
    }

    /**
     * Processes audio data through the recognizer and retrieves the final JSON result.
     *
     * @param recognizer the Vosk recognizer instance
     * @param audioData PCM audio data to process
     * @return JSON string containing the transcription result
     */
    private String processAudioAndGetResult(org.vosk.Recognizer recognizer, byte[] audioData) {
        LOG.debug("VoskSttEngine: feeding {} bytes to Vosk recognizer", audioData.length);
        recognizer.acceptWaveForm(audioData, audioData.length);
        String json = recognizer.getFinalResult();
        LOG.debug("VoskSttEngine: Vosk returned JSON (length={} chars): {}", json.length(), json);
        return json;
    }

    /**
     * Parses Vosk JSON response and creates a TranscriptionResult.
     *
     * @param json JSON string from Vosk recognizer
     * @return TranscriptionResult containing text, confidence, and engine name
     */
    private TranscriptionResult parseJsonAndCreateResult(String json) {
        VoskTranscription transcription = parseVoskJson(json);
        LOG.debug("VoskSttEngine: parsed transcription text='{}' (length={} chars), confidence={}",
                 transcription.text(), transcription.text().length(), transcription.confidence());
        // Empty text is valid (e.g., silence or unclear audio)
        return TranscriptionResult.of(transcription.text(), transcription.confidence(), getEngineName());
    }

    @Override
    public String getEngineName() {
        return SttEngineNames.VOSK;
    }

    /**
     * Closes the Vosk engine and releases JNI resources.
     *
     * <p>Called by {@link #close()} within synchronized context.
     */
    @Override
    protected void doClose() {
        safeCloseUnlocked();
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
     * <p>Package-private for testing purposes.
     *
     * @param json JSON string from Vosk recognizer
     * @return VoskTranscription containing text and confidence
     */
    static VoskTranscription parseVoskJson(String json) {
        if (json == null || json.isBlank()) {
            return new VoskTranscription("", 1.0);
        }
        json = truncateJsonIfNeeded(json);
        try {
            JSONObject obj = new JSONObject(json);

            // Vosk can return two formats:
            // 1. Final result: {"text": "...", "result": [...]}
            // 2. Alternatives format: {"alternatives": [{"text": "...", "confidence": ...}]}
            String text;
            double confidence;

            if (obj.has("alternatives")) {
                // Extract from alternatives[0]
                // Note: Vosk returns unnormalized confidence scores in alternatives format
                org.json.JSONArray alternatives = obj.getJSONArray("alternatives");
                if (!alternatives.isEmpty()) {
                    JSONObject firstAlt = alternatives.getJSONObject(0);
                    text = firstAlt.optString("text", "").trim();
                    double rawConfidence = firstAlt.optDouble("confidence", 1.0);
                    // Normalize to [0.0, 1.0] range - Vosk alternatives may return values > 1.0
                    confidence = Math.min(1.0, Math.max(0.0, rawConfidence));
                } else {
                    text = "";
                    confidence = 1.0;
                }
            } else {
                // Extract from root level (original format)
                text = obj.optString("text", "").trim();
                confidence = extractConfidenceFromJson(obj);
            }

            return new VoskTranscription(text, confidence);
        } catch (Exception e) {
            LOG.warn("Failed to parse Vosk JSON response: {}", json, e);
            return new VoskTranscription("", 1.0);
        }
    }

    /**
     * Truncates JSON string to MAX_JSON_SIZE if needed to prevent OOM attacks.
     *
     * <p>Protects against malicious/custom Vosk builds returning unbounded output.
     *
     * @param json JSON string to validate
     * @return original string if within limit, truncated string otherwise
     */
    private static String truncateJsonIfNeeded(String json) {
        if (json.length() > MAX_JSON_SIZE) {
            LOG.warn("Vosk JSON response exceeds {}B cap (actual: {}B); truncating to prevent OOM",
                    MAX_JSON_SIZE, json.length());
            return json.substring(0, MAX_JSON_SIZE);
        }
        return json;
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
        if (results.isEmpty()) {
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
     * <p>Package-private for testing purposes.
     *
     * @param text transcribed text (may be empty)
     * @param confidence confidence score (0.0-1.0)
     */
    record VoskTranscription(String text, double confidence) {
    }
}
