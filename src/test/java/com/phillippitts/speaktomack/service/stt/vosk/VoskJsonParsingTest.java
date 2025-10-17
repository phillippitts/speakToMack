package com.phillippitts.speaktomack.service.stt.vosk;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class VoskJsonParsingTest {

    @Test
    void shouldExtractTextFromValidVoskJson() {
        String json = "{\"text\": \"hello world\"}";
        String text = VoskSttEngine.extractTextFromVoskJson(json);
        assertThat(text).isEqualTo("hello world");
    }

    @Test
    void shouldReturnEmptyStringWhenJsonHasNoTextField() {
        String json = "{}";
        String text = VoskSttEngine.extractTextFromVoskJson(json);
        assertThat(text).isEmpty();
    }

    @Test
    void shouldReturnEmptyStringWhenJsonIsMalformed() {
        String json = "not-a-json";
        String text = VoskSttEngine.extractTextFromVoskJson(json);
        assertThat(text).isEmpty();
    }

    @Test
    void shouldExtractAverageConfidenceFromVoskJson() {
        String json = "{\"result\": ["
                + "{\"conf\": 0.95, \"word\": \"hello\"},"
                + "{\"conf\": 0.85, \"word\": \"world\"}"
                + "]}";
        double confidence = VoskSttEngine.extractConfidenceFromVoskJson(json);
        assertThat(confidence).isCloseTo(0.90, within(0.01)); // Average of 0.95 and 0.85
    }

    @Test
    void shouldReturnPerfectConfidenceWhenNoResultArray() {
        String json = "{\"text\": \"hello\"}";
        double confidence = VoskSttEngine.extractConfidenceFromVoskJson(json);
        assertThat(confidence).isEqualTo(1.0);
    }

    @Test
    void shouldReturnPerfectConfidenceWhenResultArrayEmpty() {
        String json = "{\"result\": []}";
        double confidence = VoskSttEngine.extractConfidenceFromVoskJson(json);
        assertThat(confidence).isEqualTo(1.0);
    }

    @Test
    void shouldReturnPerfectConfidenceWhenJsonIsMalformed() {
        String json = "not-a-json";
        double confidence = VoskSttEngine.extractConfidenceFromVoskJson(json);
        assertThat(confidence).isEqualTo(1.0);
    }

    @Test
    void shouldHandleMixedResultsWithAndWithoutConfidence() {
        String json = "{\"result\": ["
                + "{\"conf\": 0.80, \"word\": \"hello\"},"
                + "{\"word\": \"world\"}" // no conf field
                + "]}";
        double confidence = VoskSttEngine.extractConfidenceFromVoskJson(json);
        assertThat(confidence).isEqualTo(0.80); // Only count entries with conf
    }
}
