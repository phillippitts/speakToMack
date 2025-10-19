package com.phillippitts.speaktomack.service.stt.whisper;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WhisperJsonParserTest {

    @Test
    void extractText_prefersTopLevelText() {
        String json = "{\n  \"text\": \"hello world\",\n  \"segments\": [{\"text\": \"ignored\"}]\n}";
        String text = WhisperJsonParser.extractText(json);
        assertThat(text).isEqualTo("hello world");
    }

    @Test
    void extractText_concatenatesSegmentsWhenNoTopLevel() {
        String json = "{\n  \"segments\": [\n    {\"text\": \"hello\"},\n    {\"text\": \"world\"}\n  ]\n}";
        String text = WhisperJsonParser.extractText(json);
        assertThat(text).isEqualTo("hello world");
    }

    @Test
    void extractTokens_prefersWordsWhenAvailable() {
        String json = "{\n  \"segments\": [\n    {\"words\": [\n      {\"word\": \"Hello\"},\n      {\"word\": \"WORLD!\"}\n    ]}\n  ]\n}";
        List<String> tokens = WhisperJsonParser.extractTokens(json);
        assertThat(tokens).containsExactly("hello", "world");
    }

    @Test
    void extractTokens_fallsBackToTextTokenization() {
        String json = "{\n  \"text\": \"Alpha, beta. GAMMA!\"\n}";
        List<String> tokens = WhisperJsonParser.extractTokens(json);
        assertThat(tokens).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void extractText_handlesMalformedGracefully() {
        String text = WhisperJsonParser.extractText("{ not-json");
        assertThat(text).isEmpty();
    }

    @Test
    void extractTokens_handlesMalformedGracefully() {
        List<String> tokens = WhisperJsonParser.extractTokens("{ not-json");
        assertThat(tokens).isEmpty();
    }

    @Test
    void extractText_emptyWhenNoContent() {
        assertThat(WhisperJsonParser.extractText(null)).isEmpty();
        assertThat(WhisperJsonParser.extractText("")).isEmpty();
    }

    @Test
    void extractText_handlesWhitespaceOnly() {
        String json = "{\n  \"text\": \"   \"\n}";
        String text = WhisperJsonParser.extractText(json);
        assertThat(text).isEmpty();
    }

    @Test
    void extractText_multipleSegmentsWithMixedContent() {
        String json = """
            {
              "segments": [
                {"text": "First segment"},
                {"text": ""},
                {"text": "   "},
                {"text": "Last segment"}
              ]
            }
            """;
        String text = WhisperJsonParser.extractText(json);
        assertThat(text).isEqualTo("First segment Last segment");
    }

    @Test
    void extractTokens_multipleSegmentsWithWords() {
        String json = """
            {
              "segments": [
                {"words": [{"word": "Hello"}, {"word": "there"}]},
                {"words": [{"word": "General"}, {"word": "Kenobi!"}]}
              ]
            }
            """;
        List<String> tokens = WhisperJsonParser.extractTokens(json);
        assertThat(tokens).containsExactly("hello", "there", "general", "kenobi");
    }

    @Test
    void extractTokens_segmentsWithoutWords_fallsBackToSegmentText() {
        String json = """
            {
              "segments": [
                {"text": "Hello world"},
                {"text": "How are you"}
              ]
            }
            """;
        List<String> tokens = WhisperJsonParser.extractTokens(json);
        assertThat(tokens).containsExactly("hello", "world", "how", "are", "you");
    }

    @Test
    void extractTokens_handlesSpecialCharacters() {
        String json = """
            {
              "segments": [
                {"words": [
                  {"word": "Hello,"},
                  {"word": "world!"},
                  {"word": "123"},
                  {"word": "test@example.com"}
                ]}
              ]
            }
            """;
        List<String> tokens = WhisperJsonParser.extractTokens(json);
        assertThat(tokens).containsExactly("hello", "world", "test", "example", "com");
    }

    @Test
    void extractTokens_emptyWhenNoContent() {
        assertThat(WhisperJsonParser.extractTokens(null)).isEmpty();
        assertThat(WhisperJsonParser.extractTokens("")).isEmpty();
        assertThat(WhisperJsonParser.extractTokens("{}")).isEmpty();
    }

    @Test
    void extractTokens_filtersBlankWords() {
        String json = """
            {
              "segments": [
                {"words": [
                  {"word": "valid"},
                  {"word": "   "},
                  {"word": ""},
                  {"word": "also-valid"}
                ]}
              ]
            }
            """;
        List<String> tokens = WhisperJsonParser.extractTokens(json);
        assertThat(tokens).containsExactly("valid", "also", "valid");
    }

    @Test
    void extractText_emptySegmentsArray() {
        String json = "{\"segments\": []}";
        String text = WhisperJsonParser.extractText(json);
        assertThat(text).isEmpty();
    }

    @Test
    void extractTokens_emptySegmentsArray() {
        String json = "{\"segments\": []}";
        List<String> tokens = WhisperJsonParser.extractTokens(json);
        assertThat(tokens).isEmpty();
    }
}
