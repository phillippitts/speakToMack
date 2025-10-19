package com.phillippitts.speaktomack.service.events;

import com.phillippitts.speaktomack.service.audio.capture.CaptureErrorEvent;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyConflictEvent;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyPermissionDeniedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorEventsListenerTest {

    @Test
    void throttlesRepeatLogs() {
        ErrorEventsListener l = new ErrorEventsListener();
        // shouldLog allows first occurrence
        assertThat(l.shouldLog("hotkey-permission")).isTrue();
        // but rejects immediately repeated
        assertThat(l.shouldLog("hotkey-permission")).isFalse();
    }

    @Test
    void handlersDoNotThrow() {
        ErrorEventsListener l = new ErrorEventsListener();
        // Just ensure no exceptions
        l.onHotkeyPermissionDenied(new HotkeyPermissionDeniedEvent(Instant.now()));
        l.onHotkeyConflict(new HotkeyConflictEvent("TAB", List.of("META"), Instant.now()));
        l.onCaptureError(new CaptureErrorEvent("MIC_PERMISSION_DENIED", Instant.now()));
        assertThat(true).isTrue();
    }
}
