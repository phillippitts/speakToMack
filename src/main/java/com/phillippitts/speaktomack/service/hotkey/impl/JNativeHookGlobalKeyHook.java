package com.phillippitts.speaktomack.service.hotkey.impl;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.NativeInputEvent;
import com.phillippitts.speaktomack.service.hotkey.GlobalKeyHook;
import com.phillippitts.speaktomack.service.hotkey.NormalizedKeyEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Production GlobalKeyHook backed by JNativeHook.
 * Converts NativeKeyEvent into NormalizedKeyEvent for the Hotkey subsystem.
 */
@Component
public class JNativeHookGlobalKeyHook implements GlobalKeyHook, NativeKeyListener {

    private static final Logger LOG = LogManager.getLogger(JNativeHookGlobalKeyHook.class);

    private volatile Consumer<NormalizedKeyEvent> listener;
    private final AtomicBoolean registered = new AtomicBoolean(false);

    @Override
    public void register() {
        if (registered.get()) return;
        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
            registered.set(true);
            LOG.info("Registered JNativeHook global key listener");
        } catch (NativeHookException | UnsatisfiedLinkError e) {
            throw new SecurityException("Failed to register global key hook: " + e.getMessage(), e);
        }
    }

    @Override
    public void unregister() {
        if (!registered.get()) return;
        try {
            GlobalScreen.removeNativeKeyListener(this);
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
        emit(nativeEvent, NormalizedKeyEvent.Type.PRESSED);
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent nativeEvent) {
        emit(nativeEvent, NormalizedKeyEvent.Type.RELEASED);
    }

    @Override public void nativeKeyTyped(NativeKeyEvent nativeEvent) { /* ignore */ }

    private void emit(NativeKeyEvent ne, NormalizedKeyEvent.Type type) {
        Consumer<NormalizedKeyEvent> l = this.listener;
        if (l == null) return;
        String keyText = NativeKeyEvent.getKeyText(ne.getKeyCode());
        String key = canonicalizeKey(keyText);
        Set<String> mods = extractModifiers(ne);
        NormalizedKeyEvent e = new NormalizedKeyEvent(type, key, mods, System.currentTimeMillis());
        try {
            l.accept(e);
        } catch (Exception ex) {
            LOG.warn("Listener error for {}: {}", e, ex.toString());
        }
    }

    private static Set<String> extractModifiers(NativeKeyEvent e) {
        int m = e.getModifiers();
        Set<String> mods = new HashSet<>();
        if ((m & NativeInputEvent.SHIFT_MASK) != 0) mods.add("SHIFT");
        if ((m & NativeInputEvent.CTRL_MASK) != 0) mods.add("CONTROL");
        if ((m & NativeInputEvent.ALT_MASK) != 0) mods.add("ALT");
        if ((m & NativeInputEvent.META_MASK) != 0) mods.add("META");
        return mods;
    }

    private static String canonicalizeKey(String keyText) {
        if (keyText == null) return "UNKNOWN";
        String k = keyText.trim().toUpperCase()
                .replace(' ', '_')
                .replace("PLUS", "+")
                .replace("COMMAND", "META");
        // Normalize RIGHT/LEFT META names
        if (k.contains("RIGHT_META")) return "RIGHT_META";
        if (k.contains("LEFT_META")) return "LEFT_META";
        return k;
    }
}
