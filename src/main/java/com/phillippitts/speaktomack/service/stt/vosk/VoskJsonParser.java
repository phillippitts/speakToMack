package com.phillippitts.speaktomack.service.stt.vosk;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

/**
 * Utility to parse Vosk JSON output into text and confidence scores.
 *
 * <p>This parser handles two Vosk JSON response formats:
 * <ul>
 *   <li><b>Final result format:</b> {@code {"text": "...", "result": [...]}}</li>
 *   <li><b>Alternatives format:</b> {@code {"alternatives": [{"text": "...", "confidence": ...}]}}</li>
 * </ul>
 *
 * <p>Thread-safe: All methods are static and stateless.
 *
 * <p><b>Security:</b> Protects against OOM attacks by capping JSON response size
 * at {@link #MAX_JSON_SIZE} (1MB).
 *
 * @since 1.0
 */
final class VoskJsonParser {

    private static final Logger LOG = LogManager.getLogger(VoskJsonParser.class);

    /**
     * Maximum allowed JSON response size from Vosk recognizer (1MB).
     * Protects against malicious/custom Vosk builds returning unbounded output.
     */
    private static final int MAX_JSON_SIZE = 1_048_576; // 1MB

    private VoskJsonParser() {
        // Utility class - prevent instantiation
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
    static VoskTranscription parse(String json) {
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
                confidence = extractConfidence(obj);
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
    private static double extractConfidence(JSONObject obj) {
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
     * @param text transcribed text (may be empty)
     * @param confidence confidence score (0.0-1.0)
     */
    record VoskTranscription(String text, double confidence) {
    }
}
