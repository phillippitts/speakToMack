package com.phillippitts.speaktomack.service.fallback;

import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.service.orchestration.event.TranscriptionCompletedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackManagerTest {

    static class FakeTypingService implements TypingService {
        String lastText;
        boolean returnValue = true;

        @Override
        public boolean paste(String text) {
            this.lastText = text;
            return returnValue;
        }
    }

    @Test
    void delegatesToTypingServiceOnTranscription() {
        FakeTypingService typingService = new FakeTypingService();
        FallbackManager manager = new FallbackManager(typingService);

        TranscriptionResult result = new TranscriptionResult("hello world", 0.95, Instant.now(), "vosk");
        TranscriptionCompletedEvent event = new TranscriptionCompletedEvent(result, Instant.now(), "vosk");

        manager.onTranscription(event);

        assertThat(typingService.lastText).isEqualTo("hello world");
    }

    @Test
    void handlesEmptyText() {
        FakeTypingService typingService = new FakeTypingService();
        FallbackManager manager = new FallbackManager(typingService);

        TranscriptionResult result = new TranscriptionResult("", 0.5, Instant.now(), "whisper");
        TranscriptionCompletedEvent event = new TranscriptionCompletedEvent(result, Instant.now(), "whisper");

        manager.onTranscription(event);

        assertThat(typingService.lastText).isEqualTo("");
    }

    @Test
    void handlesMultilineText() {
        FakeTypingService typingService = new FakeTypingService();
        FallbackManager manager = new FallbackManager(typingService);

        TranscriptionResult result = new TranscriptionResult("line1\nline2\r\nline3", 0.85, Instant.now(), "vosk");
        TranscriptionCompletedEvent event = new TranscriptionCompletedEvent(result, Instant.now(), "vosk");

        manager.onTranscription(event);

        assertThat(typingService.lastText).isEqualTo("line1\nline2\r\nline3");
    }

    @Test
    void handlesLongText() {
        FakeTypingService typingService = new FakeTypingService();
        FallbackManager manager = new FallbackManager(typingService);

        String longText = "a".repeat(5000);
        TranscriptionResult result = new TranscriptionResult(longText, 0.9, Instant.now(), "whisper");
        TranscriptionCompletedEvent event = new TranscriptionCompletedEvent(result, Instant.now(), "whisper");

        manager.onTranscription(event);

        assertThat(typingService.lastText).hasSize(5000);
        assertThat(typingService.lastText).isEqualTo(longText);
    }

    @Test
    void continuesWhenTypingServiceFails() {
        FakeTypingService typingService = new FakeTypingService();
        typingService.returnValue = false; // Simulate all fallbacks failing
        FallbackManager manager = new FallbackManager(typingService);

        TranscriptionResult result = new TranscriptionResult("test", 0.8, Instant.now(), "vosk");
        TranscriptionCompletedEvent event = new TranscriptionCompletedEvent(result, Instant.now(), "vosk");

        // Should not throw, just log warning
        manager.onTranscription(event);

        assertThat(typingService.lastText).isEqualTo("test");
    }

    @Test
    void handlesMultipleEvents() {
        FakeTypingService typingService = new FakeTypingService();
        FallbackManager manager = new FallbackManager(typingService);

        TranscriptionResult result1 = new TranscriptionResult("first", 0.9, Instant.now(), "vosk");
        TranscriptionCompletedEvent event1 = new TranscriptionCompletedEvent(result1, Instant.now(), "vosk");
        manager.onTranscription(event1);
        assertThat(typingService.lastText).isEqualTo("first");

        TranscriptionResult result2 = new TranscriptionResult("second", 0.85, Instant.now(), "whisper");
        TranscriptionCompletedEvent event2 = new TranscriptionCompletedEvent(result2, Instant.now(), "whisper");
        manager.onTranscription(event2);
        assertThat(typingService.lastText).isEqualTo("second");
    }
}
