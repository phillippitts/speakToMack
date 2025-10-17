package com.phillippitts.speaktomack.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranscriptionResultTest {

    @Test
    void shouldCreateValidTranscriptionResult() {
        Instant now = Instant.now();
        TranscriptionResult result = new TranscriptionResult(
                "hello world",
                0.95,
                now,
                "vosk"
        );

        assertThat(result.text()).isEqualTo("hello world");
        assertThat(result.confidence()).isEqualTo(0.95);
        assertThat(result.timestamp()).isEqualTo(now);
        assertThat(result.engineName()).isEqualTo("vosk");
    }

    @Test
    void shouldRejectNullText() {
        assertThatThrownBy(() -> new TranscriptionResult(null, 0.95, Instant.now(), "vosk"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("text must not be null");
    }

    @Test
    void shouldAcceptEmptyText() {
        // Empty text is valid (silence may produce empty transcription)
        TranscriptionResult result = new TranscriptionResult("", 0.95, Instant.now(), "vosk");
        assertThat(result.text()).isEmpty();
    }

    @Test
    void shouldAcceptWhitespaceText() {
        // Whitespace is valid (some STT engines may return spaces)
        TranscriptionResult result = new TranscriptionResult("   ", 0.95, Instant.now(), "vosk");
        assertThat(result.text()).isEqualTo("   ");
    }

    @Test
    void shouldRejectNegativeConfidence() {
        assertThatThrownBy(() -> new TranscriptionResult("hello", -0.1, Instant.now(), "vosk"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0.0 and 1.0");
    }

    @Test
    void shouldRejectConfidenceAboveOne() {
        assertThatThrownBy(() -> new TranscriptionResult("hello", 1.5, Instant.now(), "vosk"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0.0 and 1.0");
    }

    @Test
    void shouldRejectNullTimestamp() {
        assertThatThrownBy(() -> new TranscriptionResult("hello", 0.95, null, "vosk"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Timestamp must not be null");
    }

    @Test
    void shouldRejectNullEngineName() {
        assertThatThrownBy(() -> new TranscriptionResult("hello", 0.95, Instant.now(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Engine name must not be null");
    }

    @Test
    void shouldCreateWithFactoryMethod() {
        TranscriptionResult result = TranscriptionResult.of("test text", 0.88, "whisper");

        assertThat(result.text()).isEqualTo("test text");
        assertThat(result.confidence()).isEqualTo(0.88);
        assertThat(result.engineName()).isEqualTo("whisper");
        assertThat(result.timestamp()).isNotNull();
        assertThat(result.timestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void shouldAcceptMinimumConfidence() {
        TranscriptionResult result = TranscriptionResult.of("hello", 0.0, "vosk");
        assertThat(result.confidence()).isEqualTo(0.0);
    }

    @Test
    void shouldAcceptMaximumConfidence() {
        TranscriptionResult result = TranscriptionResult.of("hello", 1.0, "vosk");
        assertThat(result.confidence()).isEqualTo(1.0);
    }
}
