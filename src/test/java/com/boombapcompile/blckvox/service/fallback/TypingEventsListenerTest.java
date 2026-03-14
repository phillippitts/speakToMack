package com.boombapcompile.blckvox.service.fallback;

import com.boombapcompile.blckvox.service.fallback.event.AllTypingFallbacksFailedEvent;
import com.boombapcompile.blckvox.service.fallback.event.TypingFallbackEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;

class TypingEventsListenerTest {

    @Test
    void onFallbackDoesNotThrow() {
        TypingEventsListener listener = new TypingEventsListener();
        assertThatCode(() -> listener.onFallback(
                new TypingFallbackEvent("robot", "headless", Instant.now())))
                .doesNotThrowAnyException();
    }

    @Test
    void onAllFailedDoesNotThrow() {
        TypingEventsListener listener = new TypingEventsListener();
        assertThatCode(() -> listener.onAllFailed(
                new AllTypingFallbacksFailedEvent("no adapters", Instant.now())))
                .doesNotThrowAnyException();
    }
}
