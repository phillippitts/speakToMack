package com.phillippitts.blckvox.service.hotkey.event;

import java.time.Instant;

/**
 * Published when the configured hotkey is released.
 */
public record HotkeyReleasedEvent(Instant at) { }
