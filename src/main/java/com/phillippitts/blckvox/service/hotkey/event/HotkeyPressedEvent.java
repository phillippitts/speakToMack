package com.phillippitts.blckvox.service.hotkey.event;

import java.time.Instant;

/**
 * Published when the configured hotkey is pressed.
 */
public record HotkeyPressedEvent(Instant at) { }
