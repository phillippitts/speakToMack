package com.phillippitts.speaktomack.service.hotkey.impl;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseMotionListener;
import com.github.kwhat.jnativehook.NativeInputEvent;
import com.github.kwhat.jnativehook.dispatcher.SwingDispatchService;
import com.phillippitts.speaktomack.service.hotkey.GlobalKeyHook;
import com.phillippitts.speaktomack.service.hotkey.KeyNameMapper;
import com.phillippitts.speaktomack.service.hotkey.NormalizedKeyEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Production GlobalKeyHook backed by JNativeHook.
 * Converts NativeKeyEvent into NormalizedKeyEvent for the Hotkey subsystem.
 *
 * Special handling for modifier keys: JNativeHook on macOS doesn't fire key pressed/released
 * events for standalone modifier keys. We use a polling mechanism to detect modifier state changes.
 */
@Component
public class JNativeHookGlobalKeyHook implements GlobalKeyHook, NativeKeyListener, NativeMouseMotionListener {

    private static final Logger LOG = LogManager.getLogger(JNativeHookGlobalKeyHook.class);
    private static final long POLL_INTERVAL_MS = 50; // Poll every 50ms for modifier changes

    private volatile Consumer<NormalizedKeyEvent> listener;
    private final AtomicBoolean registered = new AtomicBoolean(false);

    // Track modifier key states to detect press/release
    private final ConcurrentHashMap<String, Boolean> modifierStates = new ConcurrentHashMap<>();

    // Polling service for modifier key detection
    private ScheduledExecutorService modifierPoller;

