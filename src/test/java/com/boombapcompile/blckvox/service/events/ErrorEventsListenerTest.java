package com.boombapcompile.blckvox.service.events;

import com.boombapcompile.blckvox.service.audio.capture.CaptureErrorEvent;
import com.boombapcompile.blckvox.service.hotkey.event.HotkeyConflictEvent;
import com.boombapcompile.blckvox.service.hotkey.event.HotkeyPermissionDeniedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ErrorEventsListenerTest {

    @Test
    void shouldLogReturnsTrueOnFirstCall() {
        ErrorEventsListener listener = new ErrorEventsListener();
        assertThat(listener.shouldLog("test-key")).isTrue();
    }

    @Test
    void shouldLogReturnsFalseWithinThrottleWindow() {
        ErrorEventsListener listener = new ErrorEventsListener();
        listener.shouldLog("test-key"); // First call
        assertThat(listener.shouldLog("test-key")).isFalse(); // Within 1 min
    }

    @Test
    void shouldLogReturnsTrueForDifferentKeys() {
        ErrorEventsListener listener = new ErrorEventsListener();
        listener.shouldLog("key-1");
        assertThat(listener.shouldLog("key-2")).isTrue();
    }

    @Test
    void onHotkeyPermissionDeniedDoesNotThrow() {
        ErrorEventsListener listener = new ErrorEventsListener();
        assertThatCode(() -> listener.onHotkeyPermissionDenied(
                new HotkeyPermissionDeniedEvent(Instant.now())))
                .doesNotThrowAnyException();
    }

    @Test
    void onHotkeyConflictDoesNotThrow() {
        ErrorEventsListener listener = new ErrorEventsListener();
        assertThatCode(() -> listener.onHotkeyConflict(
                new HotkeyConflictEvent("TAB", List.of("META"), Instant.now())))
                .doesNotThrowAnyException();
    }

    @Test
    void onCaptureErrorDoesNotThrow() {
        ErrorEventsListener listener = new ErrorEventsListener();
        assertThatCode(() -> listener.onCaptureError(
                new CaptureErrorEvent("test-reason", Instant.now())))
                .doesNotThrowAnyException();
    }
}
