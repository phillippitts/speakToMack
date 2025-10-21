package com.phillippitts.speaktomack.service.hotkey.impl;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseMotionListener;
import com.github.kwhat.jnativehook.NativeInputEvent;
import com.phillippitts.speaktomack.service.hotkey.GlobalKeyHook;
import com.phillippitts.speaktomack.service.hotkey.KeyNameMapper;
import com.phillippitts.speaktomack.service.hotkey.NormalizedKeyEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Production GlobalKeyHook backed by JNativeHook.
 * Converts NativeKeyEvent into NormalizedKeyEvent for the Hotkey subsystem.
 *
 * Special handling for modifier keys: JNativeHook on macOS doesn't fire key pressed/released
 * events for standalone modifier keys. We rely on keyboard and mouse motion events to detect
 * modifier state changes and avoid an unnecessary polling thread.
 */
@Component
public class JNativeHookGlobalKeyHook implements GlobalKeyHook, NativeKeyListener, NativeMouseMotionListener {

    private static final Logger LOG = LogManager.getLogger(JNativeHookGlobalKeyHook.class);

    /**
     * macOS raw key code for right Command key.
     */
    private static final int MACOS_RIGHT_CMD_KEYCODE = 0x36;

    /**
     * macOS raw key code for left Command key.
     */
    private static final int MACOS_LEFT_CMD_KEYCODE = 0x37;

    private volatile Consumer<NormalizedKeyEvent> listener;
    private final AtomicBoolean registered = new AtomicBoolean(false);

    // Track modifier key states to detect press/release
    private final ConcurrentHashMap<String, Boolean> modifierStates = new ConcurrentHashMap<>();

    @Override
    public void register() {
        if (registered.get()) {
            return;
        }
        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
            GlobalScreen.addNativeMouseMotionListener(this);

            registered.set(true);
            LOG.info("Registered JNativeHook global key listener (modifier changes via key/mouse events)");
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
            GlobalScreen.removeNativeKeyListener(this);
            GlobalScreen.removeNativeMouseMotionListener(this);
            GlobalScreen.unregisterNativeHook();
        } catch (Exception e) {
            LOG.debug("Error unregistering native hook", e);
        } finally {
            registered.set(false);
        }
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
        checkAllModifierStates(ne);
    }

    private void emit(NativeKeyEvent ne, NormalizedKeyEvent.Type type) {
        Consumer<NormalizedKeyEvent> listener = this.listener;
        if (listener == null) {
            return;
        }
        String keyText = NativeKeyEvent.getKeyText(ne.getKeyCode());
        String key = KeyNameMapper.normalizeKey(keyText);

        // JNativeHook returns "Meta" for both left and right Command keys
        // Use key location to distinguish left vs right on macOS
        if ("META".equals(key)) {
            int rawKeyCode = ne.getRawCode();
            if (rawKeyCode == MACOS_RIGHT_CMD_KEYCODE) {
                key = "RIGHT_META";
            } else if (rawKeyCode == MACOS_LEFT_CMD_KEYCODE) {
                key = "LEFT_META";
            }
        }

        Set<String> mods = extractModifiers(ne);
        NormalizedKeyEvent e = new NormalizedKeyEvent(type, key, mods, System.currentTimeMillis());
        try {
            listener.accept(e);
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
        checkAllModifierStates(e);
    }

    /**
     * Checks all modifier key states and emits events for any changes.
     * Extracted from duplicate code in checkModifierChanges methods.
     *
     * @param event the native input event (keyboard or mouse) containing modifier state
     */
    private void checkAllModifierStates(NativeInputEvent event) {
        Consumer<NormalizedKeyEvent> listener = this.listener;
        if (listener == null) {
            return;
        }

        int modifiers = event.getModifiers();

        // Check each modifier key state
        checkModifierState("LEFT_SHIFT", (modifiers & NativeInputEvent.SHIFT_L_MASK) != 0, event);
        checkModifierState("RIGHT_SHIFT", (modifiers & NativeInputEvent.SHIFT_R_MASK) != 0, event);
        checkModifierState("LEFT_CONTROL", (modifiers & NativeInputEvent.CTRL_L_MASK) != 0, event);
        checkModifierState("RIGHT_CONTROL", (modifiers & NativeInputEvent.CTRL_R_MASK) != 0, event);
        checkModifierState("LEFT_ALT", (modifiers & NativeInputEvent.ALT_L_MASK) != 0, event);
        checkModifierState("RIGHT_ALT", (modifiers & NativeInputEvent.ALT_R_MASK) != 0, event);
        checkModifierState("LEFT_META", (modifiers & NativeInputEvent.META_L_MASK) != 0, event);
        checkModifierState("RIGHT_META", (modifiers & NativeInputEvent.META_R_MASK) != 0, event);
    }

    /**
     * Checks if a specific modifier key state has changed and emits an event if so.
     */
    private void checkModifierState(String keyName, boolean isPressed, NativeInputEvent sourceEvent) {
        Consumer<NormalizedKeyEvent> listener = this.listener;
        if (listener == null) {
            return;
        }

        Boolean previousState = modifierStates.get(keyName);
        boolean stateChanged = (previousState == null && isPressed) ||
                              (previousState != null && previousState != isPressed);

        if (stateChanged) {
            modifierStates.put(keyName, isPressed);
            NormalizedKeyEvent.Type type;
            if (isPressed) {
                type = NormalizedKeyEvent.Type.PRESSED;
            } else {
                type = NormalizedKeyEvent.Type.RELEASED;
            }
            Set<String> mods = extractModifiers(sourceEvent);
            NormalizedKeyEvent e = new NormalizedKeyEvent(type, keyName, mods, System.currentTimeMillis());

            try {
                listener.accept(e);
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
