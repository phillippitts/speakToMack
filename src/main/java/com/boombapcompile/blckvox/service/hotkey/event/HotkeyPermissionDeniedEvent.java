package com.boombapcompile.blckvox.service.hotkey.event;

import java.time.Instant;

/**
 * Published when registering the global key hook fails due to OS permissions
 * (e.g., macOS Accessibility permission not granted).
 */
public record HotkeyPermissionDeniedEvent(Instant at) { }
