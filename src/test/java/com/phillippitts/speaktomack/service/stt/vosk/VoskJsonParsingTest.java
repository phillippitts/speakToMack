package com.phillippitts.speaktomack.service.stt.vosk;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class VoskJsonParsingTest {

    @Test
    void shouldTrimTextAndClampInvalidConfidence() {
        String messy = "{\"text\": \"  hello  \", \"result\": [{\"conf\": 1.2}]}";
        VoskSttEngine.VoskTranscription result = VoskSttEngine.parseVoskJson(messy);
        assertThat(result.text()).isEqualTo("hello");
        // Confidence > 1.0 should be clamped to 1.0 to satisfy TranscriptionResult contract
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void shouldClampNegativeConfidence() {
        String negativeConf = "{\"result\": [{\"conf\": -0.5}]}";
        VoskSttEngine.VoskTranscription result = VoskSttEngine.parseVoskJson(negativeConf);
        // Negative confidence should be clamped to 0.0
        assertThat(result.confidence()).isEqualTo(0.0);
    }

    @Test
    void shouldExtractTextFromValidVoskJson() {
        String json = "{\"text\": \"hello world\"}";
        VoskSttEngine.VoskTranscription result = VoskSttEngine.parseVoskJson(json);
        assertThat(result.text()).isEqualTo("hello world");
    }

    @Test
    void shouldReturnEmptyStringWhenJsonHasNoTextField() {
        String json = "{}";
        VoskSttEngine.VoskTranscription result = VoskSttEngine.parseVoskJson(json);
        assertThat(result.text()).isEmpty();
    }

    @Test
    void shouldReturnEmptyStringWhenJsonIsMalformed() {
        String json = "not-a-json";
        VoskSttEngine.VoskTranscription result = VoskSttEngine.parseVoskJson(json);
        assertThat(result.text()).isEmpty();
    }

    @Test
    void shouldExtractAverageConfidenceFromVoskJson() {
        String json = "{\"result\": ["
                + "{\"conf\": 0.95, \"word\": \"hello\"},"
                + "{\"conf\": 0.85, \"word\": \"world\"}"
                + "]}";
        VoskSttEngine.VoskTranscription result = VoskSttEngine.parseVoskJson(json);
        assertThat(result.confidence()).isCloseTo(0.90, within(0.01)); // Average of 0.95 and 0.85
    }

    @Test
    void shouldReturnPerfectConfidenceWhenNoResultArray() {
        String json = "{\"text\": \"hello\"}";
        VoskSttEngine.VoskTranscription result = VoskSttEngine.parseVoskJson(json);
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void shouldReturnPerfectConfidenceWhenResultArrayEmpty() {
        String json = "{\"result\": []}";
        VoskSttEngine.VoskTranscription result = VoskSttEngine.parseVoskJson(json);
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void shouldReturnPerfectConfidenceWhenJsonIsMalformed() {
        String json = "not-a-json";
        VoskSttEngine.VoskTranscription result = VoskSttEngine.parseVoskJson(json);
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void shouldHandleMixedResultsWithAndWithoutConfidence() {
        String json = "{\"result\": ["
                + "{\"conf\": 0.80, \"word\": \"hello\"},"
                + "{\"word\": \"world\"}" // no conf field
                + "]}";
        VoskSttEngine.VoskTranscription result = VoskSttEngine.parseVoskJson(json);
        assertThat(result.confidence()).isEqualTo(0.80); // Only count entries with conf
    }
}
