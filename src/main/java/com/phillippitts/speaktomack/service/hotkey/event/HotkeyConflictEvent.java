package com.phillippitts.speaktomack.service.hotkey.event;

import java.time.Instant;
import java.util.List;

/**
 * Published when the configured hotkey conflicts with an OS-reserved shortcut
 * (e.g., Cmd+Tab on macOS, Win+L on Windows).
 */
public record HotkeyConflictEvent(String key, List<String> modifiers, Instant at) { }
