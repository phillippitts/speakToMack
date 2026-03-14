package com.boombapcompile.blckvox.service.stt.vosk;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class VoskJsonParsingTest {

    @Test
    void shouldTrimTextAndClampInvalidConfidence() {
        String messy = "{\"text\": \"  hello  \", \"result\": [{\"conf\": 1.2}]}";
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(messy);
        assertThat(result.text()).isEqualTo("hello");
        // Confidence > 1.0 should be clamped to 1.0 to satisfy TranscriptionResult contract
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void shouldClampNegativeConfidence() {
        String negativeConf = "{\"result\": [{\"conf\": -0.5}]}";
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(negativeConf);
        // Negative confidence should be clamped to 0.0
        assertThat(result.confidence()).isEqualTo(0.0);
    }

    @Test
    void shouldExtractTextFromValidVoskJson() {
        String json = "{\"text\": \"hello world\"}";
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.text()).isEqualTo("hello world");
    }

    @Test
    void shouldReturnEmptyStringWhenJsonHasNoTextField() {
        String json = "{}";
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.text()).isEmpty();
    }

    @Test
    void shouldReturnEmptyStringWhenJsonIsMalformed() {
        String json = "not-a-json";
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.text()).isEmpty();
    }

    @Test
    void shouldExtractAverageConfidenceFromVoskJson() {
        String json = "{\"result\": ["
                + "{\"conf\": 0.95, \"word\": \"hello\"},"
                + "{\"conf\": 0.85, \"word\": \"world\"}"
                + "]}";
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.confidence()).isCloseTo(0.90, within(0.01)); // Average of 0.95 and 0.85
    }

    @Test
    void shouldReturnPerfectConfidenceWhenNoResultArray() {
        String json = "{\"text\": \"hello\"}";
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void shouldReturnPerfectConfidenceWhenResultArrayEmpty() {
        String json = "{\"result\": []}";
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void shouldReturnPerfectConfidenceWhenJsonIsMalformed() {
        String json = "not-a-json";
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void shouldHandleMixedResultsWithAndWithoutConfidence() {
        String json = "{\"result\": ["
                + "{\"conf\": 0.80, \"word\": \"hello\"},"
                + "{\"word\": \"world\"}" // no conf field
                + "]}";
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.confidence()).isEqualTo(0.80); // Only count entries with conf
    }

    // --- Alternatives format tests ---

    @Test
    void shouldExtractFromAlternativesFormat() {
        String json = "{\"alternatives\": [{\"text\": \"hello there\", \"confidence\": 0.85}]}";
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.text()).isEqualTo("hello there");
        assertThat(result.confidence()).isCloseTo(0.85, within(0.001));
    }

    @Test
    void shouldReturnEmptyForEmptyAlternatives() {
        String json = "{\"alternatives\": []}";
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.text()).isEmpty();
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void shouldClampAlternativesConfidenceAboveOne() {
        String json = "{\"alternatives\": [{\"text\": \"hi\", \"confidence\": 5.0}]}";
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.text()).isEqualTo("hi");
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void shouldClampAlternativesNegativeConfidence() {
        String json = "{\"alternatives\": [{\"text\": \"hi\", \"confidence\": -2.0}]}";
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(json);
        assertThat(result.confidence()).isEqualTo(0.0);
    }

    // --- Null/blank input tests ---

    @Test
    void shouldReturnEmptyForNullInput() {
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(null);
        assertThat(result.text()).isEmpty();
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    void shouldReturnEmptyForBlankInput() {
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse("   ");
        assertThat(result.text()).isEmpty();
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    // --- JSON truncation test ---

    @Test
    void shouldHandleOversizedJsonGracefully() {
        // Create JSON > 1MB that will be truncated, producing malformed JSON
        String oversized = "{\"text\": \"" + "x".repeat(1_048_577) + "\"}";
        VoskJsonParser.VoskTranscription result = VoskJsonParser.parse(oversized);
        // Truncated JSON will fail to parse, falling back to empty
        assertThat(result.text()).isNotNull();
    }
}
