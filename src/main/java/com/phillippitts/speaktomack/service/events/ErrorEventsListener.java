package com.phillippitts.speaktomack.service.events;

import com.phillippitts.speaktomack.service.audio.capture.CaptureErrorEvent;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyConflictEvent;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyPermissionDeniedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized handler for user-facing error events. Privacy-safe and throttled to avoid log spam.
 */
@Component
class ErrorEventsListener {
    private static final Logger LOG = LogManager.getLogger(ErrorEventsListener.class);

    private final Map<String, Instant> lastLog = new ConcurrentHashMap<>();
    private static final Duration THROTTLE = Duration.ofMinutes(1);

    @EventListener
    void onHotkeyPermissionDenied(HotkeyPermissionDeniedEvent e) {
        if (shouldLog("hotkey-permission")) {
            LOG.warn("Hotkey permission denied. On macOS grant Accessibility: "
                    + "System Settings → Privacy & Security → Accessibility (then restart app)");
        }
    }

    @EventListener
    void onHotkeyConflict(HotkeyConflictEvent e) {
        String key = "hotkey-conflict-" + e.key() + '-' + e.modifiers();
        if (shouldLog(key)) {
            LOG.warn("Configured hotkey conflicts with OS-reserved shortcut: key={}, modifiers={}. "
                    + "Update hotkey.* properties.", e.key(), e.modifiers());
        }
    }

    @EventListener
    void onCaptureError(CaptureErrorEvent e) {
        String key = "capture-" + e.reason();
        if (shouldLog(key)) {
            LOG.warn("Capture error: reason={}. Check microphone device & permissions.", e.reason());
        }
    }

    // Package-private for tests
    boolean shouldLog(String key) {
        Instant now = Instant.now();
        Instant prev = lastLog.get(key);
        if (prev == null || Duration.between(prev, now).compareTo(THROTTLE) > 0) {
            lastLog.put(key, now);
            return true;
        }
        return false;
    }
}