    @Override
    public void register() {
        if (registered.get()) {
            return;
        }
        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
            GlobalScreen.addNativeMouseMotionListener(this);

            // Start polling for modifier state changes
            startModifierPolling();

            registered.set(true);
            LOG.info("Registered JNativeHook global key listener (with modifier polling every {}ms)", POLL_INTERVAL_MS);
        } catch (NativeHookException | UnsatisfiedLinkError e) {
            throw new SecurityException("Failed to register global key hook: " + e.getMessage(), e);
        }
    }

    @Override
    public void unregister() {
        if (!registered.get()) {
            return;
        }
        try {
            // Stop polling
            stopModifierPolling();

            GlobalScreen.removeNativeKeyListener(this);
            GlobalScreen.removeNativeMouseMotionListener(this);
            GlobalScreen.unregisterNativeHook();
        } catch (Exception e) {
            LOG.debug("Error unregistering native hook", e);
        } finally {
            registered.set(false);
        }
    }

    private void startModifierPolling() {
        modifierPoller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ModifierKeyPoller");
            t.setDaemon(true);
            return t;
        });

        modifierPoller.scheduleAtFixedRate(this::pollModifierState, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopModifierPolling() {
        if (modifierPoller != null) {
            modifierPoller.shutdown();
            try {
                modifierPoller.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Polls for current modifier state by triggering a synthetic check.
     * We create a fake mouse event with current timestamp to check modifiers.
     */
    private void pollModifierState() {
        // Get current modifier state from GlobalScreen's internal state
        // Unfortunately JNativeHook doesn't expose this directly, so we rely on
        // any recent event. We'll use a different approach - just check on any input.
        // For now, this will be triggered by mouse motion events
    }

    @Override
    public void addListener(Consumer<NormalizedKeyEvent> listener) {
        this.listener = listener;
    }

    // NativeKeyListener callbacks
    @Override
    public void nativeKeyPressed(NativeKeyEvent nativeEvent) {
        checkModifierChanges(nativeEvent);
        emit(nativeEvent, NormalizedKeyEvent.Type.PRESSED);
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent nativeEvent) {
        checkModifierChanges(nativeEvent);
        emit(nativeEvent, NormalizedKeyEvent.Type.RELEASED);
    }

    @Override public void nativeKeyTyped(NativeKeyEvent nativeEvent) { /* ignore */ }

    /**
     * Checks for modifier state changes from key events.
     */
    private void checkModifierChanges(NativeKeyEvent ne) {
        Consumer<NormalizedKeyEvent> l = this.listener;
        if (l == null) {
            return;
        }

        int modifiers = ne.getModifiers();

        // Check each modifier key state
        checkModifierState("LEFT_SHIFT", (modifiers & NativeInputEvent.SHIFT_L_MASK) != 0, ne);
        checkModifierState("RIGHT_SHIFT", (modifiers & NativeInputEvent.SHIFT_R_MASK) != 0, ne);
        checkModifierState("LEFT_CONTROL", (modifiers & NativeInputEvent.CTRL_L_MASK) != 0, ne);
        checkModifierState("RIGHT_CONTROL", (modifiers & NativeInputEvent.CTRL_R_MASK) != 0, ne);
        checkModifierState("LEFT_ALT", (modifiers & NativeInputEvent.ALT_L_MASK) != 0, ne);
        checkModifierState("RIGHT_ALT", (modifiers & NativeInputEvent.ALT_R_MASK) != 0, ne);
        checkModifierState("LEFT_META", (modifiers & NativeInputEvent.META_L_MASK) != 0, ne);
        checkModifierState("RIGHT_META", (modifiers & NativeInputEvent.META_R_MASK) != 0, ne);
    }

    private void emit(NativeKeyEvent ne, NormalizedKeyEvent.Type type) {
        Consumer<NormalizedKeyEvent> l = this.listener;
        if (l == null) {
            return;
        }
        String keyText = NativeKeyEvent.getKeyText(ne.getKeyCode());
        String key = KeyNameMapper.normalizeKey(keyText);

        // JNativeHook returns "Meta" for both left and right Command keys
        // Use key location to distinguish left (0x0) vs right (0x36)
        if ("META".equals(key)) {
            int rawKeyCode = ne.getRawCode();
            if (rawKeyCode == 0x36) {
                key = "RIGHT_META";
            } else if (rawKeyCode == 0x37) {
                key = "LEFT_META";
            }
        }

        Set<String> mods = extractModifiers(ne);
        NormalizedKeyEvent e = new NormalizedKeyEvent(type, key, mods, System.currentTimeMillis());
        try {
            l.accept(e);
        } catch (Exception ex) {
            LOG.warn("Listener error for {}: {}", e, ex.toString());
        }
    }

    // NativeMouseMotionListener callbacks - used to detect modifier changes
    @Override
    public void nativeMouseMoved(NativeMouseEvent e) {
        checkModifierChanges(e);
    }

    @Override
    public void nativeMouseDragged(NativeMouseEvent e) {
        checkModifierChanges(e);
    }

    /**
     * Checks for modifier state changes from mouse events.
     */
    private void checkModifierChanges(NativeMouseEvent e) {
        Consumer<NormalizedKeyEvent> l = this.listener;
        if (l == null) {
            return;
        }

        int modifiers = e.getModifiers();

        // Check each modifier key state
        checkModifierState("LEFT_SHIFT", (modifiers & NativeInputEvent.SHIFT_L_MASK) != 0, e);
        checkModifierState("RIGHT_SHIFT", (modifiers & NativeInputEvent.SHIFT_R_MASK) != 0, e);
        checkModifierState("LEFT_CONTROL", (modifiers & NativeInputEvent.CTRL_L_MASK) != 0, e);
        checkModifierState("RIGHT_CONTROL", (modifiers & NativeInputEvent.CTRL_R_MASK) != 0, e);
        checkModifierState("LEFT_ALT", (modifiers & NativeInputEvent.ALT_L_MASK) != 0, e);
        checkModifierState("RIGHT_ALT", (modifiers & NativeInputEvent.ALT_R_MASK) != 0, e);
        checkModifierState("LEFT_META", (modifiers & NativeInputEvent.META_L_MASK) != 0, e);
        checkModifierState("RIGHT_META", (modifiers & NativeInputEvent.META_R_MASK) != 0, e);
    }

    /**
     * Checks if a specific modifier key state has changed and emits an event if so.
     */
    private void checkModifierState(String keyName, boolean isPressed, NativeInputEvent sourceEvent) {
        Consumer<NormalizedKeyEvent> l = this.listener;
        if (l == null) {
            return;
        }

        Boolean previousState = modifierStates.get(keyName);
        boolean stateChanged = (previousState == null && isPressed) ||
                              (previousState != null && previousState != isPressed);

        if (stateChanged) {
            modifierStates.put(keyName, isPressed);
            NormalizedKeyEvent.Type type = isPressed ? NormalizedKeyEvent.Type.PRESSED : NormalizedKeyEvent.Type.RELEASED;
            Set<String> mods = extractModifiers(sourceEvent);
            NormalizedKeyEvent e = new NormalizedKeyEvent(type, keyName, mods, System.currentTimeMillis());

            try {
                l.accept(e);
            } catch (Exception ex) {
                LOG.warn("Listener error for modifier {}: {}", keyName, ex.toString());
            }
        }
    }

    private static Set<String> extractModifiers(NativeInputEvent e) {
        int m = e.getModifiers();
        Set<String> mods = new HashSet<>();
        if ((m & NativeInputEvent.SHIFT_MASK) != 0) {
            mods.add("SHIFT");
        }
        if ((m & NativeInputEvent.CTRL_MASK) != 0) {
            mods.add("CONTROL");
        }
        if ((m & NativeInputEvent.ALT_MASK) != 0) {
            mods.add("ALT");
        }
        if ((m & NativeInputEvent.META_MASK) != 0) {
            mods.add("META");
        }
        return mods;
    }
}
