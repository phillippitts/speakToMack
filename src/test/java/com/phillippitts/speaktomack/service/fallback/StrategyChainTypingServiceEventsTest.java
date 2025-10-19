package com.phillippitts.speaktomack.service.fallback;

import com.phillippitts.speaktomack.config.typing.TypingProperties;
import com.phillippitts.speaktomack.service.fallback.event.AllTypingFallbacksFailedEvent;
import com.phillippitts.speaktomack.service.fallback.event.TypingFallbackEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyChainTypingServiceEventsTest {

    @Test
    void publishesFallbackEventsOnAdapterFailures() {
        TypingProperties props = new TypingProperties(800, 0, 0, true, false,
                TypingProperties.NewlineMode.LF, true, true, "os-default");
        List<Object> events = new ArrayList<>();
        ApplicationEventPublisher pub = events::add;

        // Adapters: both available but both fail
        TypingAdapter failingRobot = new TypingAdapter() {
            @Override public boolean canType() { return true; }
            @Override public boolean type(String text) { return false; }
            @Override public String name() { return "robot"; }
        };
        TypingAdapter failingClipboard = new TypingAdapter() {
            @Override public boolean canType() { return true; }
            @Override public boolean type(String text) { return false; }
            @Override public String name() { return "clipboard"; }
        };
        StrategyChainTypingService svc = new StrategyChainTypingService(List.of(failingRobot, failingClipboard), props, pub);

        boolean ok = svc.paste("hello");
        assertThat(ok).isFalse();
        // Expect at least one TypingFallbackEvent and an AllTypingFallbacksFailedEvent
        assertThat(events.stream().anyMatch(e -> e instanceof TypingFallbackEvent)).isTrue();
        assertThat(events.stream().anyMatch(e -> e instanceof AllTypingFallbacksFailedEvent)).isTrue();
    }
}
