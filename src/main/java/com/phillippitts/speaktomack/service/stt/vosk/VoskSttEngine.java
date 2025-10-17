package com.phillippitts.speaktomack.service.stt.vosk;

import com.phillippitts.speaktomack.config.stt.VoskConfig;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Vosk-based implementation of the SttEngine interface.
 *
 * <p>Task 2.2 scope: lifecycle only (initialize/close). Audio processing is added in Task 2.3.
 *
 * <p>Thread-safe: All state-modifying operations are synchronized.
 *
 * <p>Audio contract: This engine expects raw PCM audio in the format defined by
 * com.phillippitts.speaktomack.service.audio.AudioFormat (16kHz, 16-bit, mono, little-endian).
 * Callers must validate/convert inputs (e.g., strip WAV headers) before invoking {@link #transcribe(byte[])}.
 */
@Component
public class VoskSttEngine implements SttEngine {

    private static final Logger LOG = LogManager.getLogger(VoskSttEngine.class);

    private final VoskConfig config;
    private final Object lock = new Object();

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
                throw new TranscriptionException(
                    "Failed to initialize Vosk (model=" + config.modelPath()
                        + ", sampleRate=" + config.sampleRate() + ")", "vosk", t);
            }
        }
    }

    /**
     * Transcribes audio data to text.
     *
     * <p>Implementation pending in Task 2.3. Currently enforces initialization precondition.
     *
     * @param audioData PCM audio data (16kHz, 16-bit, mono)
     * @return transcription result
     * @throws TranscriptionException if engine not initialized or transcription fails
     */
    @Override
    public TranscriptionResult transcribe(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            throw new IllegalArgumentException("audioData must not be null or empty");
        }
        org.vosk.Model localModel;
        synchronized (lock) {
            if (!initialized || closed || model == null) {
                throw new TranscriptionException("Vosk engine not initialized", "vosk");
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
            String text = extractTextFromVoskJson(json);
            double confidence = extractConfidenceFromVoskJson(json);
            // Empty text is valid (e.g., silence or unclear audio)
            return TranscriptionResult.of(text, confidence, getEngineName());
        } catch (Throwable t) {
            throw new TranscriptionException("Vosk transcription failed", "vosk", t);
        }
    }

    /**
     * Returns the engine name identifier.
     *
     * @return "vosk"
     */
    @Override
    public String getEngineName() {
        return "vosk";
    }

    /**
     * Checks if the engine is healthy and ready to transcribe.
     *
     * <p>Returns true if initialized, not closed, and JNI resources are available.
     *
     * @return true if healthy, false otherwise
     */
    @Override
    public boolean isHealthy() {
        synchronized (lock) {
            if (!initialized || closed || model == null) {
                return false;
            }
            // Optional: Quick JNI health check
            try {
                // Create and immediately close a recognizer to verify JNI works
                try (org.vosk.Recognizer testRec = new org.vosk.Recognizer(model, config.sampleRate())) {
                    return true;
                }
            } catch (Throwable t) {
                LOG.warn("Vosk health check failed", t);
                return false;
            }
        }
    }

    /**
     * Closes the Vosk engine and releases JNI resources.
     *
     * <p>This operation is idempotent - multiple calls are safe.
     *
     * <p>Thread-safe: synchronized to prevent concurrent closure.
     */
    @Override
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
     * Extracts the transcribed text from Vosk's JSON response.
     *
     * <p>Example Vosk JSON: {@code {"text": "hello world"}}
     *
     * @param json JSON string from Vosk recognizer
     * @return extracted text, or empty string if parsing fails
     */
    static String extractTextFromVoskJson(String json) {
        if (json == null || json.isBlank()) {
            return "";
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
     * <p>Vosk provides per-word confidence in the "result" array.
     * Example: {@code {"result": [{"conf": 0.95, "word": "hello"}, {"conf": 0.87, "word": "world"}]}}
     *
     * <p>If no confidence data is available, returns 1.0 (assume perfect confidence).
     *
     * @param json JSON string from Vosk recognizer
     * @return average confidence (0.0-1.0), or 1.0 if parsing fails
     */
    static double extractConfidenceFromVoskJson(String json) {
        if (json == null || json.isBlank()) {
            return 1.0;
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

            return count > 0 ? sum / count : 1.0;
        } catch (Exception e) {
            LOG.warn("Failed to parse confidence from Vosk JSON: {}", json, e);
            return 1.0; // Default to perfect confidence on parse error
        }
    }
}
