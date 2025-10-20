package com.phillippitts.speaktomack.service.stt.whisper;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WhisperJsonParserTest {

    @Test
    void extractTextPrefersTopLevelText() {
        String json = "{\n  \"text\": \"hello world\",\n  \"segments\": [{\"text\": \"ignored\"}]\n}";
        String text = WhisperJsonParser.extractText(json);
        assertThat(text).isEqualTo("hello world");
    }

    @Test
    void extractTextConcatenatesSegmentsWhenNoTopLevel() {
        String json = "{\n  \"segments\": [\n    {\"text\": \"hello\"},\n    {\"text\": \"world\"}\n  ]\n}";
        String text = WhisperJsonParser.extractText(json);
        assertThat(text).isEqualTo("hello world");
    }

    @Test
    void extractTokensPrefersWordsWhenAvailable() {
        String json = "{\n  \"segments\": [\n    {\"words\": [\n      {\"word\": \"Hello\"},"
                + "\n      {\"word\": \"WORLD!\"}\n    ]}\n  ]\n}";
        List<String> tokens = WhisperJsonParser.extractTokens(json);
        assertThat(tokens).containsExactly("hello", "world");
    }

    @Test
    void extractTokensFallsBackToTextTokenization() {
        String json = "{\n  \"text\": \"Alpha, beta. GAMMA!\"\n}";
        List<String> tokens = WhisperJsonParser.extractTokens(json);
        assertThat(tokens).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void extractTextHandlesMalformedGracefully() {
        String text = WhisperJsonParser.extractText("{ not-json");
        assertThat(text).isEmpty();
    }

    @Test
    void extractTokensHandlesMalformedGracefully() {
        List<String> tokens = WhisperJsonParser.extractTokens("{ not-json");
        assertThat(tokens).isEmpty();
    }

    @Test
    void extractTextEmptyWhenNoContent() {
        assertThat(WhisperJsonParser.extractText(null)).isEmpty();
        assertThat(WhisperJsonParser.extractText("")).isEmpty();
    }

    @Test
    void extractTextHandlesWhitespaceOnly() {
        String json = "{\n  \"text\": \"   \"\n}";
        String text = WhisperJsonParser.extractText(json);
        assertThat(text).isEmpty();
    }

    @Test
    void extractTextMultipleSegmentsWithMixedContent() {
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
    void extractTokensMultipleSegmentsWithWords() {
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
    void extractTokensSegmentsWithoutWordsFallsBackToSegmentText() {
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
    void extractTokensHandlesSpecialCharacters() {
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
    void extractTokensEmptyWhenNoContent() {
        assertThat(WhisperJsonParser.extractTokens(null)).isEmpty();
        assertThat(WhisperJsonParser.extractTokens("")).isEmpty();
        assertThat(WhisperJsonParser.extractTokens("{}")).isEmpty();
    }

    @Test
    void extractTokensFiltersBlankWords() {
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
    void extractTextEmptySegmentsArray() {
        String json = "{\"segments\": []}";
        String text = WhisperJsonParser.extractText(json);
        assertThat(text).isEmpty();
    }

    @Test
    void extractTokensEmptySegmentsArray() {
        String json = "{\"segments\": []}";
        List<String> tokens = WhisperJsonParser.extractTokens(json);
        assertThat(tokens).isEmpty();
    }
}
