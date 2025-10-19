package com.phillippitts.speaktomack.service.stt.whisper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility to parse whisper.cpp JSON stdout into text/tokens for reconciliation.
 * Safe against malformed input: falls back to empty tokens and empty text.
 */
final class WhisperJsonParser {

    private WhisperJsonParser() {}

    static String extractText(String json) {
        if (json == null || json.isBlank()) return "";
        try {
            JSONObject obj = new JSONObject(json);
            // Prefer top-level text if present
            if (obj.has("text")) {
                String t = obj.optString("text", "");
                if (t == null || t.isBlank()) {
                    return "";
                }
                return t.trim();
            }
            // Otherwise, concatenate segment texts
            if (obj.has("segments")) {
                StringBuilder sb = new StringBuilder();
                JSONArray segs = obj.optJSONArray("segments");
                if (segs != null) {
                    for (int i = 0; i < segs.length(); i++) {
                        JSONObject seg = segs.optJSONObject(i);
                        if (seg != null) {
                            String t = seg.optString("text", "");
                            if (!t.isBlank()) {
                                if (sb.length() > 0) sb.append(' ');
                                sb.append(t.trim());
                            }
                        }
                    }
                }
                return sb.toString();
            }
        } catch (Exception ignored) {
            // fall through to blank
        }
        return "";
    }

    static List<String> extractTokens(String json) {
        List<String> tokens = new ArrayList<>();
        if (json == null || json.isBlank()) return List.of();
        try {
            JSONObject obj = new JSONObject(json);
            // If words available, prefer them
            if (obj.has("segments")) {
                JSONArray segs = obj.optJSONArray("segments");
                if (segs != null) {
                    for (int i = 0; i < segs.length(); i++) {
                        JSONObject seg = segs.optJSONObject(i);
                        if (seg == null) continue;
                        JSONArray words = seg.optJSONArray("words");
                        if (words != null) {
                            for (int w = 0; w < words.length(); w++) {
                                JSONObject word = words.optJSONObject(w);
                                if (word == null) continue;
                                String t = word.optString("word", "").toLowerCase();
                                if (!t.isBlank()) {
                                    // Normalize to alpha tokens only for overlap consistency
                                    String[] parts = t.split("[^\\p{Alpha}]+");
                                    for (String p : parts) if (!p.isBlank()) tokens.add(p);
                                }
                            }
                        }
                    }
                }
            }
            if (!tokens.isEmpty()) return List.copyOf(tokens);
            // Fallback to tokenizing text if no words available
            String text = extractText(json);
            if (!text.isBlank()) {
                String[] parts = text.toLowerCase().split("[^\\p{Alpha}]+");
                for (String p : parts) if (!p.isBlank()) tokens.add(p);
            }
            return List.copyOf(tokens);
        } catch (Exception e) {
            return List.of();
        }
    }
}
